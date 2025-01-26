package by.zoomos_v2.service.file.export.service;

import by.zoomos_v2.annotations.FieldDescription;
import by.zoomos_v2.model.*;
import by.zoomos_v2.repository.FileMetadataRepository;
import by.zoomos_v2.repository.ProductRepository;
import by.zoomos_v2.service.file.ProcessingStats;
import by.zoomos_v2.service.file.export.exporter.DataExporter;
import by.zoomos_v2.service.file.export.exporter.DataExporterFactory;
import by.zoomos_v2.service.file.export.strategy.DataProcessingStrategy;
import by.zoomos_v2.service.file.export.strategy.ProcessingStrategyType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.lang.reflect.Field;
import java.rmi.server.ExportException;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для экспорта данных из файлов
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileExportService {

//    private final FileMetadataRepository fileMetadataRepository;
//    private final ProductRepository productRepository;
//    private final DataExporterFactory exporterFactory;
//    private final List<DataProcessingStrategy> processingStrategies;
//
//    /**
//     * Экспортирует данные из указанного файла
//     *
//     * @param fileId       ID файла в системе
//     * @param exportConfig конфигурация экспорта
//     * @param fileType     тип файла для экспорта (CSV, XLSX)
//     * @return результат экспорта
//     */
//    @Transactional(readOnly = true)
//    public ExportResult exportFileData(Long fileId, ExportConfig exportConfig, String fileType) {
//        log.debug("Начало экспорта данных из файла ID: {}", fileId);
//
//        // Получаем метаданные файла
//        FileMetadata fileMetadata = fileMetadataRepository.findById(fileId)
//                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));
//
//        // Получаем данные из файла
//        List<Map<String, Object>> data = getDataFromFile(fileMetadata);
//
//        // Создаем статистику обработки
//        ProcessingStats stats = ProcessingStats.createNew();
//
//        // Находим и применяем подходящую стратегию обработки
//        DataProcessingStrategy strategy = processingStrategies.stream()
//                .filter(s -> s.supports(exportConfig))
//                .findFirst()
//                .orElseGet(() -> {
//                    log.warn("Стратегия не найдена, используется стратегия по умолчанию");
//                    return processingStrategies.stream()
//                            .filter(s -> ProcessingStrategyType.DEFAULT.equals(s.getStrategyType()))
//                            .findFirst()
//                            .orElseThrow(() -> new IllegalStateException("Default strategy not found"));
//                });
//
//        log.debug("Применяется стратегия: {}", strategy.getClass().getSimpleName());
//        data = strategy.processData(data, exportConfig, stats);
//
//        // Получаем нужный экспортер
//        DataExporter exporter = exporterFactory.getExporter(fileType);
//
//        // Выполняем экспорт
//        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
//            ExportResult result = exporter.export(data, outputStream, exportConfig, stats);
//            result.setFileName(generateFileName(fileMetadata, fileType));
//            result.setFileContent(outputStream.toByteArray());
//            return result;
//        } catch (Exception e) {
//            log.error("Ошибка при экспорте данных: {}", e.getMessage(), e);
//            return ExportResult.error(e.getMessage());
//        }
//    }
//
//    /**
//     * Получает данные из файла и преобразует их для экспорта
//     */
//    private List<Map<String, Object>> getDataFromFile(FileMetadata fileMetadata) {
//        List<Product> products = productRepository.findByFileId(fileMetadata.getId());
//        List<Long> productIds = products.stream().map(Product::getId).toList();
//
//        List<Product> productsWithRegions = productRepository.findByIdInWithRegionData(productIds);
//        List<Product> productsWithCompetitors = productRepository.findByIdInWithCompetitorData(productIds);
//
//        Map<Long, Product> productWithRegionsMap = productsWithRegions.stream()
//                .collect(Collectors.toMap(Product::getId, p -> p));
//        Map<Long, Product> productWithCompetitorsMap = productsWithCompetitors.stream()
//                .collect(Collectors.toMap(Product::getId, p -> p));
//
//        List<Map<String, Object>> result = new ArrayList<>();
//
//        for (Product product : products) {
//            Product withRegions = productWithRegionsMap.get(product.getId());
//            Product withCompetitors = productWithCompetitorsMap.get(product.getId());
//
//            Map<String, Object> productData = extractFieldsWithDescriptions(product, "product");
//
//            if (withRegions != null && !withRegions.getRegionDataList().isEmpty()) {
//                for (RegionData regionData : withRegions.getRegionDataList()) {
//                    Map<String, Object> rowData = new HashMap<>(productData);
//                    rowData.putAll(extractFieldsWithDescriptions(regionData, "regiondata"));
//
//                    if (withCompetitors != null && !withCompetitors.getCompetitorDataList().isEmpty()) {
//                        for (CompetitorData competitorData : withCompetitors.getCompetitorDataList()) {
//                            Map<String, Object> fullRowData = new HashMap<>(rowData);
//                            fullRowData.putAll(extractFieldsWithDescriptions(competitorData, "competitordata"));
//                            result.add(fullRowData);
//                        }
//                    } else {
//                        result.add(rowData);
//                    }
//                }
//            } else if (withCompetitors != null && !withCompetitors.getCompetitorDataList().isEmpty()) {
//                for (CompetitorData competitorData : withCompetitors.getCompetitorDataList()) {
//                    Map<String, Object> fullRowData = new HashMap<>(productData);
//                    fullRowData.putAll(extractFieldsWithDescriptions(competitorData, "competitordata"));
//                    result.add(fullRowData);
//                }
//            } else {
//                result.add(productData);
//            }
//        }
//
//        return result;
//    }
//
//    /**
//     * Извлекает значения полей с аннотациями FieldDescription
//     */
//    private Map<String, Object> extractFieldsWithDescriptions(Object object, String prefix) {
//        Map<String, Object> fields = new HashMap<>();
//        for (Field field : object.getClass().getDeclaredFields()) {
//            FieldDescription description = field.getAnnotation(FieldDescription.class);
//            if (description != null && !description.skipMapping()) {
//                try {
//                    field.setAccessible(true);
//                    Object value = field.get(object);
//                    if (value != null) {
//                        fields.put(prefix + "." + field.getName(), value);
//                    }
//                } catch (IllegalAccessException e) {
//                    log.error("Ошибка при получении значения поля: {}", field.getName(), e);
//                }
//            }
//        }
//        return fields;
//    }
//
//    /**
//     * Генерирует имя файла для экспорта
//     */
//    private String generateFileName(FileMetadata originalFile, String exportType) {
//        String baseName = originalFile.getOriginalFilename().replaceFirst("[.][^.]+$", "");
//        return String.format("%s_export.%s", baseName, exportType.toLowerCase());
//    }

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int BATCH_SIZE = 1000;

    private final FileMetadataRepository fileMetadataRepository;
    private final ProductRepository productRepository;
    private final DataExporterFactory exporterFactory;
    private final List<DataProcessingStrategy> processingStrategies;

    /**
     * Экспортирует данные из файла с оптимизированной обработкой
     *
     * @param fileId ID файла
     * @param exportConfig конфигурация экспорта
     * @param fileType тип экспорта (CSV, XLSX)
     * @return результат экспорта
     */
    @Transactional(readOnly = true)
    public ExportResult exportFileData(Long fileId, ExportConfig exportConfig, String fileType) {
        log.info("Начало экспорта. FileId: {}, Type: {}", fileId, fileType);

        try {
            FileMetadata metadata = getFileMetadata(fileId);
            DataExporter exporter = getExporter(fileType);
            DataProcessingStrategy strategy = selectStrategy(exportConfig);

            List<Map<String, Object>> data = processDataWithStrategy(metadata, strategy, exportConfig);
            return createExportResult(data, metadata, exporter, exportConfig, fileType);

        } catch (Exception e) {
            log.error("Ошибка экспорта данных: {}", e.getMessage(), e);
            return ExportResult.error(e.getMessage());
        }
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

    private DataProcessingStrategy selectStrategy(ExportConfig exportConfig) {
        return processingStrategies.stream()
                .filter(s -> s.supports(exportConfig))
                .findFirst()
                .orElseGet(() -> getDefaultStrategy());
    }

    private DataProcessingStrategy getDefaultStrategy() {
        return processingStrategies.stream()
                .filter(s -> ProcessingStrategyType.DEFAULT.equals(s.getStrategyType()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Default strategy not found"));
    }

    private List<Map<String, Object>> processDataWithStrategy(FileMetadata metadata,
                                                              DataProcessingStrategy strategy,
                                                              ExportConfig exportConfig) throws ExportException {
        ProcessingStats stats = ProcessingStats.createNew();
        List<Map<String, Object>> data = getDataFromFile(metadata);
        return strategy.processData(data, exportConfig, stats);
    }

    private ExportResult createExportResult(List<Map<String, Object>> data,
                                            FileMetadata metadata,
                                            DataExporter exporter,
                                            ExportConfig exportConfig,
                                            String fileType) throws ExportException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ProcessingStats stats = ProcessingStats.createNew();
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
