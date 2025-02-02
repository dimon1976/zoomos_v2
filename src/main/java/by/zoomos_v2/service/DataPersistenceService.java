package by.zoomos_v2.service;

import by.zoomos_v2.annotations.FieldDescription;
import by.zoomos_v2.model.CompetitorData;
import by.zoomos_v2.model.Product;
import by.zoomos_v2.model.RegionData;
import by.zoomos_v2.repository.FileMetadataRepository;
import by.zoomos_v2.repository.ProductRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@Transactional
public class DataPersistenceService {
    private final ProductRepository productRepository;
    private static final String PRODUCT_PREFIX = "product";
    private static final String REGION_PREFIX = "regiondata";
    private static final String SITE_PREFIX = "competitordata";

    public DataPersistenceService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Map<String, Object> saveEntities(List<Map<String, String>> data, Long clientId, Map<String, String> mapping, Long fileId) {
        int successCount = 0;
        int errorCount = 0;
        List<String> errors = new ArrayList<>(); // Добавляем список для хранения ошибок

        for (Map<String, String> row : data) {
            try {
                saveEntityTransaction(row, clientId, mapping, fileId);
                successCount++;
                log.debug("Успешно обработана запись {}/{}", successCount, data.size());
            } catch (Exception e) {
                errorCount++;
                String errorMessage = String.format("Ошибка в строке %d: %s", successCount + errorCount, e.getMessage());
                errors.add(errorMessage);
                log.error("Ошибка обработки строки {} ({}/{}): {}",
                        row, successCount + errorCount, data.size(), e.getMessage());
            }
        }

        log.info("Обработка завершена. Успешно: {}, Ошибок: {}, Всего записей: {}",
                successCount, errorCount, data.size());

        Map<String, Object> result = new HashMap<>();
        result.put("successCount", successCount);
        result.put("errorCount", errorCount);
        result.put("totalCount", data.size());
        if (!errors.isEmpty()) {
            result.put("errors", errors);
        }

        return result;
    }

    @Transactional
    public void saveEntityTransaction(Map<String, String> row, Long clientId, Map<String, String> mapping, Long fileId) {
//        FileMetadata fileMetadata = fileMetadataRepository
        try {
            // Создаем основную сущность Product
            Product product = createProduct(row, clientId, mapping, fileId);

            // Создаем и связываем RegionData, если есть соответствующие данные
            if (hasEntityData(mapping, REGION_PREFIX)) {
                RegionData regionData = createRegionData(row, clientId, mapping);
                regionData.setProduct(product);
                product.getRegionDataList().add(regionData);
            }

            // Создаем и связываем SiteData, если есть соответствующие данные
            if (hasEntityData(mapping, SITE_PREFIX)) {
                CompetitorData competitorData = createSiteData(row, clientId, mapping);
                competitorData.setProduct(product);
                product.getCompetitorDataList().add(competitorData);
            }

            productRepository.save(product);

        } catch (Exception e) {
            log.error("Ошибка сохранения записи: {}", e.getMessage());
            throw new RuntimeException("Ошибка сохранения записи: " + e.getMessage(), e);
        }
    }

    private Product createProduct(Map<String, String> data, Long clientId, Map<String, String> mapping, Long fileId) {
        Product product = new Product();
        product.setClientId(clientId);
        product.setFileId(fileId);

        setEntityFields(product, data, mapping, PRODUCT_PREFIX);
        return product;
    }

    private RegionData createRegionData(Map<String, String> data, Long clientId, Map<String, String> mapping) {
        RegionData regionData = new RegionData();
        regionData.setClientId(clientId);

        setEntityFields(regionData, data, mapping, REGION_PREFIX);
        return regionData;
    }

    private CompetitorData createSiteData(Map<String, String> data, Long clientId, Map<String, String> mapping) {
        CompetitorData competitorData = new CompetitorData();
        competitorData.setClientId(clientId);

        setEntityFields(competitorData, data, mapping, SITE_PREFIX);
        return competitorData;
    }

    private <T> void setEntityFields(T entity, Map<String, String> data, Map<String, String> mapping, String prefix) {
        // Получаем все поля сущности через reflection
        Field[] fields = entity.getClass().getDeclaredFields();

        for (Field field : fields) {
            try {
                if (shouldSkipField(field)) {
                    continue;
                }

                String fullFieldPath = field.getName();
                String mappedField = getMappedField(mapping, fullFieldPath, prefix);

                if (mappedField != null && data.containsKey(mappedField)) {
                    field.setAccessible(true);
                    String value = data.get(mappedField);
                    if (value != null && !value.trim().isEmpty()) {
                        setFieldValue(entity, field, value.trim());
                    }
                }
            } catch (Exception e) {
                log.warn("Error setting field {} for entity {}: {}",
                        field.getName(), entity.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    private boolean shouldSkipField(Field field) {
        // Проверяем аннотацию FieldDescription
        FieldDescription fieldDesc = field.getAnnotation(FieldDescription.class);
        return fieldDesc != null && fieldDesc.skipMapping();
    }

    private String getMappedField(Map<String, String> mapping, String entityField, String prefix) {
        String fullFieldPath = prefix + "." + entityField;
        return mapping.entrySet().stream()
                .filter(entry -> entry.getValue().equals(fullFieldPath)) // Теперь сравниваем со значением маппинга
                .map(Map.Entry::getKey) // Возвращаем ключ (заголовок из файла)
                .findFirst()
                .orElse(null);
    }

    private <T> void setFieldValue(T entity, Field field, String value) throws IllegalAccessException {
        if (value == null || value.trim().isEmpty()) {
            return;
        }

        Class<?> fieldType = field.getType();
        try {
            if (fieldType == String.class) {
                field.set(entity, value);
            } else if (fieldType == Long.class || fieldType == long.class) {
                field.set(entity, Long.parseLong(value));
            } else if (fieldType == Integer.class || fieldType == int.class) {
                field.set(entity, Integer.parseInt(value));
            } else if (fieldType == Double.class || fieldType == double.class) {
                field.set(entity, Double.parseDouble(value));
            } else if (fieldType == Boolean.class || fieldType == boolean.class) {
                field.set(entity, Boolean.parseBoolean(value));
            } else if (fieldType == BigDecimal.class) {
                field.set(entity, new BigDecimal(value));
            } else if (fieldType.isEnum()) {
                field.set(entity, Enum.valueOf((Class<Enum>) fieldType, value));
            }
            // Добавьте другие типы по необходимости
        } catch (Exception e) {
            log.warn("Failed to convert value '{}' to type {} for field {}",
                    value, fieldType.getSimpleName(), field.getName());
        }
    }

    private boolean hasEntityData(Map<String, String> mapping, String prefix) {
        return mapping.values().stream()
                .anyMatch(value -> value.startsWith(prefix));
    }
}
