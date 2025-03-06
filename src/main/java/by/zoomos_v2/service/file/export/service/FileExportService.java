package by.zoomos_v2.service.file.export.service;

import by.zoomos_v2.annotations.FieldDescription;
import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.model.ExportResult;
import by.zoomos_v2.model.FileMetadata;
import by.zoomos_v2.model.entity.CompetitorData;
import by.zoomos_v2.model.entity.Product;
import by.zoomos_v2.model.entity.RegionData;
import by.zoomos_v2.model.enums.OperationStatus;
import by.zoomos_v2.model.enums.OperationType;
import by.zoomos_v2.model.operation.BaseOperation;
import by.zoomos_v2.model.operation.ExportOperation;
import by.zoomos_v2.repository.FileMetadataRepository;
import by.zoomos_v2.repository.ProductRepository;
import by.zoomos_v2.service.file.BatchProcessingData;
import by.zoomos_v2.service.file.export.exporter.DataExporter;
import by.zoomos_v2.service.file.export.exporter.DataExporterFactory;
import by.zoomos_v2.service.file.export.strategy.DataProcessingStrategy;
import by.zoomos_v2.service.file.export.strategy.StrategyManager;
import by.zoomos_v2.service.statistics.OperationStatsService;
import by.zoomos_v2.service.statistics.StatisticsProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.lang.reflect.Field;
import java.rmi.server.ExportException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Сервис для экспорта данных из файлов с оптимизированной обработкой
 * и улучшенным управлением памятью
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileExportService {
    private static final int BATCH_SIZE = 1000;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final FileMetadataRepository fileMetadataRepository;
    private final ProductRepository productRepository;
    private final DataExporterFactory exporterFactory;
    private final OperationStatsService operationStatsService;
    private final StatisticsProcessor statisticsProcessor;
    private final StrategyManager strategyManager;

    /**
     * Экспортирует данные из одного файла
     *
     * @param fileId       ID файла
     * @param exportConfig конфигурация экспорта
     * @param fileType     тип экспорта (CSV, XLSX)
     * @return результат экспорта
     */

    public ExportResult exportFileData(Long fileId, ExportConfig exportConfig, String fileType) {
        log.info("Начало экспорта одиночного файла. FileId: {}, стратегия: {}", fileId, exportConfig.getStrategyType());
        return exportFilesData(List.of(fileId), exportConfig, fileType);
    }

    /**
     * Экспортирует данные из нескольких файлов с оптимизированной обработкой
     *
     * @param fileIds      Список ID файлов для экспорта
     * @param exportConfig конфигурация экспорта
     * @param fileType     тип экспорта (CSV, XLSX)
     * @return результат экспорта
     */

    public ExportResult exportFilesData(List<Long> fileIds, ExportConfig exportConfig, String fileType) {
        log.info("Начало экспорта нескольких файлов. FileIds: {}, стратегия: {}", fileIds, exportConfig.getStrategyType());
        ExportOperation operation = null;

        try {
            // Проверка на пустой список файлов
            if (fileIds == null || fileIds.isEmpty()) {
                throw new IllegalArgumentException("Список файлов для экспорта пуст");
            }

            // Получаем метаданные первого файла для инициализации операции
            FileMetadata firstMetadata = getFileMetadata(fileIds.get(0));

            // Создаем операцию экспорта
            operation = initializeExportOperation(fileIds, firstMetadata, fileType, exportConfig);

            // Сохраняем операцию в БД
            operation = operationStatsService.createOperation(operation);
            log.info("Создана операция экспорта ID: {}, исходные файлы: {}", operation.getId(), fileIds);

            // Получаем стратегию и проверяем параметры
            DataProcessingStrategy strategy = strategyManager.getStrategy(exportConfig.getStrategyType());
            strategyManager.validateStrategyParameters(exportConfig.getStrategyType(), exportConfig.getParams());

            // Обрабатываем и объединяем данные из всех файлов
            List<Map<String, Object>> allData = processFilesData(fileIds, operation);

            // Применяем стратегию обработки к данным
            updateOperationStatus(operation, "Применение стратегии обработки данных");
            BatchProcessingData batchData = BatchProcessingData.createNew();
            List<Map<String, Object>> processedData = strategy.processData(allData, exportConfig, batchData);

            // Обновляем метрики операции
            updateOperationMetrics(operation, allData.size(), processedData.size(), batchData);

            // Экспортируем обработанные данные
            updateOperationStatus(operation, "Формирование файла экспорта");
            ExportResult result = createExportResult(processedData, firstMetadata, getExporter(fileType),
                    exportConfig, fileType);

            // Формируем имя выходного файла
            result.setFileName(generateExportFileName(fileIds, fileType));
            result.setBatchProcessingData(batchData);

            // Завершаем операцию экспорта
            completeExportOperation(operation, result);

            return result;

        } catch (Exception e) {
            // Обрабатываем ошибку
            handleExportError(operation, e);
            return ExportResult.error(e.getMessage());
        }
    }

    /**
     * Создает операцию экспорта в отдельной транзакции
     */
    @Transactional
    public ExportOperation createExportOperation(List<Long> fileIds, FileMetadata firstMetadata,
                                                 String fileType, ExportConfig exportConfig) {
        ExportOperation operation = new ExportOperation();
        operation.setClientId(firstMetadata.getClientId());
        operation.setType(OperationType.EXPORT);
        operation.setExportFormat(fileType);

        // Формируем идентификатор источника
        String sourceIdentifier = fileIds.size() == 1 ?
                firstMetadata.getStoredFilename() :
                fileIds.stream().map(String::valueOf).collect(Collectors.joining(", "));

        operation.setSourceIdentifier(sourceIdentifier);
        operation.setProcessingStrategy(exportConfig.getStrategyType().name());
        operation.setExportConfig(convertExportConfigToMap(exportConfig));

        // Инициализируем все необходимые поля
        initializeBaseOperationFields(operation);

        // Добавляем метаданные
        Map<String, Object> exportMetadata = new HashMap<>();
        exportMetadata.put("fileCount", fileIds.size());
        exportMetadata.put("fileIds", fileIds);
        exportMetadata.put("strategyType", exportConfig.getStrategyType());
        exportMetadata.put("formatType", fileType);
        exportMetadata.put("startTime", LocalDateTime.now());

        operation.getMetadata().put("exportInfo", exportMetadata);

        // Создаем операцию через сервис статистики
        ExportOperation savedOperation = operationStatsService.createOperation(operation);
        log.info("Создана операция экспорта ID: {}, исходные файлы: {}", savedOperation.getId(), fileIds);
        return savedOperation;
    }



    /**
     * Обновляет операцию в отдельной транзакции
     */
    @Transactional
    public void updateOperationInTransaction(ExportOperation operation) {
        statisticsProcessor.updateOperationStats(operation);
    }

    /**
     * Обрабатывает данные из нескольких файлов с учетом прогресса
     *
     * @param fileIds список идентификаторов файлов
     * @param operation операция экспорта
     * @return объединенные данные из всех файлов
     * @throws ExportException при ошибке обработки данных
     */
    private List<Map<String, Object>> processFilesData(List<Long> fileIds, ExportOperation operation) throws ExportException {
        List<Map<String, Object>> allData = new ArrayList<>();
        int totalRecords = 0;

        // Добавляем информацию о общем количестве файлов в метаданные
        Map<String, Object> progressInfo = new HashMap<>();
        progressInfo.put("totalFiles", fileIds.size());
        operation.getMetadata().put("progressInfo", progressInfo);
        statisticsProcessor.updateOperationStats(operation);

        AtomicInteger processedFiles = new AtomicInteger(0);

        for (Long fileId : fileIds) {
            try {
                FileMetadata metadata = getFileMetadata(fileId);
                int currentFile = processedFiles.incrementAndGet();

                // Обновляем прогресс операции
                int progressPercentage = (int) ((currentFile * 100.0) / fileIds.size());
                String progressMessage = String.format("Обработка файла %d из %d - %s",
                        currentFile, fileIds.size(), metadata.getOriginalFilename());

                updateOperationProgress(operation, currentFile, fileIds.size(), metadata.getOriginalFilename(), progressMessage);

                // Получаем данные из текущего файла
                List<Map<String, Object>> fileData = getDataFromFile(metadata);
                allData.addAll(fileData);
                totalRecords += fileData.size();

                // Обновляем статистику операции
                operation.setTotalRecords(totalRecords);
                operation.setProcessedRecords(currentFile); // Количество обработанных файлов
                statisticsProcessor.updateOperationStats(operation);

                log.debug("Обработан файл {}/{}: {}, получено {} записей",
                        currentFile, fileIds.size(), metadata.getOriginalFilename(), fileData.size());
            } catch (Exception e) {
                log.error("Ошибка при обработке файла с ID {}: {}", fileId, e.getMessage(), e);
                // Продолжаем обработку других файлов
            }
        }

        log.info("Завершена обработка {} файлов, получено {} записей", processedFiles.get(), totalRecords);
        return allData;
    }


    /**
     * Обновляет статус операции
     *
     * @param operation операция экспорта
     * @param status    текстовый статус для отображения
     */
    private void updateOperationStatus(ExportOperation operation, String status) {
        if (operation != null) {
            // Безопасное получение или создание Map для progressInfo
            Map<String, Object> metadata = operation.getMetadata();
            if (!metadata.containsKey("progressInfo")) {
                metadata.put("progressInfo", new HashMap<String, Object>());
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> progressInfo = (Map<String, Object>) metadata.get("progressInfo");
            progressInfo.put("processingStatus", status);

            statisticsProcessor.updateOperationStats(operation);
            log.debug("Обновлен статус операции {}: {}", operation.getId(), status);
        }
    }

    /**
     * Обновляет прогресс операции
     */
    private void updateOperationProgress(ExportOperation operation, int currentFile, int totalFiles,
                                         String currentFileName, String progressMessage) {
        int progressPercentage = (int) ((currentFile * 100.0) / totalFiles);

        // Безопасное получение или создание Map для progressInfo
        Map<String, Object> metadata = operation.getMetadata();
        if (!metadata.containsKey("progressInfo")) {
            metadata.put("progressInfo", new HashMap<String, Object>());
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> progressInfo = (Map<String, Object>) metadata.get("progressInfo");

        progressInfo.put("currentFile", currentFileName);
        progressInfo.put("processedFiles", currentFile);
        progressInfo.put("totalFiles", totalFiles);
        progressInfo.put("message", progressMessage);
        progressInfo.put("progress", progressPercentage);

        operation.setCurrentProgress(progressPercentage);
        statisticsProcessor.handleProgress(operation, progressPercentage, progressMessage);
    }

    /**
     * Обновляет метрики операции после обработки данных
     */
    @Transactional
    public void updateOperationMetrics(ExportOperation operation, int totalInputRecords,
                                        int processedRecords, BatchProcessingData batchData) {
        if (operation != null) {
            // Устанавливаем количество обработанных записей
            if (batchData != null && batchData.getSuccessCount() > 0) {
                operation.setProcessedRecords((int) batchData.getSuccessCount());
            } else {
                operation.setProcessedRecords(processedRecords);
            }

            // Устанавливаем общее количество записей
            operation.setTotalRecords(Math.max(totalInputRecords, processedRecords));

            // Устанавливаем количество ошибок, если есть
            if (batchData != null) {
                long failedRecords = totalInputRecords - batchData.getSuccessCount();
                if (failedRecords >= 0) {
                    operation.setFailedRecords((int) failedRecords);
                }
            }

            statisticsProcessor.updateOperationStats(operation);
            log.debug("Обновлены метрики операции {}: обработано {}/{} записей, ошибок: {}",
                    operation.getId(), operation.getProcessedRecords(),
                    operation.getTotalRecords(), operation.getFailedRecords());
        }
    }

    /**
     * Завершает операцию экспорта с успешным статусом
     */
    private void completeExportOperation(ExportOperation operation, ExportResult result) {
        if (operation != null) {
            operation.setFilesGenerated(1);
            operation.setTargetPath(result.getFileName());

            // Добавляем финальную статистику в метаданные
            operation.getMetadata().put("final_status", "success");
            operation.getMetadata().put("processed_records", operation.getProcessedRecords());
            operation.getMetadata().put("total_records", operation.getTotalRecords());
            operation.getMetadata().put("export_file_size", result.getFileContent() != null ?
                    result.getFileContent().length : 0);

            // Помечаем операцию как успешно завершенную
            operationStatsService.updateOperationStatus(
                    operation,
                    OperationStatus.COMPLETED,
                    null,
                    null
            );

            log.info("Операция экспорта {} успешно завершена, создан файл {}",
                    operation.getId(), result.getFileName());
        }
    }

    /**
     * Инициализирует операцию экспорта
     */
    private ExportOperation initializeExportOperation(List<Long> fileIds, FileMetadata firstMetadata,
                                                      String fileType, ExportConfig exportConfig) {
        ExportOperation operation = new ExportOperation();
        operation.setClientId(firstMetadata.getClientId());
        operation.setType(OperationType.EXPORT);
        operation.setExportFormat(fileType);

        // Формируем идентификатор источника
        String sourceIdentifier = fileIds.size() == 1 ?
                firstMetadata.getStoredFilename() :
                fileIds.stream().map(String::valueOf).collect(Collectors.joining(", "));

        operation.setSourceIdentifier(sourceIdentifier);
        operation.setProcessingStrategy(exportConfig.getStrategyType().name());
        operation.setExportConfig(convertExportConfigToMap(exportConfig));

        // Инициализируем все необходимые поля
        initializeBaseOperationFields(operation);

        // Добавляем метаданные
        Map<String, Object> exportMetadata = new HashMap<>();
        exportMetadata.put("fileCount", fileIds.size());
        exportMetadata.put("fileIds", fileIds);
        exportMetadata.put("strategyType", exportConfig.getStrategyType());
        exportMetadata.put("formatType", fileType);
        exportMetadata.put("startTime", LocalDateTime.now());

        operation.getMetadata().put("exportInfo", exportMetadata);

        return operation;
    }

    /**
     * Инициализирует базовые поля операции для избежания NullPointerException
     *
     * @param operation операция для инициализации
     */
    private void initializeBaseOperationFields(BaseOperation operation) {
        // Инициализация базовых полей для предотвращения NullPointerException
        if (operation.getProcessedRecords() == null) {
            operation.setProcessedRecords(0);
        }

        if (operation.getTotalRecords() == null) {
            operation.setTotalRecords(0);
        }

        if (operation.getFailedRecords() == null) {
            operation.setFailedRecords(0);
        }

        if (operation.getCurrentProgress() == null) {
            operation.setCurrentProgress(0);
        }

        if (operation.getMetadata() == null) {
            operation.setMetadata(new HashMap<>());
        }

        // Для ExportOperation
        if (operation instanceof ExportOperation) {
            ExportOperation exportOp = (ExportOperation) operation;

            if (exportOp.getFilesGenerated() == null) {
                exportOp.setFilesGenerated(0);
            }
        }
    }

    /**
     * Обработка ошибки экспорта с логированием
     */
    private void handleExportError(ExportOperation operation, Exception e) {
        log.error("Ошибка экспорта данных: {}", e.getMessage(), e);

        if (operation != null) {
            // Сохраняем сведения об ошибке в метаданные
            Map<String, Object> errorInfo = new HashMap<>();
            errorInfo.put("errorMessage", e.getMessage());
            errorInfo.put("errorType", e.getClass().getSimpleName());
            errorInfo.put("errorTime", LocalDateTime.now());

            operation.getMetadata().put("error", errorInfo);

            // Обновляем статус операции
            operationStatsService.updateOperationStatus(
                    operation,
                    OperationStatus.FAILED,
                    e.getMessage(),
                    e.getClass().getSimpleName()
            );
        }
    }

    /**
     * Конвертирует конфигурацию экспорта в Map для сохранения в БД
     */
    private Map<String, Object> convertExportConfigToMap(ExportConfig config) {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("name", config.getName());
        configMap.put("description", config.getDescription());
        configMap.put("strategyType", config.getStrategyType());
        configMap.put("params", config.getParams());
        configMap.put("fieldsCount", config.getFields() != null ? config.getFields().size() : 0);
        return configMap;
    }

    /**
     * Получает метаданные файла по ID
     */
    private FileMetadata getFileMetadata(Long fileId) throws FileNotFoundException {
        return fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> {
                    log.error("Файл не найден: {}", fileId);
                    return new FileNotFoundException("File not found: " + fileId);
                });
    }

    /**
     * Получает экспортер для указанного типа файла
     */
    private DataExporter getExporter(String fileType) throws ExportException {
        try {
            return exporterFactory.getExporter(fileType);
        } catch (Exception e) {
            log.error("Ошибка получения экспортера для типа {}: {}", fileType, e.getMessage());
            throw new ExportException("Invalid exporter type: " + fileType);
        }
    }

    /**
     * Создает результат экспорта с данными файла
     */
    private ExportResult createExportResult(List<Map<String, Object>> data,
                                            FileMetadata metadata,
                                            DataExporter exporter,
                                            ExportConfig exportConfig,
                                            String fileType) throws ExportException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            BatchProcessingData stats = BatchProcessingData.createNew();
            ExportResult result = exporter.export(data, outputStream, exportConfig, stats);

            // Устанавливаем имя файла, если не задано
            if (result.getFileName() == null || result.getFileName().isEmpty()) {
                result.setFileName(generateFileName(metadata, fileType));
            }

            result.setFileContent(outputStream.toByteArray());
            log.info("Создан результат экспорта, размер файла: {} байт", outputStream.size());
            return result;
        } catch (Exception e) {
            log.error("Ошибка создания результата экспорта: {}", e.getMessage(), e);
            throw new ExportException("Export result creation failed", e);
        }
    }

    /**
     * Оптимизированное получение данных из файла с батчевой обработкой
     */
    private List<Map<String, Object>> getDataFromFile(FileMetadata metadata) throws ExportException {
        log.debug("Получение данных из файла: {}", metadata.getId());
        List<Map<String, Object>> result = new ArrayList<>();

        try {
            List<Product> products = productRepository.findByFileId(metadata.getId());
            processBatchedProducts(products, result);
            log.info("Получено {} записей из файла {}", result.size(), metadata.getId());
            return result;
        } catch (Exception e) {
            log.error("Ошибка получения данных из файла: {}", e.getMessage(), e);
            throw new ExportException("Failed to extract data", e);
        }
    }

    /**
     * Обработка продуктов батчами для оптимизации памяти
     */
    private void processBatchedProducts(List<Product> products, List<Map<String, Object>> result) {
        int size = products.size();
        for (int i = 0; i < size; i += BATCH_SIZE) {
            int end = Math.min(size, i + BATCH_SIZE);
            List<Product> batch = products.subList(i, end);
            List<Long> batchIds = batch.stream().map(Product::getId).toList();

            // Оптимизация: получаем связанные данные одним запросом для каждого батча
            Map<Long, Product> regionsMap = createRegionsMap(batchIds);
            Map<Long, Product> competitorsMap = createCompetitorsMap(batchIds);

            processBatch(batch, regionsMap, competitorsMap, result);

            if (i % (BATCH_SIZE * 5) == 0 && i > 0) {
                log.debug("Обработано {} записей из {}", i, size);
            }
        }
    }

    /**
     * Создает карту продуктов с регионами
     */
    private Map<Long, Product> createRegionsMap(List<Long> productIds) {
        return productRepository.findByIdInWithRegionData(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (p1, p2) -> p1));
    }

    /**
     * Создает карту продуктов с данными конкурентов
     */
    private Map<Long, Product> createCompetitorsMap(List<Long> productIds) {
        return productRepository.findByIdInWithCompetitorData(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (p1, p2) -> p1));
    }

    /**
     * Обработка батча продуктов с применением оптимизаций памяти
     */
    private void processBatch(List<Product> products,
                              Map<Long, Product> regionsMap,
                              Map<Long, Product> competitorsMap,
                              List<Map<String, Object>> result) {
        products.forEach(product -> {
            try {
                Map<String, Object> productData = extractFieldsWithDescriptions(product, "product");

                // Оптимизация: используем уже загруженные связанные данные
                Product withRegions = regionsMap.get(product.getId());
                Product withCompetitors = competitorsMap.get(product.getId());

                processProductRelations(withRegions, withCompetitors, productData, result);
            } catch (Exception e) {
                log.warn("Ошибка обработки продукта {}: {}", product.getId(), e.getMessage());
            }
        });
    }

    /**
     * Обработка связей продукта с регионами и конкурентами
     */
    private void processProductRelations(Product withRegions,
                                         Product withCompetitors,
                                         Map<String, Object> productData,
                                         List<Map<String, Object>> result) {
        if (withRegions != null && withRegions.getRegionDataList() != null && !withRegions.getRegionDataList().isEmpty()) {
            processRegionsWithCompetitors(withRegions, withCompetitors, productData, result);
        } else if (withCompetitors != null && withCompetitors.getCompetitorDataList() != null &&
                !withCompetitors.getCompetitorDataList().isEmpty()) {
            processCompetitorsOnly(withCompetitors, productData, result);
        } else {
            result.add(productData);
        }
    }

    /**
     * Обработка связей продукта с регионами и конкурентами
     */
    private void processRegionsWithCompetitors(Product withRegions,
                                               Product withCompetitors,
                                               Map<String, Object> productData,
                                               List<Map<String, Object>> result) {
        for (RegionData regionData : withRegions.getRegionDataList()) {
            Map<String, Object> rowData = new HashMap<>(productData);
            rowData.putAll(extractFieldsWithDescriptions(regionData, "regiondata"));

            if (withCompetitors != null && withCompetitors.getCompetitorDataList() != null &&
                    !withCompetitors.getCompetitorDataList().isEmpty()) {
                for (CompetitorData competitorData : withCompetitors.getCompetitorDataList()) {
                    Map<String, Object> fullRowData = new HashMap<>(rowData);
                    fullRowData.putAll(extractFieldsWithDescriptions(competitorData, "competitordata"));
                    result.add(fullRowData);
                }
            } else {
                result.add(rowData);
            }
        }
    }

    /**
     * Обработка только связей с конкурентами
     */
    private void processCompetitorsOnly(Product withCompetitors,
                                        Map<String, Object> productData,
                                        List<Map<String, Object>> result) {
        for (CompetitorData competitorData : withCompetitors.getCompetitorDataList()) {
            Map<String, Object> fullRowData = new HashMap<>(productData);
            fullRowData.putAll(extractFieldsWithDescriptions(competitorData, "competitordata"));
            result.add(fullRowData);
        }
    }

    /**
     * Извлекает значения полей с оптимизированной обработкой ошибок
     */
    private Map<String, Object> extractFieldsWithDescriptions(Object object, String prefix) {
        Map<String, Object> fields = new HashMap<>();
        if (object == null) {
            return fields;
        }

        try {
            for (Field field : object.getClass().getDeclaredFields()) {
                FieldDescription description = field.getAnnotation(FieldDescription.class);
                if (description != null && !description.skipMapping()) {
                    try {
                        field.setAccessible(true);
                        Object value = field.get(object);
                        if (value != null) {
                            fields.put(prefix + "." + field.getName(), value);
                        }
                    } catch (Exception e) {
                        log.trace("Ошибка доступа к полю {}: {}", field.getName(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Ошибка извлечения полей для {}: {}",
                    object != null ? object.getClass().getSimpleName() : "null", e.getMessage());
        }
        return fields;
    }

    /**
     * Генерирует имя файла для экспорта из одного источника
     */
    private String generateFileName(FileMetadata originalFile, String exportType) {
        String baseName = originalFile.getOriginalFilename().replaceFirst("[.][^.]+$", "");
        return String.format("%s_export.%s", baseName, exportType.toLowerCase());
    }

    /**
     * Генерирует имя файла для экспорта из нескольких источников
     */
    private String generateExportFileName(List<Long> fileIds, String fileType) {
        // Если один файл, используем его имя
        if (fileIds.size() == 1) {
            try {
                FileMetadata metadata = getFileMetadata(fileIds.get(0));
                return generateFileName(metadata, fileType);
            } catch (Exception e) {
                log.warn("Не удалось получить метаданные файла для генерации имени: {}", e.getMessage());
                // Продолжаем с общим шаблоном
            }
        }

        // Формируем базовое имя файла с учетом количества источников
        String baseName = "export_" + fileIds.size() + "_files";

        // Добавляем текущую дату и время для уникальности
        String timestamp = LocalDateTime.now().format(DATE_FORMATTER);

        return String.format("%s_%s.%s", baseName, timestamp, fileType.toLowerCase());
    }
}