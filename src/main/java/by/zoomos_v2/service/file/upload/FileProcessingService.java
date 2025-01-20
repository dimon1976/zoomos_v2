package by.zoomos_v2.service.file.upload;

import by.zoomos_v2.constant.FileStatus;
import by.zoomos_v2.exception.FileProcessingException;
import by.zoomos_v2.mapping.ClientMappingConfig;
import by.zoomos_v2.model.FileMetadata;
import by.zoomos_v2.repository.FileMetadataRepository;
import by.zoomos_v2.service.client.ClientService;
import by.zoomos_v2.service.file.FileMetadataService;
import by.zoomos_v2.service.mapping.MappingConfigService;
import by.zoomos_v2.service.processing.client.ClientDataProcessor;
import by.zoomos_v2.service.processing.client.ClientDataProcessorFactory;
import by.zoomos_v2.service.processing.client.ValidationResult;
import by.zoomos_v2.service.processing.processor.FileProcessor;
import by.zoomos_v2.service.processing.processor.FileProcessorFactory;
import by.zoomos_v2.service.processing.processor.ProcessingStats;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static by.zoomos_v2.constant.BatchSize.BATCH_SIZE_DATA_SAVE;
import static by.zoomos_v2.constant.BatchSize.BATCH_SIZE_FILE_RECORD;

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
    private final ClientService clientService;
    private final ObjectMapper objectMapper;
    private final PathResolver pathResolver;


    private final ConcurrentHashMap<Long, ProcessingStatus> processingStatuses = new ConcurrentHashMap<>();

    /**
     * Асинхронно обрабатывает загруженный файл
     */
    @Async("fileProcessingExecutor")
    public void processFileAsync(Long fileId) {
        log.debug("Начало асинхронной обработки файла с ID: {}", fileId);
        LocalDateTime startTime = LocalDateTime.now();

        try {

            // Сначала получаем метаданные без изменения статуса
            FileMetadata metadata = fileMetadataRepository.findById(fileId)
                    .orElseThrow(() -> new FileProcessingException("Файл не найден"));
            // Проверяем статус
            if (FileStatus.PROCESSING.equals(metadata.getStatus())) {
                throw new FileProcessingException("Файл уже находится в процессе обработки");
            }

            if (metadata.isProcessingCompleted()) {
                throw new FileProcessingException("Файл уже обработан, статус: " + metadata.getStatus());
            }
            // Инициализация
            fileMetadataService.initializeProcessing(fileId);
            updateProcessingStatus(fileId, 0, "Инициализация обработки");


            // Основной процесс обработки
            ProcessingStats stats = processFile(metadata, startTime);

            // Обновление результатов
            fileMetadataService.updateProcessingResults(metadata, stats);
            updateProcessingStatus(fileId, 100, "Обработка завершена");

        } catch (Exception e) {
            handleProcessingError(fileId, e);
        } finally {
            // Очищаем статус обработки из памяти
            processingStatuses.remove(fileId);
        }
    }

    /**
     * Выполняет основной процесс обработки файла
     */
    private ProcessingStats processFile(FileMetadata metadata, LocalDateTime startTime) throws Exception {
        try {
            metadata.updateStatus(FileStatus.PROCESSING, null);

            // Базовая обработка файла
            ProcessingStats fileStats = processRawFile(metadata);
            log.debug("Базовая обработка файла завершена. Получено записей: {}", fileStats.getTotalCount());

            // Клиентская обработка
//            ProcessingStats clientStats = processForClient(metadata, fileStats.getProcessedData());
//            log.debug("Клиентская обработка завершена. Обработано записей: {}", clientStats.getSuccessCount());

            // Сохранение данных
            ProcessingStats persistenceStats = persistData(metadata, fileStats.getProcessedData());
            log.debug("Сохранение данных завершено. Сохранено записей: {}", persistenceStats.getSuccessCount());

            // Объединяем статистику и добавляем время обработки
            ProcessingStats finalStats = ProcessingStats.merge(fileStats, persistenceStats);
            finalStats.setProcessingTimeSeconds(ChronoUnit.SECONDS.between(startTime, LocalDateTime.now()));

            // Обновляем статус и статистику в метаданных файла
            metadata.updateStatus(FileStatus.COMPLETED, null);
            metadata.updateProcessingStatistics(
                    finalStats.getTotalCount(),
                    finalStats.getSuccessCount(),
                    finalStats.getErrorCount()
            );

            addAdditionalStats(finalStats);
            return finalStats;

        } catch (Exception e) {
            log.error("Ошибка при обработке файла {}: {}", metadata.getOriginalFilename(), e.getMessage(), e);
            metadata.updateStatus(FileStatus.ERROR, e.getMessage());
            metadata.addProcessingError(e.getMessage());
            throw e;
        }

    }

    /**
     * Выполняет базовую обработку файла
     */
    private ProcessingStats processRawFile(FileMetadata metadata) {
        log.debug("Начало базовой обработки файла: {}", metadata.getOriginalFilename());
        updateProcessingStatus(metadata.getId(), 10, "Чтение файла");

        try {
            // Получаем путь к файлу
            Path filePath = pathResolver.getFilePath(metadata.getClientId(), metadata.getStoredFilename());
            if (!filePath.toFile().exists()) {
                throw new FileProcessingException("Файл не найден: " + metadata.getOriginalFilename());
            }

            // Получаем процессор для типа файла
            FileProcessor processor = processorFactory.getProcessor(metadata);

            // Обработка файла с отслеживанием прогресса
            Map<String, Object> results = processor.processFile(filePath, metadata,
                    (progress, message) -> updateProcessingStatus(
                            metadata.getId(),
                            10 + (int)(progress * 0.2), // от 10% до 30% общего прогресса
                            "Чтение файла: " + message
                    ));

            // Конвертация результатов
            ProcessingStats stats = convertToProcessingStats(results);

            if (stats.getTotalCount() == 0) {
                throw new FileProcessingException("Файл не содержит данных для обработки");
            }

            log.debug("Базовая обработка файла завершена. Прочитано записей: {}", stats.getTotalCount());
            return stats;

        } catch (Exception e) {
            String errorMsg = "Ошибка при чтении файла " + metadata.getOriginalFilename() + ": " + e.getMessage();
            log.error(errorMsg, e);
            throw new FileProcessingException(errorMsg, e);
        }
    }

    /**
     * Обрабатывает данные согласно правилам клиента при загрузке файла
     */
    private ProcessingStats processForClient(FileMetadata metadata, List<Map<String, String>> data) {
        // Получаем процессор для клиента
        ClientDataProcessor processor = clientProcessorFactory.getProcessor(clientService.getClientById(metadata.getClientId()));
        ProcessingStats stats = ProcessingStats.createNew();

        try {
            // Устанавливаем общее количество записей
            stats.setTotalCount(data.size());

            // Проверяем валидность данных
            ValidationResult validationResult = processor.validateData(data);
            if (!validationResult.isValid()) {
                // Если данные не валидны, добавляем ошибки в статистику
                validationResult.getErrors().forEach(error ->
                        stats.incrementErrorCount(error, "VALIDATION_ERROR"));

                // Обновляем статус файла
                metadata.updateStatus(FileStatus.ERROR, "Ошибка валидации данных");
                validationResult.getErrors().forEach(metadata::addProcessingError);

                return stats;
            }

            // Запускаем обработку файла
            processor.processFile(metadata, data);

            // Обновляем статистику исходя из результатов обработки файла
            stats.setSuccessCount(metadata.getSuccessRecords());
            stats.setErrorCount(metadata.getFailedRecords());

            // Если есть ошибки обработки, добавляем их в статистику
            if (metadata.getProcessingErrors() != null) {
                metadata.getProcessingErrors().forEach(error ->
                        stats.incrementErrorCount(error, "PROCESSING_ERROR"));
            }

            // Сохраняем обработанные данные
            stats.setProcessedData(data);

            // Выполняем пост-обработку
            processor.afterProcessing(metadata);

            return stats;

        } catch (Exception e) {
            log.error("Ошибка при обработке данных для клиента {}: {}",
                    metadata.getClientId(), e.getMessage(), e);

            // Обновляем статус файла
            metadata.updateStatus(FileStatus.ERROR, e.getMessage());
            metadata.addProcessingError(e.getMessage());

            // Добавляем ошибку в статистику
            stats.incrementErrorCount(e.getMessage(), "SYSTEM_ERROR");

            return stats;
        }
    }

    /**
     * Сохраняет обработанные данные в базу данных
     */
    private ProcessingStats persistData(FileMetadata metadata, List<Map<String, String>> data) throws Exception {

        log.debug("Начало сохранения данных для файла: {}", metadata.getOriginalFilename());
        ProcessingStats stats = new ProcessingStats();
        stats.setTotalCount(data.size());

        try {
            // Получаем и проверяем конфигурацию маппинга
            if (metadata.getMappingConfigId() == null) {
                throw new FileProcessingException("Не указана конфигурация маппинга");
            }

            ClientMappingConfig mapping = mappingConfigService.getMappingById(metadata.getMappingConfigId());
            Map<String, String> columnsMapping = objectMapper.readValue(
                    mapping.getColumnsConfig(),
                    new TypeReference<>() {}
            );

            // Обработка данных батчами
            List<Map<String, String>> currentBatch = new ArrayList<>();
            int processedRecords = 0;
            int totalRecords = data.size();
            int successCount = 0;
            int errorCount = 0;

            for (int i = 0; i < totalRecords; i++) {
                currentBatch.add(data.get(i));

                if (shouldProcessBatch(currentBatch, i, totalRecords)) {
                    // Обработка батча
                    Map<String, Object> batchResults = processBatch(
                            metadata,
                            currentBatch,
                            columnsMapping,
                            processedRecords,
                            totalRecords
                    );

                    // Обновление статистики
                    successCount += (int) batchResults.get("successCount");
                    errorCount += (int) batchResults.get("errorCount");

                    // Обработка ошибок батча
                    if (batchResults.containsKey("errors")) {
                        List<String> errors = (List<String>) batchResults.get("errors");
                        errors.forEach(error -> {
                            metadata.addProcessingError(error);
                            stats.incrementErrorCount(error, "SAVE_ERROR");
                        });
                    }

                    processedRecords += currentBatch.size();
                    currentBatch.clear();

                    // Обновляем прогресс
                    updateProcessingStatus(metadata.getId(),
                            70 + (processedRecords * 30 / totalRecords),
                            String.format("Сохранено %d из %d записей", processedRecords, totalRecords));
                }
            }

            // Обновляем итоговую статистику
            stats.setSuccessCount(successCount);
            stats.setErrorCount(errorCount);

            log.debug("Сохранение данных завершено. Успешно: {}, Ошибок: {}",
                    successCount, errorCount);
            return stats;

        } catch (Exception e) {
            String errorMsg = "Ошибка при сохранении данных: " + e.getMessage();
            log.error(errorMsg, e);
            throw new FileProcessingException(errorMsg, e);
        }


    }

    /**
     * Добавляет дополнительную статистику к результатам обработки
     */
    private void addAdditionalStats(ProcessingStats stats) {
        // Скорость обработки
        if (stats.getProcessingTimeSeconds() > 0) {
            double speed = (double) stats.getTotalCount() / stats.getProcessingTimeSeconds();
            stats.addAdditionalStat("Скорость обработки (записей/сек)", String.format("%.2f", speed));
        }

        // Размер пакета
        stats.addAdditionalStat("Размер пакета при загрузке файла", BATCH_SIZE_FILE_RECORD);
        stats.addAdditionalStat("Размер пакета при сохранении в БД", BATCH_SIZE_DATA_SAVE);

        // Процент успешных записей
        if (stats.getTotalCount() > 0) {
            double successRate = (double) stats.getSuccessCount() / stats.getTotalCount() * 100;
            stats.addAdditionalStat("Процент успешных записей", String.format("%.2f%%", successRate));
        }
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

        // Основные показатели
        stats.setTotalCount((int) results.getOrDefault("totalCount", 0));
        stats.setSuccessCount((int) results.getOrDefault("successCount", 0));
        stats.setErrorCount((int) results.getOrDefault("errorCount", 0));

        // Обработанные данные
        @SuppressWarnings("unchecked")
        List<Map<String, String>> records = (List<Map<String, String>>) results.get("records");
        stats.setProcessedData(records != null ? records : new ArrayList<>());

        // Обработка ошибок
        if (results.containsKey("errors")) {
            @SuppressWarnings("unchecked")
            List<String> errors = (List<String>) results.get("errors");
            errors.forEach(error -> stats.incrementErrorCount(error, "PARSE_ERROR"));
        }

        return stats;

    }

    private boolean shouldProcessBatch(List<Map<String, String>> batch, int currentIndex, int totalSize) {
        return batch.size() >= BATCH_SIZE_FILE_RECORD || currentIndex == totalSize - 1;
    }

    private Map<String, Object> processBatch(FileMetadata metadata,
                                             List<Map<String, String>> batch,
                                             Map<String, String> columnsMapping,
                                             int processedRecords,
                                             int totalRecords) {

        log.debug("Обработка батча {} записей (с {} по {}) из {}",
                batch.size(),
                processedRecords + 1,
                processedRecords + batch.size(),
                totalRecords);

        // Обновляем статус обработки
        updateProcessingStatus(metadata.getId(),
                70 + (processedRecords * 30 / totalRecords),
                String.format("Сохранение записей %d-%d из %d",
                        processedRecords + 1,
                        processedRecords + batch.size(),
                        totalRecords));

        // Сохраняем данные
        return dataPersistenceService.saveEntities(
                batch,
                metadata.getClientId(),
                columnsMapping,
                metadata.getId()
        );

    }

    private void updateProcessingStatus(Long fileId, int progress, String message) {
        ProcessingStatus status = new ProcessingStatus(progress, message);
        processingStatuses.put(fileId, status);
        log.debug("Обновлен статус обработки файла {}: {}% - {}", fileId, progress, message);
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