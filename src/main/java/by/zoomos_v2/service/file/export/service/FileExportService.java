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
import java.util.stream.Collectors;

/**
 * Сервис для экспорта данных из файлов
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileExportService {
    private static final int BATCH_SIZE = 1000;

    private final FileMetadataRepository fileMetadataRepository;
    private final ProductRepository productRepository;
    private final DataExporterFactory exporterFactory;
    private final OperationStatsService operationStatsService;
    private final StatisticsProcessor statisticsProcessor;
    private final StrategyManager strategyManager;

    /**
     * Экспортирует данные из файла с оптимизированной обработкой
     *
     * @param fileId       ID файла
     * @param exportConfig конфигурация экспорта
     * @param fileType     тип экспорта (CSV, XLSX)
     * @return результат экспорта
     */
    @Transactional()
    public ExportResult exportFileData(Long fileId, ExportConfig exportConfig, String fileType) {
        log.info("Начало экспорта. FileId: {}, стратегия: {}", fileId, exportConfig.getStrategyType());
        ExportOperation operation = null;

        try {
            FileMetadata metadata = getFileMetadata(fileId);
            operation = initializeExportOperation(metadata, fileType, exportConfig);

            // Получаем стратегию и проверяем параметры
            DataProcessingStrategy strategy = strategyManager.getStrategy(exportConfig.getStrategyType());
            strategyManager.validateStrategyParameters(exportConfig.getStrategyType(), exportConfig.getParams());

            // Обрабатываем данные
            List<Map<String, Object>> processedData = processDataWithStrategy(metadata, strategy, exportConfig, operation);

            // Экспортируем обработанные данные
            ExportResult result = createExportResult(processedData, metadata, getExporter(fileType),
                    exportConfig, fileType);

            // Обновляем статистику
            if (operation != null) {
                operation.setFilesGenerated(1);
                operation.setTargetPath(result.getFileName());
                statisticsProcessor.updateOperationStats(operation);
            }

            return result;

        } catch (Exception e) {
            handleExportError(operation, e);
            return ExportResult.error(e.getMessage());
        }
    }

    /**
     * Экспортирует данные из нескольких файлов с оптимизированной обработкой
     *
     * @param fileIds      Список ID файлов для экспорта
     * @param exportConfig конфигурация экспорта
     * @param fileType     тип экспорта (CSV, XLSX)
     * @return результат экспорта
     */
    @Transactional()
    public ExportResult exportFilesData(List<Long> fileIds, ExportConfig exportConfig, String fileType) {
        log.info("Начало экспорта из нескольких файлов. FileIds: {}, стратегия: {}", fileIds, exportConfig.getStrategyType());
        ExportOperation operation = null;

        try {
            // Проверка на пустой список файлов
            if (fileIds == null || fileIds.isEmpty()) {
                throw new IllegalArgumentException("Список файлов для экспорта пуст");
            }

            // Получаем метаданные первого файла для инициализации операции
            FileMetadata firstMetadata = getFileMetadata(fileIds.get(0));

            // Создаем единую операцию экспорта для всех файлов
            operation = initializeMultiFileOperation(fileIds, firstMetadata, fileType, exportConfig);

            // Получаем стратегию и проверяем параметры
            DataProcessingStrategy strategy = strategyManager.getStrategy(exportConfig.getStrategyType());
            strategyManager.validateStrategyParameters(exportConfig.getStrategyType(), exportConfig.getParams());

            // Обрабатываем данные в оптимизированном режиме - объединяем все данные из файлов
            List<Map<String, Object>> allData = new ArrayList<>();
            int totalRecords = 0;
            int processedFiles = 0;

            // Добавляем информацию о общем количестве файлов в метаданные
            Map<String, Object> progressInfo = new HashMap<>();
            progressInfo.put("totalFiles", fileIds.size());
            operation.getMetadata().put("progressInfo", progressInfo);
            statisticsProcessor.updateOperationStats(operation);

            for (Long fileId : fileIds) {
                FileMetadata metadata = getFileMetadata(fileId);
                processedFiles++;

                // Обновляем метрики прогресса
                int progressPercentage = (int) ((processedFiles * 100.0) / fileIds.size());
                String progressMessage = String.format("Обработка файла %d из %d - %s",
                        processedFiles, fileIds.size(), metadata.getOriginalFilename());

                // Обновляем прогресс и метаданные
                operation.setCurrentProgress(progressPercentage);
                progressInfo.put("currentFile", metadata.getOriginalFilename());
                progressInfo.put("processedFiles", processedFiles);
                progressInfo.put("message", progressMessage);

                // Обновляем прогресс операции
                statisticsProcessor.handleProgress(operation, progressPercentage, progressMessage);

                // Получаем данные из текущего файла
                List<Map<String, Object>> fileData = getDataFromFile(metadata);
                allData.addAll(fileData);
                totalRecords += fileData.size();

                // Обновляем статистику операции
                operation.setTotalRecords(totalRecords);
                operation.setProcessedRecords(processedFiles); // Обновляем количество обработанных файлов
                statisticsProcessor.updateOperationStats(operation);
            }

            // Обрабатываем все собранные данные с помощью стратегии
            progressInfo.put("processingStatus", "Применение стратегии обработки данных");
            statisticsProcessor.updateOperationStats(operation);

            BatchProcessingData batchData = BatchProcessingData.createNew();
            List<Map<String, Object>> processedData = strategy.processData(allData, exportConfig, batchData);

            // Устанавливаем количество обработанных записей
            if (batchData != null && batchData.getSuccessCount() > 0) {
                operation.setProcessedRecords((int) batchData.getSuccessCount());
            } else {
                operation.setProcessedRecords(processedData.size());
            }

            // Устанавливаем общее количество записей
            operation.setTotalRecords(Math.max(totalRecords, processedData.size()));

            // Экспортируем обработанные данные
            progressInfo.put("processingStatus", "Формирование файла экспорта");
            statisticsProcessor.updateOperationStats(operation);

            ExportResult result = createExportResult(processedData, firstMetadata, getExporter(fileType),
                    exportConfig, fileType);

            // Формируем имя выходного файла с учетом нескольких источников
            result.setFileName(generateMultiFileExportName(fileIds, fileType));

            // Обновляем статистику завершенной операции
            if (operation != null) {
                operation.setFilesGenerated(1);
                operation.setTargetPath(result.getFileName());

                // Устанавливаем final_status в метаданные для анализа
                operation.getMetadata().put("final_status", "success");
                operation.getMetadata().put("processed_records", operation.getProcessedRecords());
                operation.getMetadata().put("total_records", operation.getTotalRecords());

                // Помечаем операцию как успешно завершенную
                operationStatsService.updateOperationStatus(
                        operation,
                        OperationStatus.COMPLETED,
                        null,
                        null
                );
            }

            // Добавляем статистику обработки в результат
            result.setBatchProcessingData(batchData);

            return result;

        } catch (Exception e) {
            handleExportError(operation, e);
            return ExportResult.error(e.getMessage());
        }
    }

    /**
     * Инициализирует базовые поля операции, чтобы избежать NullPointerException
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
     * Обновляет прогресс операции при многофайловом экспорте
     */
    private void updateOperationProgress(ExportOperation operation, int currentFileIndex, int totalFiles) {
        int progressPercentage = (int) ((currentFileIndex * 100.0) / totalFiles);
        String message = String.format("Обработка файла %d из %d", currentFileIndex + 1, totalFiles);

        // Добавляем информацию о прогрессе в метаданные
        Map<String, Object> progressInfo = new HashMap<>();
        progressInfo.put("currentFile", currentFileIndex + 1);
        progressInfo.put("totalFiles", totalFiles);
        progressInfo.put("message", message);
        progressInfo.put("progress", progressPercentage);

        operation.getMetadata().put("progressInfo", progressInfo);
        operation.setCurrentProgress(progressPercentage);

        // Обновляем операцию
        statisticsProcessor.handleProgress(operation, progressPercentage, message);
    }

    /**
     * Генерирует имя файла для экспорта из нескольких источников
     */
    private String generateMultiFileExportName(List<Long> fileIds, String fileType) {
        // Формируем базовое имя файла с учетом количества источников
        String baseName = "multi_export_" + fileIds.size() + "_files";

        // Добавляем текущую дату и время для уникальности
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        return String.format("%s_%s.%s", baseName, timestamp, fileType.toLowerCase());
    }

    /**
     * Инициализирует операцию экспорта для нескольких файлов
     */
    private ExportOperation initializeMultiFileOperation(List<Long> fileIds,
                                                         FileMetadata firstMetadata,
                                                         String fileType,
                                                         ExportConfig exportConfig) {
        ExportOperation operation = new ExportOperation();
        operation.setClientId(firstMetadata.getClientId());
        operation.setType(OperationType.EXPORT);
        operation.setExportFormat(fileType);

        // Формируем идентификатор источника из списка ID файлов
        String sourceIdentifier = fileIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(", "));
        operation.setSourceIdentifier(sourceIdentifier);

        operation.setProcessingStrategy(exportConfig.getStrategyType().name());
        operation.setExportConfig(convertExportConfigToMap(exportConfig));

        // Инициализируем все необходимые поля
        initializeBaseOperationFields(operation);

        // Добавляем метаданные о многофайловом экспорте
        Map<String, Object> multiFileMetadata = new HashMap<>();
        multiFileMetadata.put("fileCount", fileIds.size());
        multiFileMetadata.put("fileIds", fileIds);
        operation.getMetadata().put("multiFileExport", multiFileMetadata);

        return operationStatsService.createOperation(operation);
    }


    private ExportOperation initializeExportOperation(FileMetadata metadata,
                                                      String fileType,
                                                      ExportConfig exportConfig) {
        ExportOperation operation = new ExportOperation();
        operation.setClientId(metadata.getClientId());
        operation.setType(OperationType.EXPORT);
        operation.setExportFormat(fileType);
        operation.setSourceIdentifier(metadata.getStoredFilename());
        operation.setProcessingStrategy(exportConfig.getStrategyType().name());
        operation.setExportConfig(convertExportConfigToMap(exportConfig));

        // Инициализируем все необходимые поля
        initializeBaseOperationFields(operation);

        return operationStatsService.createOperation(operation);
    }

    private void handleExportError(ExportOperation operation, Exception e) {
        log.error("Ошибка экспорта данных: {}", e.getMessage(), e);

        if (operation != null) {
            operationStatsService.updateOperationStatus(
                    operation,
                    OperationStatus.FAILED,
                    e.getMessage(),
                    e.getClass().getSimpleName()
            );
        }
    }

    private Map<String, Object> convertExportConfigToMap(ExportConfig config) {
        // Конвертация конфигурации экспорта в Map для сохранения в БД
        Map<String, Object> configMap = new HashMap<>();
        // Добавляем нужные параметры из конфигурации
        return configMap;
    }

    private FileMetadata getFileMetadata(Long fileId) throws FileNotFoundException {
        return fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> {
                    log.error("Файл не найден: {}", fileId);
                    return new FileNotFoundException("File not found: " + fileId);
                });
    }

    private DataExporter getExporter(String fileType) throws ExportException {
        try {
            return exporterFactory.getExporter(fileType);
        } catch (Exception e) {
            log.error("Ошибка получения экспортера для типа {}: {}", fileType, e.getMessage());
            throw new ExportException("Invalid exporter type: " + fileType);
        }
    }

    /**
     * Обрабатывает данные с использованием выбранной стратегии
     */
    private List<Map<String, Object>> processDataWithStrategy(FileMetadata metadata,
                                                              DataProcessingStrategy strategy,
                                                              ExportConfig exportConfig,
                                                              ExportOperation operation) throws ExportException {
        try {
            // Валидируем параметры стратегии перед обработкой

            BatchProcessingData stats = BatchProcessingData.createNew();
            List<Map<String, Object>> data = getDataFromFile(metadata);

            // Добавляем метаданные в статистику
            Map<String, Object> strategyMetadata = new HashMap<>();
            strategyMetadata.put("strategyType", strategy.getStrategyType());
            strategyMetadata.put("parameters", exportConfig.getParams());
            operation.getMetadata().put("strategy", strategyMetadata);

            // Обрабатываем данные
            List<Map<String, Object>> processedData = strategy.processData(data, exportConfig, stats);

            // Обновляем статистику операции
            operation.setProcessedRecords(processedData.size());
            statisticsProcessor.updateOperationStats(operation);

            return processedData;

        } catch (IllegalArgumentException e) {
            // Ошибки валидации параметров
            log.error("Ошибка валидации параметров стратегии: {}", e.getMessage());
            throw new ExportException("Некорректные параметры стратегии: " + e.getMessage());
        } catch (Exception e) {
            log.error("Ошибка при обработке данных стратегией: {}", e.getMessage(), e);
            throw new ExportException("Ошибка обработки данных: " + e.getMessage());
        }
    }

    private ExportResult createExportResult(List<Map<String, Object>> data,
                                            FileMetadata metadata,
                                            DataExporter exporter,
                                            ExportConfig exportConfig,
                                            String fileType) throws ExportException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            BatchProcessingData stats = BatchProcessingData.createNew();
            ExportResult result = exporter.export(data, outputStream, exportConfig, stats);
            result.setFileName(generateFileName(metadata, fileType));
            result.setFileContent(outputStream.toByteArray());
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

    private void processBatchedProducts(List<Product> products, List<Map<String, Object>> result) {
        int size = products.size();
        for (int i = 0; i < size; i += BATCH_SIZE) {
            int end = Math.min(size, i + BATCH_SIZE);
            List<Product> batch = products.subList(i, end);
            List<Long> batchIds = batch.stream().map(Product::getId).toList();

            Map<Long, Product> regionsMap = createRegionsMap(batchIds);
            Map<Long, Product> competitorsMap = createCompetitorsMap(batchIds);

            processBatch(batch, regionsMap, competitorsMap, result);

            if (i % (BATCH_SIZE * 5) == 0) {
                log.debug("Обработано {} записей из {}", i, size);
            }
        }
    }


    private Map<Long, Product> createRegionsMap(List<Long> productIds) {
        return productRepository.findByIdInWithRegionData(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
    }

    private Map<Long, Product> createCompetitorsMap(List<Long> productIds) {
        return productRepository.findByIdInWithCompetitorData(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
    }

    private void processBatch(List<Product> products,
                              Map<Long, Product> regionsMap,
                              Map<Long, Product> competitorsMap,
                              List<Map<String, Object>> result) {
        for (Product product : products) {
            try {
                Map<String, Object> productData = extractFieldsWithDescriptions(product, "product");
                processProductRelations(product, productData, regionsMap, competitorsMap, result);
            } catch (Exception e) {
                log.warn("Ошибка обработки продукта {}: {}", product.getId(), e.getMessage());
            }
        }
    }

    private void processProductRelations(Product product,
                                         Map<String, Object> productData,
                                         Map<Long, Product> regionsMap,
                                         Map<Long, Product> competitorsMap,
                                         List<Map<String, Object>> result) {
        Product withRegions = regionsMap.get(product.getId());
        Product withCompetitors = competitorsMap.get(product.getId());

        if (withRegions != null && !withRegions.getRegionDataList().isEmpty()) {
            processRegionsWithCompetitors(withRegions, withCompetitors, productData, result);
        } else if (withCompetitors != null && !withCompetitors.getCompetitorDataList().isEmpty()) {
            processCompetitorsOnly(withCompetitors, productData, result);
        } else {
            result.add(productData);
        }
    }

    private void processRegionsWithCompetitors(Product withRegions,
                                               Product withCompetitors,
                                               Map<String, Object> productData,
                                               List<Map<String, Object>> result) {
        for (RegionData regionData : withRegions.getRegionDataList()) {
            Map<String, Object> rowData = new HashMap<>(productData);
            rowData.putAll(extractFieldsWithDescriptions(regionData, "regiondata"));

            if (withCompetitors != null && !withCompetitors.getCompetitorDataList().isEmpty()) {
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
        try {
            for (Field field : object.getClass().getDeclaredFields()) {
                processField(field, object, prefix, fields);
            }
        } catch (Exception e) {
            log.error("Ошибка извлечения полей для {}: {}", object.getClass().getSimpleName(), e.getMessage());
        }
        return fields;
    }

    private void processField(Field field, Object object, String prefix, Map<String, Object> fields) {
        FieldDescription description = field.getAnnotation(FieldDescription.class);
        if (description != null && !description.skipMapping()) {
            try {
                field.setAccessible(true);
                Object value = field.get(object);
                if (value != null) {
                    fields.put(prefix + "." + field.getName(), value);
                }
            } catch (Exception e) {
                log.warn("Ошибка доступа к полю {}: {}", field.getName(), e.getMessage());
            }
        }
    }

    private String generateFileName(FileMetadata originalFile, String exportType) {
        String baseName = originalFile.getOriginalFilename().replaceFirst("[.][^.]+$", "");
        return String.format("%s_export.%s", baseName, exportType.toLowerCase());
    }
}
