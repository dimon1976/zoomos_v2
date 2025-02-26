package by.zoomos_v2.service.file.input.service;

import by.zoomos_v2.exception.FileProcessingException;
import by.zoomos_v2.mapping.ClientMappingConfig;
import by.zoomos_v2.model.FileMetadata;
import by.zoomos_v2.model.FileType;
import by.zoomos_v2.model.enums.DataSourceType;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Оптимизированный сервис для асинхронной обработки файлов.
 * Реализует параллельную обработку данных и эффективное управление памятью.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileProcessingService {
    private static final int CHUNK_SIZE = 1000;
    private static final int PARALLEL_CHUNKS = Runtime.getRuntime().availableProcessors();
    private static final int MAX_CONCURRENT_BATCHES = PARALLEL_CHUNKS * 2;

    // Семафор для ограничения количества одновременных задач обработки
    private final Semaphore concurrentProcessingLimiter = new Semaphore(MAX_CONCURRENT_BATCHES);

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
    private final PlatformTransactionManager transactionManager;

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
            // Создаем транзакционный шаблон для выполнения операций с БД
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

            // Этап 1: Инициализация операции (в транзакции)
            FileMetadata metadata = transactionTemplate.execute(status -> getFileMetadata(fileId));
            operation = transactionTemplate.execute(status -> initializeOperation(metadata));
            log.debug("Операция инициализирована: {}", operation.getId());

            // Этап 2: Асинхронное чтение файла (не требует транзакций)
            fileReadingFuture = readFileAsync(metadata, operation);

            // Этап 3: Асинхронная обработка и сохранение данных
            BatchProcessingData batchData = fileReadingFuture.get(30, TimeUnit.MINUTES);
            if (!operationStateManager.isCancelled(operation.getId())) {
                dataPersistenceFuture = persistDataAsync(metadata, operation, batchData, transactionTemplate);
                dataPersistenceFuture.get(60, TimeUnit.MINUTES);
            }

            // Этап 4: Завершение операции (в транзакции)
            final ImportOperation finalOperation = operation;
            transactionTemplate.execute(status -> {
                finalizeOperation(finalOperation);
                return null;
            });

        } catch (Exception e) {
            log.error("Ошибка при обработке файла: {}", e.getMessage(), e);
            if (operation != null) {
                // Создаем новую транзакцию для обработки ошибки
                TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
                transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

                final ImportOperation errorOperation = operation;
                transactionTemplate.execute(status -> {
                    handleProcessingError(errorOperation, e);
                    return null;
                });
            }
        } finally {
            cleanup(operation, fileReadingFuture, dataPersistenceFuture);
        }
    }

    /**
     * Асинхронно читает данные из файла.
     * Не требует транзакции, так как работает только с файловой системой.
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
     * Асинхронно сохраняет обработанные данные в БД с использованием отдельных транзакций для каждого батча
     */
    private CompletableFuture<Boolean> persistDataAsync(FileMetadata metadata,
                                                        ImportOperation operation,
                                                        BatchProcessingData batchData,
                                                        TransactionTemplate transactionTemplate) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Начало асинхронного сохранения данных: {}", metadata.getOriginalFilename());
                progressTracker.trackProgress(operation, 41, "Подготовка к сохранению данных");

                // Получаем конфигурацию маппинга (в транзакции)
                Map<String, String> columnsMapping = transactionTemplate.execute(status ->
                        getMappingConfig(metadata.getMappingConfigId())
                );

                // Получаем ClientMappingConfig для определения типа данных (в транзакции)
                ClientMappingConfig mappingConfig = transactionTemplate.execute(status ->
                        mappingConfigService.getMappingById(metadata.getMappingConfigId())
                );
                DataSourceType dataSourceType = mappingConfig.getDataSource();

                // Создаем пул для параллельной обработки
                ExecutorService chunkExecutor = Executors.newFixedThreadPool(PARALLEL_CHUNKS);
                List<CompletableFuture<Void>> futures = new ArrayList<>();

                try {
                    // Обрабатываем файл партиями
                    final AtomicInteger processedCount = new AtomicInteger(0);
                    final List<String> errors = Collections.synchronizedList(new ArrayList<>());

                    batchData.processTempFileInBatches(CHUNK_SIZE, batch -> {
                        if (operationStateManager.isCancelled(operation.getId())) {
                            return;
                        }

                        try {
                            // Получаем разрешение от семафора перед запуском задачи
                            concurrentProcessingLimiter.acquire();

                            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                                try {
                                    // Создаем новую транзакцию для каждого батча
                                    transactionTemplate.execute(status -> {
                                        try {
                                            Map<String, Object> results = dataPersistenceService.saveEntities(
                                                    batch,
                                                    metadata.getClientId(),
                                                    columnsMapping,
                                                    metadata.getId(),
                                                    dataSourceType
                                            );

                                            synchronized (operation) {
                                                updateOperationProgress(operation, results, operation.getTotalRecords());
                                            }

                                            return results;
                                        } catch (Exception e) {
                                            log.error("Ошибка при сохранении батча: {}", e.getMessage());
                                            errors.add("Ошибка сохранения: " + e.getMessage());
                                            status.setRollbackOnly();
                                            throw e;
                                        }
                                    });

                                    int currentProcessed = processedCount.addAndGet(batch.size());
                                    updateProgressMessage(operation, currentProcessed);
                                } finally {
                                    // Освобождаем разрешение после выполнения задачи
                                    concurrentProcessingLimiter.release();
                                }
                            }, chunkExecutor);

                            futures.add(future);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            log.warn("Прерывание при ожидании разрешения семафора", e);
                        }
                    });

                    // Ожидаем завершения всех задач
                    CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                            futures.toArray(new CompletableFuture[0])
                    );

                    try {
                        allFutures.get(30, TimeUnit.MINUTES);
                    } catch (Exception e) {
                        log.error("Ошибка при ожидании завершения задач: {}", e.getMessage());
                        // Добавляем ошибки в операцию
                        errors.add("Ошибка при обработке: " + e.getMessage());
                    }

                    // Обновляем операцию с ошибками (если они есть)
                    if (!errors.isEmpty()) {
                        transactionTemplate.execute(status -> {
                            errors.forEach(error -> operation.addError(error, "DATA_SAVE_ERROR"));
                            operationStatsService.updateOperation(operation);
                            return null;
                        });
                    }

                    return true;
                } finally {
                    // Корректно завершаем пул потоков
                    chunkExecutor.shutdown();
                    if (!chunkExecutor.awaitTermination(5, TimeUnit.MINUTES)) {
                        log.warn("Принудительное завершение пула потоков после тайм-аута");
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
                                         int totalRecords) {
        int successCount = (Integer) results.getOrDefault("successCount", 0);
        operation.incrementProcessedRecords(successCount);

        // Обновляем прогресс
        int progress = 41 + (int) ((((double) operation.getProcessedRecords()) / totalRecords) * 58);
        String message = String.format("Обработано записей: %d из %d",
                operation.getProcessedRecords(), totalRecords);

        progressTracker.trackProgress(operation, Math.min(99, progress), message);

        if (results.containsKey("errors")) {
            @SuppressWarnings("unchecked")
            List<String> errors = (List<String>) results.get("errors");
            errors.forEach(error -> operation.addError(error, "DATA_SAVE_ERROR"));
        }
    }

    /**
     * Получает метаданные файла.
     * Требует транзакцию.
     */
    private FileMetadata getFileMetadata(Long fileId) {
        return fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new FileProcessingException("Файл не найден: " + fileId));
    }

    /**
     * Инициализирует операцию импорта.
     * Требует транзакцию.
     */
    private ImportOperation initializeOperation(FileMetadata metadata) {
        ImportOperation operation = createInitialOperation(metadata);
        operationStatsService.updateOperationStatus(operation, OperationStatus.IN_PROGRESS, null, null);
        return operation;
    }

    /**
     * Создает начальный объект операции импорта.
     */
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

    /**
     * Завершает операцию импорта.
     * Требует транзакцию.
     */
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

    /**
     * Обрабатывает ошибку в процессе импорта.
     * Требует транзакцию.
     */
    private void handleProcessingError(ImportOperation operation, Exception e) {
        if (operation != null && !operationStateManager.isCancelled(operation.getId())) {
            String errorType = e instanceof FileProcessingException ?
                    "PROCESSING_ERROR" : "SYSTEM_ERROR";
            statisticsProcessor.handleOperationError(operation, e.getMessage(), errorType);
        }
        log.error("Ошибка обработки файла: {}", e.getMessage(), e);
    }

    /**
     * Освобождает ресурсы после завершения обработки
     */
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
     * Настраивает процессор файлов с оптимальными параметрами.
     * Не требует транзакцию.
     */
    private FileProcessor setupFileProcessor(FileMetadata metadata) {
        if (metadata.getFileType() == FileType.CSV) {
            FileProcessor processor = processorFactory.getProcessor(metadata);
            processor.configure(createProcessorConfig(metadata));
            return processor;
        }
        return processorFactory.getProcessor(metadata);
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
     * Получает конфигурацию маппинга из базы данных.
     * Требует транзакцию.
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
     * Отменяет обработку файла с очисткой ресурсов.
     * Создает новую транзакцию.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelProcessing(Long fileId) {
        try {
            ImportOperation operation = operationStatsService.findOperationByFileId(fileId)
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
}