package by.zoomos_v2.service;

import by.zoomos_v2.model.FileMetadata;
import by.zoomos_v2.repository.FileMetadataRepository;
import by.zoomos_v2.service.processor.FileProcessor;
import by.zoomos_v2.service.processor.FileProcessorFactory;
import by.zoomos_v2.util.FileUtils;
import by.zoomos_v2.util.PathResolver;
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

            Path filePath = pathResolver.getFilePath(metadata.getShopId(),
                    metadata.getStoredFilename());

            FileProcessor processor = processorFactory.getProcessor(metadata);

            Map<String, Object> results = processor.processFile(filePath, metadata,
                    (progress, message) -> updateProcessingStatus(fileId, progress, message));

            metadata.setProcessingResults(objectMapper.writeValueAsString(results));
            updateFileStatus(metadata, "COMPLETED", null);
            updateProcessingStatus(fileId, 100, "Обработка завершена");

            log.info("Файл {} успешно обработан", metadata.getOriginalFilename());

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