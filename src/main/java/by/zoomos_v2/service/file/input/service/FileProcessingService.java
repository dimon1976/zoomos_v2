package by.zoomos_v2.service.file.input.service;

import by.zoomos_v2.exception.FileProcessingException;
import by.zoomos_v2.mapping.ClientMappingConfig;
import by.zoomos_v2.model.FileMetadata;
import by.zoomos_v2.model.FileType;
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
import java.time.temporal.ChronoUnit;
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
        if (metadata.getFileType()== FileType.CSV){
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
}