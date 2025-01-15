package by.zoomos_v2.service;

import by.zoomos_v2.mapping.ClientMappingConfig;
import by.zoomos_v2.model.FileMetadata;
import by.zoomos_v2.repository.FileMetadataRepository;
import by.zoomos_v2.service.processor.FileProcessor;
import by.zoomos_v2.service.processor.FileProcessorFactory;
import by.zoomos_v2.util.PathResolver;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис для обработки загруженных файлов.
 * Обеспечивает асинхронную обработку файлов в соответствии с настройками маппинга.
 * Поддерживает:
 * - Асинхронную обработку файлов
 * - Отслеживание прогресса обработки
 * - Управление статусами обработки
 * - Отмену обработки файлов
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileProcessingService {

    private final FileMetadataRepository fileMetadataRepository;
    private final ObjectMapper objectMapper;
    private final FileProcessorFactory processorFactory;
    private final PathResolver pathResolver;
    private final DataPersistenceService dataPersistenceService;
    private final MappingConfigService mappingConfigService; // добавляем для получения маппинга
    private static final int BATCH_SIZE = 1000; // размер порции для обработки

    // Хранилище статусов обработки файлов
    private final ConcurrentHashMap<Long, FileProcessingStatus> processingStatuses = new ConcurrentHashMap<>();

    /**
     * Асинхронно обрабатывает загруженный файл.
     * Процесс обработки включает:
     * 1. Загрузку метаданных файла
     * 2. Обновление статуса обработки
     * 3. Выбор подходящего процессора
     * 4. Обработку файла с отслеживанием прогресса
     * 5. Сохранение результатов обработки
     *
     * @param fileId идентификатор файла для обработки
     * @throws IllegalArgumentException если файл не найден
     */
    @Async("fileProcessingExecutor")
    public void processFileAsync(Long fileId) {
        log.debug("Начало асинхронной обработки файла с ID: {}", fileId);

        try {
            FileMetadata metadata = fileMetadataRepository.findById(fileId)
                    .orElseThrow(() -> new IllegalArgumentException("Файл не найден"));

            updateProcessingStatus(fileId, 0, "Инициализация обработки");
            updateFileStatus(metadata, "PROCESSING", null);

            // Получаем активный маппинг для клиента
            ClientMappingConfig mapping = mappingConfigService.getMappingById(metadata.getMappingConfigId());

            Path filePath = pathResolver.getFilePath(metadata.getShopId(),
                    metadata.getStoredFilename());

            FileProcessor processor = processorFactory.getProcessor(metadata);

            // Обрабатываем файл
            Map<String, Object> results = processor.processFile(filePath, metadata,
                    (progress, message) -> updateProcessingStatus(fileId, progress, message));

            // Получаем данные и маппинг
            @SuppressWarnings("unchecked")
            List<Map<String, String>> data = (List<Map<String, String>>) results.get("records");
            Map<String, String> columnsMapping = objectMapper.readValue(
                    mapping.getColumnsConfig(),
                    new TypeReference<>() {
                    }
            );

            log.info("Начало пакетной обработки. Всего записей: {}", data.size());

            List<Map<String, String>> currentBatch = new ArrayList<>();
            Map<String, Object> totalResults = new HashMap<>();
            totalResults.put("totalCount", data.size());
            int totalSuccessCount = 0;
            int totalErrorCount = 0;

            for (int i = 0; i < data.size(); i++) {
                currentBatch.add(data.get(i));

                if (currentBatch.size() >= BATCH_SIZE || i == data.size() - 1) {
                    // Обрабатываем текущий пакет
                    updateProcessingStatus(fileId, (i * 100) / data.size(),
                            String.format("Обработка записей %d-%d из %d",
                                    i - currentBatch.size() + 1, i + 1, data.size()));

                    Map<String, Object> batchResults = dataPersistenceService.saveEntities(
                            currentBatch, metadata.getShopId(), columnsMapping);

                    totalSuccessCount += (int) batchResults.get("successCount");
                    totalErrorCount += (int) batchResults.get("errorCount");

                    currentBatch.clear();
                }
            }

            // Формируем итоговые результаты
            totalResults.put("successCount", totalSuccessCount);
            totalResults.put("errorCount", totalErrorCount);
            results.putAll(totalResults);

            // Обновляем статус в зависимости от результатов
            if (totalErrorCount > 0) {
                String message = String.format("Обработано с ошибками (%d из %d записей)",
                        totalErrorCount, data.size());
                updateFileStatus(metadata, "COMPLETED_WITH_ERRORS", message);
            } else {
                updateFileStatus(metadata, "COMPLETED", null);
            }

            metadata.setProcessingResults(objectMapper.writeValueAsString(results));
            metadata.updateProcessingStatistics(data.size(), totalSuccessCount, totalErrorCount);
            updateProcessingStatus(fileId, 100, "Обработка завершена");

            log.info("Файл {} успешно обработан. Успешно: {}, Ошибок: {}",
                    metadata.getOriginalFilename(), totalSuccessCount, totalErrorCount);

        } catch (Exception e) {
            log.error("Ошибка при обработке файла {}: {}", fileId, e.getMessage(), e);
            updateProcessingStatus(fileId, -1, "Ошибка: " + e.getMessage());

            try {
                FileMetadata metadata = fileMetadataRepository.findById(fileId).orElse(null);
                if (metadata != null) {
                    updateFileStatus(metadata, "ERROR", e.getMessage());
                }
            } catch (Exception ex) {
                log.error("Ошибка при обновлении статуса файла: {}", ex.getMessage(), ex);
            }
        }
    }


    /**
     * Обновляет статус обработки файла в базе данных.
     * Также обновляет временные метки начала и завершения обработки.
     *
     * @param metadata метаданные файла для обновления
     * @param status новый статус файла (PROCESSING, COMPLETED, ERROR, CANCELLED)
     * @param errorMessage сообщение об ошибке (если есть)
     */
    @Transactional
    public void updateFileStatus(FileMetadata metadata, String status, String errorMessage) {
        metadata.setStatus(status);
        metadata.setErrorMessage(errorMessage);

        if ("PROCESSING".equals(status)) {
            metadata.setProcessingStartedAt(LocalDateTime.now());
        } else if ("COMPLETED".equals(status) || "ERROR".equals(status) || "CANCELLED".equals(status)) {
            metadata.setProcessingCompletedAt(LocalDateTime.now());
        }

        fileMetadataRepository.save(metadata);
    }

    /**
     * Возвращает текущий статус обработки файла.
     * Если статус не найден, возвращает статус по умолчанию.
     *
     * @param fileId идентификатор файла
     * @return объект статуса обработки, содержащий прогресс и сообщение
     */
    public FileProcessingStatus getProcessingStatus(Long fileId) {
        return processingStatuses.getOrDefault(fileId,
                new FileProcessingStatus(0, "Статус неизвестен"));
    }

    /**
     * Отменяет обработку файла.
     * Обновляет статус файла на CANCELLED и добавляет сообщение об отмене.
     *
     * @param fileId идентификатор файла для отмены обработки
     */
    @Transactional
    public void cancelProcessing(Long fileId) {
        log.debug("Отмена обработки файла: {}", fileId);
        updateProcessingStatus(fileId, -1, "Обработка отменена");

        FileMetadata metadata = fileMetadataRepository.findById(fileId).orElse(null);
        if (metadata != null) {
            updateFileStatus(metadata, "CANCELLED", "Обработка отменена пользователем");
        }
    }

    /**
     * Обновляет статус обработки файла в памяти.
     * Используется для отслеживания прогресса обработки.
     *
     * @param fileId идентификатор файла
     * @param progress процент выполнения (от 0 до 100, или -1 для ошибки)
     * @param message текущее сообщение о статусе обработки
     */
    private void updateProcessingStatus(Long fileId, int progress, String message) {
        processingStatuses.put(fileId, new FileProcessingStatus(progress, message));
    }

    /**
     * Внутренний класс для хранения информации о статусе обработки файла.
     * Содержит информацию о прогрессе и текущем состоянии обработки.
     */
    @Data
    @AllArgsConstructor
    public static class FileProcessingStatus {
        /**
         * Прогресс обработки в процентах (от 0 до 100, или -1 для ошибки)
         */
        private int progress;

        /**
         * Текущее сообщение о статусе обработки
         */
        private String message;
    }
}