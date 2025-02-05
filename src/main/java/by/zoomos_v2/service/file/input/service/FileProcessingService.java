package by.zoomos_v2.service.file.input.service;

import by.zoomos_v2.exception.FileProcessingException;
import by.zoomos_v2.exception.ValidationError;
import by.zoomos_v2.mapping.ClientMappingConfig;
import by.zoomos_v2.model.FileMetadata;
import by.zoomos_v2.model.enums.OperationStatus;
import by.zoomos_v2.model.enums.OperationType;
import by.zoomos_v2.model.operation.ImportOperation;
import by.zoomos_v2.repository.FileMetadataRepository;
import by.zoomos_v2.service.file.BatchProcessingData;
import by.zoomos_v2.service.file.input.processor.FileProcessor;
import by.zoomos_v2.service.file.input.processor.FileProcessorFactory;
import by.zoomos_v2.service.file.input.callback.ProcessingProgressCallback;
import by.zoomos_v2.service.file.metadata.FileMetadataService;
import by.zoomos_v2.service.mapping.MappingConfigService;
import by.zoomos_v2.service.statistics.OperationStatsService;
import by.zoomos_v2.service.statistics.StatisticsProcessor;
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

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static by.zoomos_v2.constant.BatchSize.BATCH_SIZE_DATA_SAVE;
import static by.zoomos_v2.constant.BatchSize.BATCH_SIZE_FILE_RECORD;

/**
 * Сервис для асинхронной обработки файлов с оптимизированной производительностью и управлением памятью.
 * Обеспечивает потокобезопасную обработку файлов с журналированием и обработкой ошибок.
 *
 * @author Dimon
 * @version 2.0
 * @since 2024-01-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileProcessingService {
    private final FileMetadataService fileMetadataService;
    private final FileMetadataRepository fileMetadataRepository;
    private final DataPersistenceService dataPersistenceService;
    private final FileProcessorFactory processorFactory;
    private final MappingConfigService mappingConfigService;
    private final ObjectMapper objectMapper;
    private final PathResolver pathResolver;
    private final OperationStatsService operationStatsService;
    private final StatisticsProcessor statisticsProcessor;


    private final ConcurrentHashMap<Long, ProcessingStatus> processingStatuses = new ConcurrentHashMap<>();

    /**
     * Асинхронно обрабатывает загруженный файл
     */
    @Async("fileProcessingExecutor")
    public void processFileAsync(Long fileId) {
        log.debug("Начало асинхронной обработки файла с ID: {}", fileId);
        ImportOperation operation = null;
        BatchProcessingData batchData = null;

        try {
            // Получение метаданных файла
            FileMetadata metadata = fileMetadataRepository.findById(fileId)
                    .orElseThrow(() -> new FileProcessingException("Файл не найден"));

            // Инициализация операции
            operation = initializeImportOperation(metadata);

//            operationStatsService.updateOperationStatus(operation.getId(),
//                    OperationStatus.IN_PROGRESS, null, null);


            // Обновление статуса через StatisticsProcessor
            statisticsProcessor.updateOperationStatus(
                    operation.getId(),
                    OperationStatus.IN_PROGRESS,
                    "Начало обработки файла",
                    "PROCESSING_START"  // добавляем errorType
            );

            // Обновление статуса обработки
            updateProcessingStatus(fileId, 0, "Инициализация обработки");

            // Основной процесс обработки
            batchData = processFile(metadata, operation);

            // Обновление статистики через StatisticsProcessor
            statisticsProcessor.updateOperationStats(operation);

            // Обновление результатов
//            fileMetadataService.updateProcessingResults(metadata, operation);
            updateProcessingStatus(fileId, 100, "Обработка завершена");

        } catch (Exception e) {
            handleProcessingError(fileId, e, operation);
        } finally {
            if (batchData != null) {
                batchData.cleanup();
            }
            processingStatuses.remove(fileId);

            log.info("Завершена обработка файла с ID: {}. Статус: {}",
                    fileId,
                    operation != null ? operation.getStatus() : "UNKNOWN");
        }
    }

    private ImportOperation initializeImportOperation(FileMetadata metadata) {
        log.debug("Инициализация операции импорта для файла: {}", metadata.getOriginalFilename());

        ImportOperation operation = new ImportOperation();

        // Основные параметры операции
        operation.setClientId(metadata.getClientId());
        operation.setType(OperationType.IMPORT);
        operation.setStatus(OperationStatus.PENDING);
        operation.setSourceIdentifier(metadata.getOriginalFilename());

        // Параметры файла
        operation.setFileName(metadata.getOriginalFilename());
        operation.setFileSize(metadata.getSize());
        operation.setFileFormat(metadata.getFileType().name());
        operation.setContentType(metadata.getContentType());
        operation.setMappingConfigId(metadata.getMappingConfigId());
        operation.setEncoding(metadata.getEncoding());
        operation.setDelimiter(metadata.getDelimiter());

        // Создаем операцию через сервис статистики
        return operationStatsService.createOperation(operation);
    }

    /**
     * Выполняет основной процесс обработки файла
     *
     * @param metadata  метаданные обрабатываемого файла
     * @param operation операция импорта
     * @return объект с данными пакетной обработки
     */
    private BatchProcessingData processFile(FileMetadata metadata, ImportOperation operation) {
        log.debug("Начало обработки файла: {}", metadata.getOriginalFilename());
        LocalDateTime startTime = LocalDateTime.now();
        BatchProcessingData batchData = null;

        try {
            // Обработка сырых данных из файла
            batchData = processRawFile(metadata, operation);
            log.debug("Базовая обработка файла завершена. Прочитано записей: {}", operation.getTotalRecords());

            // Сохранение обработанных данных
            persistData(metadata, operation, batchData);
            log.debug("Сохранение данных завершено. Успешно обработано: {}", operation.getProcessedRecords());

            // Обновление итоговой статистики операции
            operation.setEndTime(LocalDateTime.now());
            statisticsProcessor.addPerformanceMetrics(operation, startTime);
            statisticsProcessor.updateOperationStats(operation);

            return batchData;

        } catch (Exception e) {
            log.error("Ошибка при обработке файла: {}", e.getMessage(), e);
            statisticsProcessor.handleOperationError(operation,
                    "Ошибка при обработке файла: " + e.getMessage(),
                    "FILE_PROCESSING_ERROR");
            throw new FileProcessingException("Ошибка при обработке файла", e);
        }
    }


    /**
     * Выполняет базовую обработку файла и создание временных данных
     *
     * @param metadata  метаданные файла
     * @param operation операция импорта
     * @return объект с данными пакетной обработки
     */
    private BatchProcessingData processRawFile(FileMetadata metadata, ImportOperation operation) {
        log.debug("Начало базовой обработки файла: {}", metadata.getOriginalFilename());
        updateProcessingStatus(metadata.getId(), 10, "Чтение файла");

        try {
            // Получаем путь к файлу
            Path filePath = pathResolver.getFilePath(metadata.getClientId(), metadata.getStoredFilename());
            if (!Files.exists(filePath)) {
                throw new FileProcessingException("Файл не найден: " + metadata.getOriginalFilename());
            }

            // Создаем объект для пакетной обработки
            BatchProcessingData batchData = BatchProcessingData.builder().build();

            // Получаем и настраиваем процессор
            FileProcessor processor = processorFactory.getProcessor(metadata);
            Map<String, Object> processorConfig = configureProcessor(metadata);
            operation.getMetadata().put("processorConfig", processorConfig);
            processor.configure(processorConfig);

            // Создаем обработчик прогресса
            ProcessingProgressCallback progressCallback = createProgressCallback(metadata, operation);

            // Обработка файла
            Map<String, Object> results = processor.processFile(filePath, metadata, progressCallback);

            // Обработка результатов
            processResults(results, batchData, operation);

            return batchData;

        } catch (Exception e) {
            String errorMsg = String.format("Ошибка при чтении файла %s: %s",
                    metadata.getOriginalFilename(), e.getMessage());
            statisticsProcessor.handleOperationError(operation, errorMsg, "FILE_READ_ERROR");
            throw new FileProcessingException(errorMsg, e);
        }
    }

    /**
     * Настраивает процессор файлов
     */
    private Map<String, Object> configureProcessor(FileMetadata metadata) {
        Map<String, Object> config = new HashMap<>();
        config.put("encoding", metadata.getEncoding());
        config.put("delimiter", metadata.getDelimiter());
        config.put("batchSize", BATCH_SIZE_FILE_RECORD);
        config.put("skipEmptyRows", true);
        config.put("trimFields", true);
        return config;
    }

    /**
     * Создает callback для отслеживания прогресса обработки
     *
     * @param metadata  метаданные файла
     * @param operation операция импорта
     * @return объект ProcessingProgressCallback
     */
    private ProcessingProgressCallback createProgressCallback(FileMetadata metadata, ImportOperation operation) {
        return new ProcessingProgressCallback() {
            private LocalDateTime lastUpdate = LocalDateTime.now();
            private static final long UPDATE_INTERVAL_MS = 1000; // Интервал обновления - 1 секунда

            @Override
            public void updateProgress(int progress, String message) {
                LocalDateTime now = LocalDateTime.now();

                // Обновляем статус только если прошло достаточно времени с последнего обновления
                if (ChronoUnit.MILLIS.between(lastUpdate, now) >= UPDATE_INTERVAL_MS) {
                    // Пересчитываем прогресс для текущего этапа (чтение файла - 10-30%)
                    int totalProgress = 10 + (progress * 20 / 100);
                    updateProcessingStatus(metadata.getId(), totalProgress, message);

                    // Сохраняем метрики прогресса
                    Map<String, Object> progressData = new HashMap<>();
                    progressData.put("rawProgress", progress);
                    progressData.put("totalProgress", totalProgress);
                    progressData.put("message", message);
                    progressData.put("timestamp", now);
                    progressData.put("memoryUsage", getMemoryUsage());
                    progressData.put("processingSpeed", calculateProcessingSpeed(operation));

                    operation.getMetadata().put("progressMetrics", progressData);

                    lastUpdate = now;
                }
            }

            private Map<String, Long> getMemoryUsage() {
                Runtime runtime = Runtime.getRuntime();
                Map<String, Long> memory = new HashMap<>();
                memory.put("total", runtime.totalMemory());
                memory.put("free", runtime.freeMemory());
                memory.put("used", runtime.totalMemory() - runtime.freeMemory());
                return memory;
            }

            private Double calculateProcessingSpeed(ImportOperation operation) {
                if (operation.getProcessedRecords() != null && operation.getStartTime() != null) {
                    long secondsElapsed = ChronoUnit.SECONDS.between(operation.getStartTime(), LocalDateTime.now());
                    if (secondsElapsed > 0) {
                        return operation.getProcessedRecords().doubleValue() / secondsElapsed;
                    }
                }
                return null;
            }
        };
    }

    /**
     * Обрабатывает результаты чтения файла
     */
    private void processResults(Map<String, Object> results, BatchProcessingData batchData,
                                ImportOperation operation) {
        // Обновляем статистику операции
        Long totalCount = (Long) results.getOrDefault("totalCount", 0L);
        operation.setTotalRecords(totalCount.intValue());
        Long successCount = (Long) results.getOrDefault("successCount", 0L);
        operation.setProcessedRecords(successCount.intValue());
        if (successCount > 0) {
            operation.setProcessedRecords(
                    (int) ((operation.getProcessedRecords() != null ? operation.getProcessedRecords() : 0) + successCount)
            );
        }

        // Обрабатываем ошибки валидации
        if (results.containsKey("validationErrors")) {
            @SuppressWarnings("unchecked")
            List<ValidationError> errors = (List<ValidationError>) results.get("validationErrors");
            errors.forEach(error ->
                    statisticsProcessor.handleOperationError(operation, error.getMessage(), error.getCode())
            );
        }

        // Сохраняем данные для дальнейшей обработки
        @SuppressWarnings("unchecked")
        List<String> headers = (List<String>) results.get("headers");
        batchData.setHeaders(headers);

        @SuppressWarnings("unchecked")
        List<Map<String, String>> records = (List<Map<String, String>>) results.get("records");
        batchData.setProcessedData(records);

        if (results.containsKey("tempFilePath")) {
            batchData.setTempFilePath((Path) results.get("tempFilePath"));
        }
    }


    /**
     * Сохраняет обработанные данные в базу данных
     *
     * @param metadata  метаданные файла
     * @param operation операция импорта
     * @param batchData данные для сохранения
     */
    private void persistData(FileMetadata metadata, ImportOperation operation, BatchProcessingData batchData) {
        log.debug("Начало сохранения данных для файла: {}", metadata.getOriginalFilename());
        LocalDateTime startSave = LocalDateTime.now();

        try {
            Map<String, String> columnsMapping = getMappingConfig(metadata.getMappingConfigId());
            List<Map<String, String>> currentBatch = new ArrayList<>();
            int totalProcessed = 0;

            // Читаем и обрабатываем данные из временного файла
            try (BufferedReader reader = Files.newBufferedReader(batchData.getTempFilePath())) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Map<String, String> record = objectMapper.readValue(line, new TypeReference<>() {
                    });
                    currentBatch.add(record);

                    if (shouldProcessBatch(currentBatch, totalProcessed, operation.getTotalRecords())) {
                        processBatch(metadata, operation, currentBatch, columnsMapping, totalProcessed);
                        totalProcessed += currentBatch.size();
                        currentBatch.clear();
                    }
                }

                // Обрабатываем оставшийся батч
                if (!currentBatch.isEmpty()) {
                    processBatch(metadata, operation, currentBatch, columnsMapping, totalProcessed);
                    totalProcessed += currentBatch.size();
                }
            }

            // Добавляем метрики сохранения
            Map<String, Object> saveMetrics = new HashMap<>();
            saveMetrics.put("totalProcessed", totalProcessed);
            saveMetrics.put("saveTimeSeconds",
                    ChronoUnit.SECONDS.between(startSave, LocalDateTime.now()));
            operation.getMetadata().put("saveMetrics", saveMetrics);

        } catch (Exception e) {
            String errorMsg = "Ошибка при сохранении данных: " + e.getMessage();
            statisticsProcessor.handleOperationError(operation, errorMsg, "DATA_SAVE_ERROR");
            throw new FileProcessingException(errorMsg, e);
        }
    }

    /**
     * Получает конфигурацию маппинга полей
     */
    private Map<String, String> getMappingConfig(Long mappingConfigId) {
        try {
            ClientMappingConfig mapping = mappingConfigService.getMappingById(mappingConfigId);
            return objectMapper.readValue(mapping.getColumnsConfig(), new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new FileProcessingException("Ошибка при получении конфигурации маппинга: " + e.getMessage(), e);
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
    private void handleProcessingError(Long fileId, Exception e, ImportOperation operation) {
        String errorMessage = String.format("Ошибка при обработке файла %d: %s", fileId, e.getMessage());
        log.error(errorMessage, e);

        if (operation != null) {
            // Обновление статуса через StatisticsProcessor
            statisticsProcessor.handleOperationError(
                    operation,
                    errorMessage,
                    e instanceof FileProcessingException ? "PROCESSING_ERROR" : "SYSTEM_ERROR"
            );
        }

        // Обновление статуса файла
//        fileMetadataService.markAsError(fileId, errorMessage);
        updateProcessingStatus(fileId, -1, "Ошибка: " + e.getMessage());
    }


    private boolean shouldProcessBatch(List<Map<String, String>> batch, int currentIndex, int totalSize) {
        return batch.size() >= BATCH_SIZE_DATA_SAVE || currentIndex + batch.size() == totalSize;
    }

    /**
     * Обрабатывает пакет данных
     */
    private void processBatch(FileMetadata metadata, ImportOperation operation,
                              List<Map<String, String>> batch, Map<String, String> columnsMapping,
                              int processedSoFar) {

        log.debug("Обработка батча {} записей (с {} по {}) из {}",
                batch.size(), processedSoFar + 1,
                processedSoFar + batch.size(), operation.getTotalRecords());
        int progress = (int)((processedSoFar * 100.0) / operation.getTotalRecords());
        updateProcessingStatus(metadata.getId(), progress,
                String.format("Обработано %d из %d записей", processedSoFar, operation.getTotalRecords()));
        try {
            // Сохраняем батч
            Map<String, Object> results = dataPersistenceService.saveEntities(
                    batch,
                    metadata.getClientId(),
                    columnsMapping,
                    metadata.getId()
            );

            // Обновляем статистику в операции
            operation.incrementProcessedRecords((Integer) results.getOrDefault("successCount", 0));

            // Обрабатываем ошибки, если они есть
            if (results.containsKey("errors")) {
                @SuppressWarnings("unchecked")
                List<String> errors = (List<String>) results.get("errors");
                errors.forEach(error -> operation.addError(error, "DATA_SAVE_ERROR"));
            }

            // Добавляем информацию о батче в метаданные
            addBatchMetrics(operation, results, processedSoFar);

        } catch (Exception e) {
            log.error("Ошибка при сохранении батча данных: {}", e.getMessage(), e);
            operation.addError("Ошибка сохранения батча: " + e.getMessage(), "BATCH_SAVE_ERROR");
            throw e;
        }
    }

    /**
     * Добавляет метрики пакетной обработки
     */
    private void addBatchMetrics(ImportOperation operation, Map<String, Object> batchResults, int processedSoFar) {
        Map<String, Object> batchMetrics = new HashMap<>();
        batchMetrics.put("processedRecords", processedSoFar);
        batchMetrics.put("successCount", batchResults.get("successCount"));
        batchMetrics.put("errorCount", batchResults.get("errorCount"));
        batchMetrics.put("timestamp", LocalDateTime.now().toString());

        // Добавляем или обновляем список метрик батчей
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> batchesMetrics = (List<Map<String, Object>>)
                operation.getMetadata().computeIfAbsent("batchesMetrics", k -> new ArrayList<>());
        batchesMetrics.add(batchMetrics);
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
//            if (metadata != null) {
//                fileMetadataService.updateStatus(metadata, "CANCELLED", "Обработка отменена пользователем");
//            }

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