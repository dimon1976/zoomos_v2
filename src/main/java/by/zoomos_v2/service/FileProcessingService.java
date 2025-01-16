package by.zoomos_v2.service;

import by.zoomos_v2.exception.ValidationException;
import by.zoomos_v2.mapping.ClientMappingConfig;
import by.zoomos_v2.model.FileMetadata;
import by.zoomos_v2.repository.FileMetadataRepository;
import by.zoomos_v2.service.processor.FileProcessor;
import by.zoomos_v2.service.processor.FileProcessorFactory;
import by.zoomos_v2.service.processor.ProcessingStats;
import by.zoomos_v2.service.processor.client.ClientDataProcessor;
import by.zoomos_v2.service.processor.client.ClientDataProcessorFactory;
import by.zoomos_v2.service.processor.client.ProcessingResult;
import by.zoomos_v2.util.PathResolver;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис для асинхронной обработки файлов.
 * Обеспечивает загрузку, обработку и сохранение данных из файлов.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileProcessingService {

    private final FileMetadataService fileMetadataService;
    private final FileMetadataRepository fileMetadataRepository;
    private final DataPersistenceService dataPersistenceService;
    private final FileProcessorFactory processorFactory;
    private final ClientDataProcessorFactory clientProcessorFactory;
    private final MappingConfigService mappingConfigService;
    private final ObjectMapper objectMapper;
    private final PathResolver pathResolver;

    private static final int BATCH_SIZE = 1000;
    private final ConcurrentHashMap<Long, ProcessingStatus> processingStatuses = new ConcurrentHashMap<>();

    /**
     * Асинхронно обрабатывает загруженный файл
     */
    @Async("fileProcessingExecutor")
    public void processFileAsync(Long fileId) {
        log.debug("Начало асинхронной обработки файла с ID: {}", fileId);
        LocalDateTime startTime = LocalDateTime.now();

        try {
            // Инициализация
            FileMetadata metadata = fileMetadataService.initializeProcessing(fileId);
            updateProcessingStatus(fileId, 0, "Инициализация обработки");

            // Основной процесс обработки
            ProcessingStats stats = processFile(metadata, startTime);

            // Обновление результатов
            fileMetadataService.updateProcessingResults(metadata, stats);
            updateProcessingStatus(fileId, 100, "Обработка завершена");

        } catch (Exception e) {
            handleProcessingError(fileId, e);
        }
    }

    /**
     * Выполняет основной процесс обработки файла
     */
    private ProcessingStats processFile(FileMetadata metadata, LocalDateTime startTime) throws Exception {
        // Базовая обработка файла
        ProcessingStats fileStats = processRawFile(metadata);
        log.debug("Базовая обработка файла завершена. Получено записей: {}", fileStats.getTotalCount());

        // Клиентская обработка
        ProcessingStats clientStats = processForClient(metadata, fileStats.getProcessedData());
        log.debug("Клиентская обработка завершена. Обработано записей: {}", clientStats.getSuccessCount());

        // Сохранение данных
        ProcessingStats persistenceStats = persistData(metadata, clientStats.getProcessedData());
        log.debug("Сохранение данных завершено. Сохранено записей: {}", persistenceStats.getSuccessCount());

        // Объединяем статистику и добавляем время обработки
        ProcessingStats finalStats = ProcessingStats.merge(fileStats, clientStats, persistenceStats);
        finalStats.setProcessingTimeSeconds(ChronoUnit.SECONDS.between(startTime, LocalDateTime.now()));

        addAdditionalStats(finalStats);
        return finalStats;
    }

    /**
     * Выполняет базовую обработку файла
     */
    private ProcessingStats processRawFile(FileMetadata metadata) {
        Path filePath = pathResolver.getFilePath(metadata.getClientId(), metadata.getStoredFilename());
        FileProcessor processor = processorFactory.getProcessor(metadata);

        Map<String, Object> results = processor.processFile(filePath, metadata,
                (progress, message) -> updateProcessingStatus(metadata.getId(), progress, message));

        return convertToProcessingStats(results);
    }

    /**
     * Обрабатывает данные согласно правилам клиента
     */
    private ProcessingStats processForClient(FileMetadata metadata, List<Map<String, String>> data) {
        ClientDataProcessor processor = clientProcessorFactory.getProcessor(metadata.getClientId());
        ProcessingStats stats = new ProcessingStats();

        try {
            processor.validateData(data);
            ProcessingResult result = processor.processData(data, metadata.getClientId());

            stats.setTotalCount(result.getProcessedData().size());
            stats.setSuccessCount(result.getProcessedData().size());
            stats.setErrorCount(result.getErrors().size());
            stats.setProcessedData(result.getProcessedData());

            if (result.getErrors() != null) {
                result.getErrors().forEach(error ->
                        stats.addError(error, "Ошибка клиентской обработки"));
            }

            if (result.getStatistics() != null) {
                stats.setAdditionalStats(result.getStatistics());
            }

            return stats;
        } catch (ValidationException e) {
            stats.addError(e.getMessage(), "Ошибка валидации");
            return stats;
        } catch (Exception e) {
            stats.addError(e.getMessage(), "Ошибка обработки");
            return stats;
        }
    }

    /**
     * Сохраняет обработанные данные в базу данных
     */
    private ProcessingStats persistData(FileMetadata metadata, List<Map<String, String>> data) throws Exception {
        ProcessingStats stats = new ProcessingStats();
        stats.setTotalCount(data.size());

        ClientMappingConfig mapping = mappingConfigService.getMappingById(metadata.getMappingConfigId());
        Map<String, String> columnsMapping = objectMapper.readValue(
                mapping.getColumnsConfig(),
                new TypeReference<>() {}
        );

        List<Map<String, String>> currentBatch = new ArrayList<>();
        int successCount = 0;
        int errorCount = 0;

        for (int i = 0; i < data.size(); i++) {
            currentBatch.add(data.get(i));

            if (shouldProcessBatch(currentBatch, i, data.size())) {
                Map<String, Object> batchResults = processBatch(metadata, currentBatch,
                        columnsMapping, i, data.size());

                successCount += (int) batchResults.get("successCount");
                errorCount += (int) batchResults.get("errorCount");

                if (batchResults.containsKey("errors")) {
                    List<String> errors = (List<String>) batchResults.get("errors");
                    errors.forEach(error -> stats.addError(error, "Ошибка сохранения"));
                }

                currentBatch.clear();
            }
        }

        stats.setSuccessCount(successCount);
        stats.setErrorCount(errorCount);
        return stats;
    }

    /**
     * Добавляет дополнительную статистику к результатам обработки
     */
    private void addAdditionalStats(ProcessingStats stats) {
        stats.addAdditionalStat("Скорость обработки (записей/сек)", stats.getProcessingSpeed());
        stats.addAdditionalStat("Размер пакета", BATCH_SIZE);
        stats.addAdditionalStat("Процент успешных записей",
                String.format("%.2f%%", (double) stats.getSuccessCount() / stats.getTotalCount() * 100));
    }

    /**
     * Возвращает текущий статус обработки файла.
     * Если статус не найден, возвращает статус по умолчанию.
     *
     * @param fileId идентификатор файла
     * @return объект статуса обработки, содержащий прогресс и сообщение
     */
    public ProcessingStatus getProcessingStatus(Long fileId) {
        return processingStatuses.getOrDefault(fileId,
                new ProcessingStatus(0, "Статус неизвестен"));
    }

    /**
     * Обрабатывает ошибки процесса обработки
     */
    private void handleProcessingError(Long fileId, Exception e) {
        log.error("Ошибка при обработке файла {}: {}", fileId, e.getMessage(), e);
        fileMetadataService.markAsError(fileId, e.getMessage());
        updateProcessingStatus(fileId, -1, "Ошибка: " + e.getMessage());
    }

    /**
     * Преобразует результаты обработки в объект ProcessingStats
     */
    private ProcessingStats convertToProcessingStats(Map<String, Object> results) {
        ProcessingStats stats = new ProcessingStats();
        stats.setTotalCount((int) results.getOrDefault("totalCount", 0));
        stats.setSuccessCount((int) results.getOrDefault("successCount", 0));
        stats.setErrorCount((int) results.getOrDefault("errorCount", 0));
        stats.setProcessedData((List<Map<String, String>>) results.get("records"));

        if (results.containsKey("errors")) {
            List<String> errors = (List<String>) results.get("errors");
            errors.forEach(error -> stats.addError(error, "Ошибка обработки"));
        }

        return stats;
    }

    private boolean shouldProcessBatch(List<Map<String, String>> batch, int currentIndex, int totalSize) {
        return batch.size() >= BATCH_SIZE || currentIndex == totalSize - 1;
    }

    private Map<String, Object> processBatch(FileMetadata metadata,
                                             List<Map<String, String>> batch,
                                             Map<String, String> columnsMapping,
                                             int currentIndex,
                                             int totalSize) throws Exception {
        updateProcessingStatus(metadata.getId(),
                (currentIndex * 100) / totalSize,
                String.format("Обработка записей %d-%d из %d",
                        currentIndex - batch.size() + 1,
                        currentIndex + 1,
                        totalSize));

        return dataPersistenceService.saveEntities(batch, metadata.getClientId(), columnsMapping);
    }

    private void updateProcessingStatus(Long fileId, int progress, String message) {
        processingStatuses.put(fileId, new ProcessingStatus(progress, message));
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

        try {
            // Обновляем статус обработки в памяти
            updateProcessingStatus(fileId, -1, "Обработка отменена");

            // Обновляем статус в БД
            FileMetadata metadata = fileMetadataRepository.findById(fileId).orElse(null);
            if (metadata != null) {
                fileMetadataService.updateStatus(metadata, "CANCELLED", "Обработка отменена пользователем");
            }

            log.info("Обработка файла {} успешно отменена", fileId);
        } catch (Exception e) {
            log.error("Ошибка при отмене обработки файла: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Data
    @AllArgsConstructor
    public static class ProcessingStatus {
        private int progress;
        private String message;
    }

}