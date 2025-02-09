package by.zoomos_v2.service.file.input.service;

import by.zoomos_v2.exception.FileProcessingException;
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
import by.zoomos_v2.service.statistics.OperationStateManager;
import by.zoomos_v2.service.statistics.OperationStatsService;
import by.zoomos_v2.service.statistics.StatisticsProcessor;
import by.zoomos_v2.util.PathResolver;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Оптимизированный сервис для асинхронной обработки файлов.
 * Реализует параллельную обработку данных и эффективное управление памятью.
 *
 * @author Dimon
 * @version 3.0
 * @since 2024-02-09
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileProcessingService {
    private static final int CHUNK_SIZE = 1000;
    private static final int PARALLEL_CHUNKS = Runtime.getRuntime().availableProcessors() * 2;

    private final FileMetadataRepository fileMetadataRepository;
    private final DataPersistenceService dataPersistenceService;
    private final FileProcessorFactory processorFactory;
    private final MappingConfigService mappingConfigService;
    private final ObjectMapper objectMapper;
    private final PathResolver pathResolver;
    private final OperationStatsService operationStatsService;
    private final StatisticsProcessor statisticsProcessor;
    private final OperationProgressTracker progressTracker;
    private final OperationStateManager operationStateManager;

    @Qualifier("fileProcessingExecutor")
    private final Executor fileProcessingExecutor;

    /**
     * Асинхронно обрабатывает файл с использованием параллельной обработки данных.
     * Метод разбит на логические этапы с возможностью отмены операции на любом этапе.
     *
     * @param fileId идентификатор файла для обработки
     */
    @Async("fileProcessingExecutor")
    public void processFileAsync(Long fileId) {
        log.info("Начало асинхронной обработки файла: {}", fileId);
        ImportOperation operation = null;
        CompletableFuture<BatchProcessingData> fileReadingFuture = null;
        CompletableFuture<Boolean> dataPersistenceFuture = null;

        try {
            // Этап 1: Инициализация операции
            FileMetadata metadata = getFileMetadata(fileId);
            operation = initializeOperation(metadata);
            log.debug("Операция инициализирована: {}", operation.getId());

            // Этап 2: Асинхронное чтение файла
            fileReadingFuture = readFileAsync(metadata, operation);

            // Этап 3: Асинхронная обработка и сохранение данных
            BatchProcessingData batchData = fileReadingFuture.get(30, TimeUnit.MINUTES);
            if (!operationStateManager.isCancelled(operation.getId())) {
                dataPersistenceFuture = persistDataAsync(metadata, operation, batchData);
                dataPersistenceFuture.get(60, TimeUnit.MINUTES);
            }

            // Этап 4: Завершение операции
            finalizeOperation(operation);

        } catch (Exception e) {
            handleProcessingError(operation, e);
        } finally {
            cleanup(operation, fileReadingFuture, dataPersistenceFuture);
        }
    }

    /**
     * Асинхронно читает данные из файла
     */
    private CompletableFuture<BatchProcessingData> readFileAsync(FileMetadata metadata, ImportOperation operation) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Начало асинхронного чтения файла: {}", metadata.getOriginalFilename());
                progressTracker.trackProgress(operation, 0, "Чтение файла");

                // Валидация пути к файлу
                Path filePath = pathResolver.getFilePath(metadata.getClientId(), metadata.getStoredFilename());
                if (!Files.exists(filePath)) {
                    throw new FileProcessingException("Файл не найден по пути: " + filePath);
                }

                // Настройка и получение процессора
                FileProcessor processor = setupFileProcessor(metadata);
                Map<String, Object> processorResults = processor.processFile(filePath, metadata,
                        (progress, message) -> progressTracker.trackProgress(operation, (int) (progress * 0.4), message));

                log.debug("Получены результаты обработки файла: {}", metadata.getOriginalFilename());

                // Проверка результатов
                if (processorResults == null) {
                    throw new FileProcessingException("Процессор вернул null вместо результатов обработки");
                }

                // Логируем состояние результатов для отладки
                log.debug("Содержимое результатов обработки: {}",
                        processorResults.keySet().stream()
                                .map(key -> key + "=" + (processorResults.get(key) != null ?
                                        processorResults.get(key).getClass().getSimpleName() : "null"))
                                .collect(Collectors.joining(", ")));

                // Создание и заполнение объекта BatchProcessingData
                BatchProcessingData batchData = new BatchProcessingData();
                processResults(processorResults, batchData, operation);

                log.debug("Чтение файла завершено успешно");
                progressTracker.trackProgress(operation, 40, "Чтение файла завершено");

                return batchData;

            } catch (Exception e) {
                log.error("Критическая ошибка при чтении файла {}: {}",
                        metadata.getOriginalFilename(), e.getMessage());
                log.debug("Детальная информация об ошибке:", e);
                throw new CompletionException("Ошибка при чтении файла: " + e.getMessage(), e);
            }
        }, fileProcessingExecutor);
    }

    /**
     * Асинхронно сохраняет обработанные данные в БД
     */
    private CompletableFuture<Boolean> persistDataAsync(FileMetadata metadata,
                                                        ImportOperation operation,
                                                        BatchProcessingData batchData) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Начало асинхронного сохранения данных: {}", metadata.getOriginalFilename());
                progressTracker.trackProgress(operation, 41, "Подготовка к сохранению данных");

                Map<String, String> columnsMapping = getMappingConfig(metadata.getMappingConfigId());

                // Создаем пул для параллельной обработки
                ExecutorService chunkExecutor = Executors.newFixedThreadPool(PARALLEL_CHUNKS);
                List<CompletableFuture<Void>> futures = new ArrayList<>();

                try {
                    // Обрабатываем файл партиями
                    final AtomicInteger processedCount = new AtomicInteger(0);

                    batchData.processTempFileInBatches(CHUNK_SIZE, batch -> {
                        if (!operationStateManager.isCancelled(operation.getId())) {
                            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                                try {
                                    Map<String, Object> results = dataPersistenceService.saveEntities(
                                            batch,
                                            metadata.getClientId(),
                                            columnsMapping,
                                            metadata.getId()
                                    );

                                    synchronized (operation) {
                                        updateOperationProgress(operation, results, operation.getTotalRecords());
                                    }

                                    int currentProcessed = processedCount.addAndGet(batch.size());
                                    updateProgressMessage(operation, currentProcessed);

                                } catch (Exception e) {
                                    log.error("Ошибка при сохранении батча: {}", e.getMessage());
                                    throw new CompletionException(e);
                                }
                            }, chunkExecutor);

                            futures.add(future);
                        }
                    });

                    // Ожидаем завершения всех задач
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                            .get(30, TimeUnit.MINUTES);

                    return true;
                } finally {
                    chunkExecutor.shutdown();
                    if (!chunkExecutor.awaitTermination(5, TimeUnit.MINUTES)) {
                        chunkExecutor.shutdownNow();
                    }
                }
            } catch (Exception e) {
                log.error("Ошибка при сохранении данных: {}", e.getMessage(), e);
                throw new CompletionException(e);
            }
        }, fileProcessingExecutor);
    }

    private void updateOperationProgress(ImportOperation operation,
                                         Map<String, Object> results,
                                         int chunkSize) {
        int successCount = (Integer) results.getOrDefault("successCount", 0);
        operation.incrementProcessedRecords(successCount);

        // Обновляем прогресс
        int progress = 41 + (int) ((((double) operation.getProcessedRecords()) / operation.getTotalRecords()) * 58);
        String message = String.format("Обработано записей: %d из %d",
                operation.getProcessedRecords(), operation.getTotalRecords());
        progressTracker.trackProgress(operation, Math.min(99, progress), message);

        if (results.containsKey("errors")) {
            @SuppressWarnings("unchecked")
            List<String> errors = (List<String>) results.get("errors");
            errors.forEach(error -> operation.addError(error, "DATA_SAVE_ERROR"));
        }
    }

    // Вспомогательные методы...

    private FileMetadata getFileMetadata(Long fileId) {
        return fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new FileProcessingException("Файл не найден: " + fileId));
    }

    private ImportOperation initializeOperation(FileMetadata metadata) {
        ImportOperation operation = createInitialOperation(metadata);
        operationStatsService.updateOperationStatus(operation, OperationStatus.IN_PROGRESS, null, null);
        return operation;
    }

    private ImportOperation createInitialOperation(FileMetadata metadata) {
        ImportOperation operation = new ImportOperation();
        operation.setFileId(metadata.getId());
        operation.setClientId(metadata.getClientId());
        operation.setProcessedRecords(0);
        operation.setType(OperationType.IMPORT);
        operation.setStatus(OperationStatus.PENDING);
        operation.setSourceIdentifier(metadata.getOriginalFilename());
        operation.setFileName(metadata.getOriginalFilename());
        operation.setFileSize(metadata.getSize());
        operation.setFileFormat(metadata.getFileType().name());
        operation.setContentType(metadata.getContentType());
        operation.setMappingConfigId(metadata.getMappingConfigId());
        operation.setEncoding(metadata.getEncoding());
        operation.setDelimiter(metadata.getDelimiter());

        return operationStatsService.createOperation(operation);
    }

    private void finalizeOperation(ImportOperation operation) {
        if (operation.getStatus() != OperationStatus.CANCELLED) {
            boolean hasErrors = !operation.getErrors().isEmpty();
            OperationStatus finalStatus = hasErrors ?
                    OperationStatus.PARTIAL_SUCCESS : OperationStatus.COMPLETED;

            operation.setStatus(finalStatus);
            operation.setEndTime(LocalDateTime.now());
            statisticsProcessor.updateOperationStats(operation);

            String finalMessage = String.format("Обработка завершена: обработано %d записей",
                    operation.getProcessedRecords());
            progressTracker.trackProgress(operation, 100, finalMessage);
        }
    }

    private void handleProcessingError(ImportOperation operation, Exception e) {
        if (operation != null && !operationStateManager.isCancelled(operation.getId())) {
            String errorType = e instanceof FileProcessingException ?
                    "PROCESSING_ERROR" : "SYSTEM_ERROR";
            statisticsProcessor.handleOperationError(operation, e.getMessage(), errorType);
        }
        log.error("Ошибка обработки файла: {}", e.getMessage(), e);
    }

    private void cleanup(ImportOperation operation,
                         CompletableFuture<BatchProcessingData> readingFuture,
                         CompletableFuture<Boolean> persistenceFuture) {
        try {
            if (operation != null) {
                operationStateManager.cleanup(operation.getId());
            }
            if (readingFuture != null && !readingFuture.isDone()) {
                readingFuture.cancel(true);
            }
            if (persistenceFuture != null && !persistenceFuture.isDone()) {
                persistenceFuture.cancel(true);
            }
        } catch (Exception e) {
            log.error("Ошибка при очистке ресурсов: {}", e.getMessage());
        }
    }

    private void updateProgressMessage(ImportOperation operation, int currentProcessed) {
        int progress = (int) ((currentProcessed * 58.0) / operation.getTotalRecords()) + 41;
        String message = String.format("Сохранено записей: %d из %d",
                currentProcessed, operation.getTotalRecords());
        progressTracker.trackProgress(operation, Math.min(99, progress), message);
    }

    /**
     * Настраивает процессор файлов с оптимальными параметрами
     */
    private FileProcessor setupFileProcessor(FileMetadata metadata) {
        FileProcessor processor = processorFactory.getProcessor(metadata);
        processor.configure(createProcessorConfig(metadata));
        return processor;
    }

    /**
     * Создает оптимизированную конфигурацию для процессора
     */
    private Map<String, Object> createProcessorConfig(FileMetadata metadata) {
        return Map.of(
                "encoding", metadata.getEncoding(),
                "delimiter", metadata.getDelimiter(),
                "batchSize", CHUNK_SIZE,
                "skipEmptyRows", true,
                "trimFields", true,
                "bufferSize", 8192,
                "parallelProcessing", true
        );
    }

    /**
     * Обрабатывает результаты чтения файла
     */
    private void processResults(Map<String, Object> results, BatchProcessingData batchData,
                                ImportOperation operation) {
        log.debug("Обработка результатов чтения файла");

        // Получаем и проверяем заголовки
        @SuppressWarnings("unchecked")
        List<String> headers = (List<String>) results.get("headers");
        if (headers == null || headers.isEmpty()) {
            log.error("Заголовки отсутствуют или пусты");
            throw new FileProcessingException("Не найдены заголовки в файле");
        }
        batchData.setHeaders(headers);
        log.debug("Получены заголовки: {}", headers);

        // Обрабатываем путь к временному файлу
        Path tempPath = (Path) results.get("tempFilePath");
        if (tempPath == null || !Files.exists(tempPath)) {
            throw new FileProcessingException("Временный файл не найден или недоступен");
        }
        batchData.setTempFilePath(tempPath);

        // Пустой список для ProcessedData, так как данные находятся во временном файле
        batchData.setProcessedData(new ArrayList<>());

        log.debug("Установлен временный файл: {}", tempPath);

        // Обновляем статистику операции
        Long totalCount = (Long) results.get("totalCount");
        if (totalCount == null || totalCount == 0) {
            throw new FileProcessingException("Некорректное количество записей в файле");
        }
        operation.setTotalRecords(totalCount.intValue());
        log.debug("Установлено общее количество записей: {}", totalCount);

        // Проверяем размер временного файла
        try {
            long fileSize = Files.size(tempPath);
            log.debug("Размер временного файла: {} байт", fileSize);
            if (fileSize == 0) {
                throw new FileProcessingException("Временный файл пуст");
            }
        } catch (IOException e) {
            throw new FileProcessingException("Ошибка при проверке временного файла", e);
        }

        // Добавляем метрики обработки
        Map<String, Object> processMetrics = new HashMap<>();
        processMetrics.put("startTime", LocalDateTime.now().toString());
        processMetrics.put("totalRecords", totalCount);
        operation.getMetadata().put("processMetrics", processMetrics);

        log.debug("Обработка результатов завершена успешно");
    }

    /**
     * Получает конфигурацию маппинга из кэша или БД
     */
    private Map<String, String> getMappingConfig(Long mappingConfigId) {
        try {
            ClientMappingConfig mapping = mappingConfigService.getMappingById(mappingConfigId);
            return objectMapper.readValue(mapping.getColumnsConfig(),
                    new TypeReference<Map<String, String>>() {
                    });
        } catch (Exception e) {
            throw new FileProcessingException(
                    "Ошибка при получении конфигурации маппинга: " + e.getMessage(), e);
        }
    }

    /**
     * Отменяет обработку файла с очисткой ресурсов
     */
    @Transactional
    public void cancelProcessing(Long fileId) {
        try {
            ImportOperation operation = operationStatsService.findOperationByFileId(fileId)
                    .map(op -> (ImportOperation) op)
                    .orElseThrow(() -> new FileProcessingException("Операция не найдена"));

            operationStateManager.markAsCancelled(operation.getId());
            operation.setStatus(OperationStatus.CANCELLED);
            operation.setEndTime(LocalDateTime.now());

            // Добавляем информацию об отмене в метаданные
            Map<String, Object> cancelInfo = new HashMap<>();
            cancelInfo.put("cancelTime", LocalDateTime.now().toString());
            cancelInfo.put("processedBeforeCancel", operation.getProcessedRecords());
            operation.getMetadata().put("cancelInfo", cancelInfo);

            progressTracker.trackProgress(operation, operation.getCurrentProgress(),
                    "Обработка отменена пользователем");

            statisticsProcessor.updateOperationStats(operation);

        } catch (Exception e) {
            log.error("Ошибка при отмене обработки файла: {}", e.getMessage());
            throw e;
        }
    }

//    private final FileMetadataRepository fileMetadataRepository;
//    private final DataPersistenceService dataPersistenceService;
//    private final FileProcessorFactory processorFactory;
//    private final MappingConfigService mappingConfigService;
//    private final ObjectMapper objectMapper;
//    private final PathResolver pathResolver;
//    private final OperationStatsService operationStatsService;
//    private final StatisticsProcessor statisticsProcessor;
//    private final OperationProgressTracker progressTracker;
//    private final OperationStateManager operationStateManager;
//
//    /**
//     * Асинхронно обрабатывает загруженный файл с поддержкой отмены операции.
//     * Использует настроенный пул потоков для параллельной обработки.
//     *
//     * @param fileId идентификатор файла для обработки
//     */
//    @Async("fileProcessingExecutor")
//    public void processFileAsync(Long fileId) {
//        // TODO Очистить код от дублирования и избыточности
//        log.debug("Начало асинхронной обработки файла с ID: {}", fileId);
//        ImportOperation operation = null;
//        BatchProcessingData batchData = null;
//
//        try {
//            // Получение метаданных файла
//            FileMetadata metadata = fileMetadataRepository.findById(fileId)
//                    .orElseThrow(() -> new FileProcessingException("Файл не найден"));
//
//            // Инициализация операции
//            operation = initializeImportOperation(metadata);
//            log.debug("Создана операция импорта с ID: {}", operation.getId());
//
//            // Обновляем статус на IN_PROGRESS - используем непосредственно сервис
//            operationStatsService.updateOperationStatus(
//                    operation,
//                    OperationStatus.IN_PROGRESS,
//                    null,
//                    null
//            );
//            // Присваиваем начальный прогресс
//            progressTracker.trackProgress(operation, 0, "Чтение файла");
//
//
//            // Основной процесс обработки
//            batchData = processFile(metadata, operation);
//
//            // Если дошли до этой точки - обработка успешна
//            log.debug("Файл {} успешно обработан, обновляем статус", fileId);
//
//            // Проверяем на наличие ошибок
//            boolean hasErrors = !operation.getErrors().isEmpty();
//            OperationStatus finalStatus = null;
//
//            if (operation.getStatus() != OperationStatus.CANCELLED) {
//                finalStatus = hasErrors ? OperationStatus.PARTIAL_SUCCESS : OperationStatus.COMPLETED;
//            } else {
//                finalStatus = OperationStatus.CANCELLED;
//            }
//
//            operation.setStatus(finalStatus);
//            operation.setEndTime(LocalDateTime.now());
//            // Записываем завершающий статус операции
//            statisticsProcessor.updateOperationStats(operation);
//
//            // Обновляем прогресс только если не было отмены
//            if (!operationStateManager.isCancelled(operation.getId())) {
//                String finalMessage = String.format("Обработка завершена: обработано %d записей", operation.getProcessedRecords());
//                progressTracker.trackProgress(operation, 100, finalMessage);
//                log.info("Файл {} обработан. Прогресс: 100%, Статус: {}", fileId, finalStatus);
//            }
//        } catch (Exception e) {
//            // Проверяем, не была ли операция отменена перед обработкой ошибки
//            if (!operationStateManager.isCancelled(operation.getId())) {
//                statisticsProcessor.handleOperationError(operation, e.getMessage(),
//                        e instanceof FileProcessingException ? "PROCESSING_ERROR" : "SYSTEM_ERROR");
//            }
//        } finally {
//            if (batchData != null) {
//                batchData.cleanup();
//            }
//            // Логируем финальный статус
//            if (operation != null) {
//                operationStateManager.cleanup(operation.getId()); // Очищаем состояние
//            }
//            log.info("Завершена обработка файла с ID: {}. Статус: {}",
//                    fileId, operation != null ? operation.getStatus() : "UNKNOWN");
//        }
//    }
//
//    public ImportOperation initializeImportOperation(FileMetadata metadata) {
//        log.debug("Инициализация операции импорта для файла: {}", metadata.getOriginalFilename());
//
//        ImportOperation operation = new ImportOperation();
//
//        // Основные параметры операции
//        operation.setFileId(metadata.getId());
//        operation.setClientId(metadata.getClientId());
//        operation.setType(OperationType.IMPORT);
//        operation.setStatus(OperationStatus.PENDING);
//        operation.setSourceIdentifier(metadata.getOriginalFilename());
//
//        // Параметры файла
//        operation.setFileName(metadata.getOriginalFilename());
//        operation.setFileSize(metadata.getSize());
//        operation.setProcessedRecords(0);
//        operation.setFileFormat(metadata.getFileType().name());
//        operation.setContentType(metadata.getContentType());
//        operation.setMappingConfigId(metadata.getMappingConfigId());
//        operation.setEncoding(metadata.getEncoding());
//        operation.setDelimiter(metadata.getDelimiter());
//
//        // Создаем операцию через сервис статистики
//        return operationStatsService.createOperation(operation);
//    }
//
//    /**
//     * Выполняет основной процесс обработки файла
//     *
//     * @param metadata  метаданные обрабатываемого файла
//     * @param operation операция импорта
//     * @return объект с данными пакетной обработки
//     */
//    private BatchProcessingData processFile(FileMetadata metadata, ImportOperation operation) {
//        log.debug("Начало обработки файла: {}", metadata.getOriginalFilename());
//        LocalDateTime startTime = LocalDateTime.now();
//        BatchProcessingData batchData = null;
//
//        try {
//            // Обработка сырых данных из файла
//            batchData = processRawFile(metadata, operation);
//            log.debug("Базовая обработка файла завершена. Прочитано записей: {}", operation.getTotalRecords());
//
//            // Сохранение обработанных данных
//            progressTracker.trackProgress(operation, 30, "Сохранение данных");
//            persistData(metadata, operation, batchData);
//            log.debug("Сохранение данных завершено. Успешно обработано: {}", operation.getProcessedRecords());
//
//            return batchData;
//
//        } catch (Exception e) {
//            log.error("Ошибка при обработке файла: {}", e.getMessage(), e);
//            statisticsProcessor.handleOperationError(operation,
//                    "Ошибка при обработке файла: " + e.getMessage(),
//                    "FILE_PROCESSING_ERROR");
//            throw new FileProcessingException("Ошибка при обработке файла", e);
//        }
//    }
//
//
//    /**
//     * Выполняет базовую обработку файла и создание временных данных
//     *
//     * @param metadata  метаданные файла
//     * @param operation операция импорта
//     * @return объект с данными пакетной обработки
//     */
//    private BatchProcessingData processRawFile(FileMetadata metadata, ImportOperation operation) {
//        log.debug("Начало базовой обработки файла: {}", metadata.getOriginalFilename());
//
//        try {
//            // Получаем путь к файлу
//            Path filePath = pathResolver.getFilePath(metadata.getClientId(), metadata.getStoredFilename());
//            if (!Files.exists(filePath)) {
//                throw new FileProcessingException("Файл не найден: " + metadata.getOriginalFilename());
//            }
//
//            // Создаем объект для пакетной обработки
//            BatchProcessingData batchData = BatchProcessingData.builder().build();
//
//            // Получаем и настраиваем процессор
//            FileProcessor processor = processorFactory.getProcessor(metadata);
//            Map<String, Object> processorConfig = configureProcessor(metadata);
//            operation.getMetadata().put("processorConfig", processorConfig);
//            processor.configure(processorConfig);
//
//            // Обработка файла
//            Map<String, Object> results = processor.processFile(filePath, metadata, (progress, message) ->
//                    progressTracker.trackProgress(operation, progress, message));
//
//            // Обработка результатов
//            processResults(results, batchData, operation);
//
//            return batchData;
//
//        } catch (Exception e) {
//            String errorMsg = String.format("Ошибка при чтении файла %s: %s",
//                    metadata.getOriginalFilename(), e.getMessage());
//            statisticsProcessor.handleOperationError(operation, errorMsg, "FILE_READ_ERROR");
//            throw new FileProcessingException(errorMsg, e);
//        }
//    }
//
//    /**
//     * Настраивает процессор файлов
//     */
//    private Map<String, Object> configureProcessor(FileMetadata metadata) {
//        Map<String, Object> config = new HashMap<>();
//        config.put("encoding", metadata.getEncoding());
//        config.put("delimiter", metadata.getDelimiter());
//        config.put("batchSize", BATCH_SIZE_FILE_RECORD);
//        config.put("skipEmptyRows", true);
//        config.put("trimFields", true);
//        return config;
//    }
//
//
//    /**
//     * Обрабатывает результаты чтения файла
//     */
//    private void processResults(Map<String, Object> results, BatchProcessingData batchData,
//                                ImportOperation operation) {
//        // Обновляем статистику операции
//        Long totalCount = (Long) results.getOrDefault("totalCount", 0L);
//        operation.setTotalRecords(totalCount.intValue());
//        // Обрабатываем ошибки валидации
//        if (results.containsKey("validationErrors")) {
//            @SuppressWarnings("unchecked")
//            List<ValidationError> errors = (List<ValidationError>) results.get("validationErrors");
//            errors.forEach(error ->
//                    statisticsProcessor.handleOperationError(operation, error.getMessage(), error.getCode())
//            );
//        }
//
//        // Сохраняем данные для дальнейшей обработки
//        @SuppressWarnings("unchecked")
//        List<String> headers = (List<String>) results.get("headers");
//        batchData.setHeaders(headers);
//
//        @SuppressWarnings("unchecked")
//        List<Map<String, String>> records = (List<Map<String, String>>) results.get("records");
//        batchData.setProcessedData(records);
//
//        if (results.containsKey("tempFilePath")) {
//            batchData.setTempFilePath((Path) results.get("tempFilePath"));
//        }
//    }
//
//
//    /**
//     * Сохраняет обработанные данные в постоянное хранилище
//     *
//     * @param metadata  Метаданные импортируемого файла
//     * @param operation Текущая операция импорта
//     * @param batchData Данные для пакетной обработки
//     * @throws FileProcessingException при ошибках сохранения данных
//     */
//    private void persistData(FileMetadata metadata, ImportOperation operation, BatchProcessingData batchData) {
//        log.debug("Начало сохранения данных для файла: {}", metadata.getOriginalFilename());
//        LocalDateTime startSave = LocalDateTime.now();
//
//        try {
//            Map<String, String> columnsMapping = getMappingConfig(metadata.getMappingConfigId());
//            List<Map<String, String>> currentBatch = new ArrayList<>();
//            long totalProcessed = 0;
//
//            try (BufferedReader reader = Files.newBufferedReader(batchData.getTempFilePath())) {
//                String line;
//                while ((line = reader.readLine()) != null) {
//                    // Проверяем флаг отмены перед каждой записью
//                    if (operationStateManager.isCancelled(operation.getId())) {
//                        log.info("Обнаружена отмена операции {}, прерываем обработку", operation.getId());
//                        operationStatsService.updateOperationStatus(operation, OperationStatus.CANCELLED,
//                                null, null);
//                        return;
//                    }
//
//                    Map<String, String> record = objectMapper.readValue(line, new TypeReference<>() {
//                    });
//                    currentBatch.add(record);
//
//                    if (shouldProcessBatch(currentBatch, totalProcessed, operation.getTotalRecords())) {
//                        // Проверяем флаг отмены перед обработкой батча
//                        if (operationStateManager.isCancelled(operation.getId())) {
//                            log.info("Операция отменена перед обработкой батча");
//                            operationStatsService.updateOperationStatus(operation, OperationStatus.CANCELLED,
//                                    null, null);
//                            return;
//                        }
//
//                        processBatch(metadata, operation, currentBatch, columnsMapping, totalProcessed);
//                        totalProcessed += currentBatch.size();
//                        currentBatch.clear();
//
//                        if (totalProcessed < operation.getTotalRecords()) {
//                            updateIntermediateProgress(operation, totalProcessed);
//                        }
//                    }
//                }
//
//                // Обрабатываем оставшийся батч
//                if (!currentBatch.isEmpty() && !operationStateManager.isCancelled(operation.getId())) {
//                    processBatch(metadata, operation, currentBatch, columnsMapping, totalProcessed);
//                    totalProcessed += currentBatch.size();
//                    updateIntermediateProgress(operation, totalProcessed);
//
//                    // Проверяем флаг отмены перед установкой финального сообщения
//                    if (!operationStateManager.isCancelled(operation.getId())) {
//                        progressTracker.trackProgress(operation, 99,
//                                String.format("Обработка завершена: сохранено %d записей", totalProcessed));
//                    } else {
//                        operationStatsService.updateOperationStatus(operation, OperationStatus.CANCELLED,
//                                null, null);
//                    }
//                }
//            }
//
//            // Сохраняем метрики только если операция не была отменена
//            if (!operationStateManager.isCancelled(operation.getId())) {
//                Map<String, Object> saveMetrics = new HashMap<>();
//                saveMetrics.put("totalProcessed", totalProcessed);
//                saveMetrics.put("saveTimeSeconds", ChronoUnit.SECONDS.between(startSave, LocalDateTime.now()));
//                operation.getMetadata().put("saveMetrics", saveMetrics);
//
//                progressTracker.trackProgress(operation, 99, "Сохранение в БД завершено");
//            } else {
//                operationStatsService.updateOperationStatus(operation, OperationStatus.CANCELLED,
//                        null, null);
//            }
//
//        } catch (Exception e) {
//            String errorMsg = "Ошибка при сохранении данных: " + e.getMessage();
//            log.error(errorMsg, e);
//            statisticsProcessor.handleOperationError(operation, errorMsg, "DATA_SAVE_ERROR");
//            throw new FileProcessingException(errorMsg, e);
//        } finally {
//            // Очищаем состояние операции
//            operationStateManager.cleanup(operation.getId());
//        }
//    }
//
//    /**
//     * Обновляет промежуточный прогресс обработки
//     *
//     * @param operation      Текущая операция импорта
//     * @param totalProcessed Количество обработанных записей
//     */
//    private void updateIntermediateProgress(ImportOperation operation, long totalProcessed) {
//        double progressPercentage = (totalProcessed * 100.0) / operation.getTotalRecords();
//        int saveProgress = 30 + (int) ((progressPercentage * 69) / 100); // Максимум 99%
//
//        // Формируем сообщение в зависимости от прогресса
//        String statusMessage;
//        if (totalProcessed >= operation.getTotalRecords()) {
//            statusMessage = String.format("Обработка завершена: сохранено %d записей", totalProcessed);
//            saveProgress = 99; // Устанавливаем 99% для завершающего этапа
//            log.info("Устанавливаем финальное сообщение о сохранении: {}", statusMessage);
//        } else {
//            statusMessage = String.format("Сохраняем в БД: %d из %d записей",
//                    totalProcessed, operation.getTotalRecords());
//            log.debug("Устанавливаем промежуточное сообщение о сохранении: {}", statusMessage);
//        }
//
//        log.debug("Прогресс сохранения: {}%, totalProcessed: {}, total: {}",
//                saveProgress, totalProcessed, operation.getTotalRecords());
//        progressTracker.trackProgress(operation, saveProgress, statusMessage);
//    }
//
//    /**
//     * Получает конфигурацию маппинга полей
//     */
//    private Map<String, String> getMappingConfig(Long mappingConfigId) {
//        try {
//            ClientMappingConfig mapping = mappingConfigService.getMappingById(mappingConfigId);
//            return objectMapper.readValue(mapping.getColumnsConfig(), new TypeReference<>() {
//            });
//        } catch (Exception e) {
//            throw new FileProcessingException("Ошибка при получении конфигурации маппинга: " + e.getMessage(), e);
//        }
//    }
//
//
//    private boolean shouldProcessBatch(List<Map<String, String>> batch, long currentIndex, long totalSize) {
//        return batch.size() >= BATCH_SIZE_DATA_SAVE || currentIndex + batch.size() == totalSize;
//    }
//
//    /**
//     * Обрабатывает пакет данных для сохранения
//     *
//     * @param metadata       Метаданные файла
//     * @param operation      Текущая операция импорта
//     * @param batch          Пакет данных для обработки
//     * @param columnsMapping Маппинг колонок
//     * @param processedSoFar Количество обработанных записей
//     * @throws RuntimeException при ошибках обработки батча
//     */
//    private void processBatch(FileMetadata metadata, ImportOperation operation,
//                              List<Map<String, String>> batch, Map<String, String> columnsMapping,
//                              long processedSoFar) {
//        // Проверяем флаг отмены перед обработкой батча
//        if (operationStateManager.isCancelled(operation.getId())) {
//            log.info("Пропуск обработки батча - операция отменена");
//            operationStatsService.updateOperationStatus(operation, OperationStatus.CANCELLED,
//                    null, null);
//            return;
//        }
//
//        log.debug("Обработка батча {} записей (с {} по {}) из {}",
//                batch.size(), processedSoFar + 1,
//                processedSoFar + batch.size(), operation.getTotalRecords());
//
//        try {
//            // Сохраняем батч
//            Map<String, Object> results = dataPersistenceService.saveEntities(
//                    batch,
//                    metadata.getClientId(),
//                    columnsMapping,
//                    metadata.getId()
//            );
//
//            // Проверка отмены после сохранения
//            if (operationStateManager.isCancelled(operation.getId())) {
//                log.info("Операция была отменена во время сохранения батча");
//                operationStatsService.updateOperationStatus(operation, OperationStatus.CANCELLED,
//                        null, null);
//                return;
//            }
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
//    }
//
//    /**
//     * Добавляет метрики пакетной обработки
//     */
//    private void addBatchMetrics(ImportOperation operation, Map<String, Object> batchResults, long processedSoFar) {
//        Map<String, Object> batchMetrics = new HashMap<>();
//        batchMetrics.put("processedRecords", processedSoFar);
//        batchMetrics.put("successCount", batchResults.get("successCount"));
//        batchMetrics.put("errorCount", batchResults.get("errorCount"));
//        batchMetrics.put("timestamp", LocalDateTime.now().toString());
//
//        // Добавляем или обновляем список метрик батчей
//        @SuppressWarnings("unchecked")
//        List<Map<String, Object>> batchesMetrics = (List<Map<String, Object>>)
//                operation.getMetadata().computeIfAbsent("batchesMetrics", k -> new ArrayList<>());
//        batchesMetrics.add(batchMetrics);
//    }
//
//    /**
//     * Отменяет обработку файла.
//     * Обновляет статус файла на CANCELLED и добавляет сообщение об отмене.
//     *
//     * @param fileId идентификатор файла для отмены обработки
//     */
//
//    @Transactional
//    public void cancelProcessing(Long fileId) {
//        try {
//            ImportOperation operation = operationStatsService.findOperationByFileId(fileId)
//                    .map(op -> (ImportOperation) op)
//                    .orElseThrow(() -> new FileProcessingException("Операция не найдена"));
//
//            // Устанавливаем флаг отмены
//            operationStateManager.markAsCancelled(operation.getId());
//
//            // Обновляем статус в БД
//            progressTracker.trackProgress(operation, operation.getCurrentProgress(), "Обработка отменена");
//
//            log.info("Обработка файла {} успешно отменена", fileId);
//        } catch (Exception e) {
//            log.error("Ошибка при отмене обработки файла: {}", e.getMessage(), e);
//            throw e;
//        }
//    }

}