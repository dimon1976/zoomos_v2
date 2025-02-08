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
import by.zoomos_v2.service.mapping.MappingConfigService;
import by.zoomos_v2.service.statistics.OperationProgressTracker;
import by.zoomos_v2.service.statistics.OperationStatsService;
import by.zoomos_v2.service.statistics.StatisticsProcessor;
import by.zoomos_v2.util.PathResolver;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
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

    private final FileMetadataRepository fileMetadataRepository;
    private final DataPersistenceService dataPersistenceService;
    private final FileProcessorFactory processorFactory;
    private final MappingConfigService mappingConfigService;
    private final ObjectMapper objectMapper;
    private final PathResolver pathResolver;
    private final OperationStatsService operationStatsService;
    private final StatisticsProcessor statisticsProcessor;
    private final OperationProgressTracker progressTracker;

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
            log.debug("Создана операция импорта с ID: {}", operation.getId());

            // Обновляем статус на IN_PROGRESS - используем непосредственно сервис
            operationStatsService.updateOperationStatus(
                    operation,
                    OperationStatus.IN_PROGRESS,
                    null,
                    null
            );
            // Присваиваем начальный прогресс
            progressTracker.trackProgress(operation,0,"Чтение файла");


            // Основной процесс обработки
            batchData = processFile(metadata, operation);

            // Если дошли до этой точки - обработка успешна
            log.debug("Файл {} успешно обработан, обновляем статус", fileId);

            // Проверяем на наличие ошибок
            boolean hasErrors = !operation.getErrors().isEmpty();
            OperationStatus finalStatus = hasErrors ?
                    OperationStatus.PARTIAL_SUCCESS : OperationStatus.COMPLETED;

            operation.setStatus(finalStatus);
            operation.setEndTime(LocalDateTime.now());
            // Записываем завершающий статус операции
            statisticsProcessor.updateOperationStats(operation);

            // Обновляем статус в памяти
            progressTracker.trackProgress(operation,100,"Завершено");
            log.info("Файл {} обработан. Прогресс: 100%, Статус: {}", fileId, finalStatus);
        } catch (Exception e) {
            statisticsProcessor.handleOperationError(operation, e.getMessage(),
                    e instanceof FileProcessingException ? "PROCESSING_ERROR" : "SYSTEM_ERROR");
        } finally {
            if (batchData != null) {
                batchData.cleanup();
            }
            // Логируем финальный статус
            if (operation != null) {
                progressTracker.trackProgress(
                        operation,
                        100,
                        operation.getStatus().name() + ": " + operation.getStatus().getDescription()
                );
                log.info("Завершена обработка файла с ID: {}. Статус: {}",
                        fileId, operation.getStatus());
            }
        }
    }

    public ImportOperation initializeImportOperation(FileMetadata metadata) {
        log.debug("Инициализация операции импорта для файла: {}", metadata.getOriginalFilename());

        ImportOperation operation = new ImportOperation();

        // Основные параметры операции
        operation.setFileId(metadata.getId());
        operation.setClientId(metadata.getClientId());
        operation.setType(OperationType.IMPORT);
        operation.setStatus(OperationStatus.PENDING);
        operation.setSourceIdentifier(metadata.getOriginalFilename());

        // Параметры файла
        operation.setFileName(metadata.getOriginalFilename());
        operation.setFileSize(metadata.getSize());
        operation.setProcessedRecords(0);
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
            progressTracker.trackProgress(operation,30,"Сохранение данных");
            persistData(metadata, operation, batchData);
            log.debug("Сохранение данных завершено. Успешно обработано: {}", operation.getProcessedRecords());

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


            // Обработка файла
            Map<String, Object> results = processor.processFile(filePath, metadata, (progress, message) ->
                    progressTracker.trackProgress(operation, progress, message));

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
     * Обрабатывает результаты чтения файла
     */
    private void processResults(Map<String, Object> results, BatchProcessingData batchData,
                                ImportOperation operation) {
        // Обновляем статистику операции
        Long totalCount = (Long) results.getOrDefault("totalCount", 0L);
        operation.setTotalRecords(totalCount.intValue());
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
     * Сохраняет обработанные данные в постоянное хранилище
     *
     * @param metadata     Метаданные импортируемого файла
     * @param operation    Текущая операция импорта
     * @param batchData    Данные для пакетной обработки
     * @throws FileProcessingException при ошибках сохранения данных
     */
    private void persistData(FileMetadata metadata, ImportOperation operation, BatchProcessingData batchData) {
//        log.debug("Начало сохранения данных для файла: {}", metadata.getOriginalFilename());
//        LocalDateTime startSave = LocalDateTime.now();
//
//        try {
//            Map<String, String> columnsMapping = getMappingConfig(metadata.getMappingConfigId());
//            List<Map<String, String>> currentBatch = new ArrayList<>();
//            int totalProcessed = 0;
//
//            // Читаем и обрабатываем данные из временного файла
//            try (BufferedReader reader = Files.newBufferedReader(batchData.getTempFilePath())) {
//                String line;
//                while ((line = reader.readLine()) != null) {
//                    Map<String, String> record = objectMapper.readValue(line, new TypeReference<>() {
//                    });
//                    currentBatch.add(record);
//
//                    if (shouldProcessBatch(currentBatch, totalProcessed, operation.getTotalRecords())) {
//                        processBatch(metadata, operation, currentBatch, columnsMapping, totalProcessed);
//                        totalProcessed += currentBatch.size();
//                        currentBatch.clear();
//
//                         // Обновляем прогресс: 30% + (сохраненные записи / общее количество * 70%)
//                        int saveProgress =30+ (int) ((totalProcessed / (double) operation.getTotalRecords()) * 70);
//                        progressTracker.trackProgress(operation, saveProgress, "Сохраняем в БД");
//                    }
//                }
//
//                // Обрабатываем оставшийся батч
//                if (!currentBatch.isEmpty()) {
//                    processBatch(metadata, operation, currentBatch, columnsMapping, totalProcessed);
//                    totalProcessed += currentBatch.size();
//                }
//            }
//
//            // Добавляем метрики сохранения
//            Map<String, Object> saveMetrics = new HashMap<>();
//            saveMetrics.put("totalProcessed", totalProcessed);
//            saveMetrics.put("saveTimeSeconds",
//                    ChronoUnit.SECONDS.between(startSave, LocalDateTime.now()));
//            operation.getMetadata().put("saveMetrics", saveMetrics);
//
//        } catch (Exception e) {
//            String errorMsg = "Ошибка при сохранении данных: " + e.getMessage();
//            statisticsProcessor.handleOperationError(operation, errorMsg, "DATA_SAVE_ERROR");
//            throw new FileProcessingException(errorMsg, e);
//        }
        log.debug("Начало сохранения данных для файла: {}", metadata.getOriginalFilename());
        LocalDateTime startSave = LocalDateTime.now();

        try {
            Map<String, String> columnsMapping = getMappingConfig(metadata.getMappingConfigId());
            List<Map<String, String>> currentBatch = new ArrayList<>();
            long totalProcessed = 0; // Изменен тип на long для больших файлов

            // Читаем и обрабатываем данные из временного файла
            try (BufferedReader reader = Files.newBufferedReader(batchData.getTempFilePath())) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Map<String, String> record = objectMapper.readValue(line, new TypeReference<>() {});
                    currentBatch.add(record);

                    if (shouldProcessBatch(currentBatch, totalProcessed, operation.getTotalRecords())) {
                        processBatch(metadata, operation, currentBatch, columnsMapping, totalProcessed);
                        totalProcessed += currentBatch.size();
                        currentBatch.clear();

                        // Обновляем прогресс: 30% + (сохраненные записи / общее количество * 70%)
                        double progressPercentage = (totalProcessed * 100.0) / operation.getTotalRecords();
                        int saveProgress = 30 + (int) ((progressPercentage * 70) / 100);
                        log.debug("Прогресс сохранения: {}%, totalProcessed: {}, total: {}",
                                saveProgress, totalProcessed, operation.getTotalRecords());
                        progressTracker.trackProgress(operation, saveProgress,
                                String.format("Сохраняем в БД: %d из %d записей",
                                        totalProcessed, operation.getTotalRecords()));
                    }
                }

                // Обрабатываем оставшийся батч
                if (!currentBatch.isEmpty()) {
                    processBatch(metadata, operation, currentBatch, columnsMapping, totalProcessed);
                    totalProcessed += currentBatch.size();

                    // Финальное обновление прогресса
                    double progressPercentage = (totalProcessed * 100.0) / operation.getTotalRecords();
                    int finalProgress = 30 + (int) ((progressPercentage * 70) / 100);
                    progressTracker.trackProgress(operation, finalProgress, "Завершение сохранения в БД");
                }
            }

            // Добавляем метрики сохранения
            Map<String, Object> saveMetrics = new HashMap<>();
            saveMetrics.put("totalProcessed", totalProcessed);
            saveMetrics.put("saveTimeSeconds", ChronoUnit.SECONDS.between(startSave, LocalDateTime.now()));
            operation.getMetadata().put("saveMetrics", saveMetrics);

        } catch (Exception e) {
            String errorMsg = "Ошибка при сохранении данных: " + e.getMessage();
            log.error(errorMsg, e);
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


    private boolean shouldProcessBatch(List<Map<String, String>> batch, long currentIndex, long totalSize) {
        return batch.size() >= BATCH_SIZE_DATA_SAVE || currentIndex + batch.size() == totalSize;
    }

    /**
     * Обрабатывает пакет данных для сохранения
     *
     * @param metadata       Метаданные файла
     * @param operation      Текущая операция импорта
     * @param batch         Пакет данных для обработки
     * @param columnsMapping Маппинг колонок
     * @param processedSoFar Количество обработанных записей
     * @throws RuntimeException при ошибках обработки батча
     */
    private void processBatch(FileMetadata metadata, ImportOperation operation,
                              List<Map<String, String>> batch, Map<String, String> columnsMapping,
                              long processedSoFar) {

//        log.debug("Обработка батча {} записей (с {} по {}) из {}",
//                batch.size(), processedSoFar + 1,
//                processedSoFar + batch.size(), operation.getTotalRecords());
//        int progress = (int) ((processedSoFar * 100.0) / operation.getTotalRecords());
//        progressTracker.trackProgress(operation, progress,
//                String.format("Сохраняем в БД:  %d из %d записей", processedSoFar, operation.getTotalRecords()));
//        try {
//            // Сохраняем батч
//            Map<String, Object> results = dataPersistenceService.saveEntities(
//                    batch,
//                    metadata.getClientId(),
//                    columnsMapping,
//                    metadata.getId()
//            );
//
//            // Обновляем статистику в операции
//            operation.incrementProcessedRecords((Integer) results.getOrDefault("successCount", 0));
//
//            // Обрабатываем ошибки, если они есть
//            if (results.containsKey("errors")) {
//                @SuppressWarnings("unchecked")
//                List<String> errors = (List<String>) results.get("errors");
//                errors.forEach(error -> operation.addError(error, "DATA_SAVE_ERROR"));
//            }
//
//            // Добавляем информацию о батче в метаданные
//            addBatchMetrics(operation, results, processedSoFar);
//
//        } catch (Exception e) {
//            log.error("Ошибка при сохранении батча данных: {}", e.getMessage(), e);
//            operation.addError("Ошибка сохранения батча: " + e.getMessage(), "BATCH_SAVE_ERROR");
//            throw e;
//        }
        log.debug("Обработка батча {} записей (с {} по {}) из {}",
                batch.size(), processedSoFar + 1,
                processedSoFar + batch.size(), operation.getTotalRecords());

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
    private void addBatchMetrics(ImportOperation operation, Map<String, Object> batchResults, long processedSoFar) {
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

    /**
     * Отменяет обработку файла.
     * Обновляет статус файла на CANCELLED и добавляет сообщение об отмене.
     *
     * @param fileId идентификатор файла для отмены обработки
     */

    @Transactional
    public void cancelProcessing(Long fileId) {
        try {
            FileMetadata metadata = fileMetadataRepository.findById(fileId)
                    .orElseThrow(() -> new FileProcessingException("Файл не найден"));

            ImportOperation operation = operationStatsService.findOperationByFileId(fileId)
                    .map(op -> (ImportOperation) op)
                    .orElseThrow(() -> new FileProcessingException("Операция не найдена"));

//            progressTracker.trackProgress(operation, -1, "Обработка отменена");
            operation.setStatus(OperationStatus.CANCELLED);
            operationStatsService.updateOperationStatus(operation, OperationStatus.CANCELLED,
                    "Обработка отменена пользователем", null);

            log.info("Обработка файла {} успешно отменена", fileId);
        } catch (Exception e) {
            log.error("Ошибка при отмене обработки файла: {}", e.getMessage(), e);
            throw e;
        }
    }

}