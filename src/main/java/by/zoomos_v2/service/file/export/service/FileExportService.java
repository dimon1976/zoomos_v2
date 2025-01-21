package by.zoomos_v2.service.file.export.service;
import by.zoomos_v2.annotations.FieldDescription;
import by.zoomos_v2.model.*;
import by.zoomos_v2.repository.FileMetadataRepository;
import by.zoomos_v2.repository.ProductRepository;
import by.zoomos_v2.service.file.ProcessingStats;
import by.zoomos_v2.service.file.export.exporter.DataExporter;
import by.zoomos_v2.service.file.export.exporter.DataExporterFactory;
import by.zoomos_v2.service.file.export.strategy.DataProcessingStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Сервис для экспорта данных из файлов
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileExportService {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final FileMetadataRepository fileMetadataRepository;
    private final ProductRepository productRepository;
    private final DataExporterFactory exporterFactory;
    private final List<DataProcessingStrategy> processingStrategies;

    /**
     * Экспортирует данные из указанного файла
     *
     * @param fileId ID файла в системе
     * @param exportConfig конфигурация экспорта
     * @param fileType тип файла для экспорта (CSV, XLSX)
     * @return результат экспорта
     */
    @Transactional(readOnly = true)
    public ExportResult exportFileData(Long fileId, ExportConfig exportConfig, String fileType) {
        log.debug("Начало экспорта данных из файла ID: {}", fileId);

        // Получаем метаданные файла
        FileMetadata fileMetadata = fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));

        // Получаем данные из файла
        List<Map<String, Object>> data = getDataFromFile(fileMetadata);

        // Создаем статистику обработки
        ProcessingStats stats = ProcessingStats.createNew();

        // Находим и применяем подходящую стратегию обработки
        Optional<DataProcessingStrategy> strategy = processingStrategies.stream()
                .filter(s -> s.supports(exportConfig))
                .findFirst();

        if (strategy.isPresent()) {
            log.debug("Применяем стратегию обработки: {}", strategy.get().getClass().getSimpleName());
            data = strategy.get().processData(data, exportConfig, stats);
        }

        // Получаем нужный экспортер
        DataExporter exporter = exporterFactory.getExporter(fileType);

        // Выполняем экспорт
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ExportResult result = exporter.export(data, outputStream, exportConfig, stats);
            result.setFileName(generateFileName(fileMetadata, fileType));
            result.setFileContent(outputStream.toByteArray());
            return result;
        } catch (Exception e) {
            log.error("Ошибка при экспорте данных: {}", e.getMessage(), e);
            return ExportResult.error(e.getMessage());
        }
    }

    /**
     * Получает данные из файла и преобразует их для экспорта
     */
    private List<Map<String, Object>> getDataFromFile(FileMetadata fileMetadata) {
        log.debug("Получение данных из файла: {}", fileMetadata.getOriginalFilename());

        List<Product> products = productRepository.findByFileId(fileMetadata.getId());
        List<Map<String, Object>> result = new ArrayList<>();

        for (Product product : products) {
            // Обрабатываем основные данные продукта
            Map<String, Object> productData = extractFieldsWithDescriptions(product);

            // Обрабатываем данные регионов
            for (RegionData regionData : product.getRegionDataList()) {
                Map<String, Object> rowData = new HashMap<>(productData);
                rowData.putAll(extractFieldsWithDescriptions(regionData));

                // Обрабатываем данные конкурентов
                for (CompetitorData competitorData : product.getCompetitorDataList()) {
                    Map<String, Object> fullRowData = new HashMap<>(rowData);
                    fullRowData.putAll(extractFieldsWithDescriptions(competitorData));
                    result.add(fullRowData);
                }

                // Если нет данных конкурентов, добавляем строку только с данными региона
                if (product.getCompetitorDataList().isEmpty()) {
                    result.add(rowData);
                }
            }

            // Если нет данных регионов, добавляем строку только с данными продукта
            if (product.getRegionDataList().isEmpty()) {
                result.add(productData);
            }
        }

        log.debug("Получено {} записей для экспорта", result.size());
        return result;
    }

    /**
     * Извлекает значения полей с аннотациями FieldDescription
     */
    private Map<String, Object> extractFieldsWithDescriptions(Object object) {
        Map<String, Object> fields = new HashMap<>();

        for (Field field : object.getClass().getDeclaredFields()) {
            FieldDescription description = field.getAnnotation(FieldDescription.class);
            if (description != null && !description.skipMapping()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(object);
                    if (value != null) {
                        // Форматируем значение в зависимости от типа
                        if (value instanceof Date) {
                            value = DATE_TIME_FORMATTER.format(((Date) value).toInstant());
                        }
                        fields.put(field.getName(), value);
                    }
                } catch (IllegalAccessException e) {
                    log.error("Ошибка при получении значения поля: {}", field.getName(), e);
                }
            }
        }

        return fields;
    }

    /**
     * Генерирует имя файла для экспорта
     */
    private String generateFileName(FileMetadata originalFile, String exportType) {
        String baseName = originalFile.getOriginalFilename().replaceFirst("[.][^.]+$", "");
        return String.format("%s_export.%s", baseName, exportType.toLowerCase());
    }
}
