package by.zoomos_v2.service.file.input.service;

import by.zoomos_v2.annotations.FieldDescription;
import by.zoomos_v2.model.CompetitorData;
import by.zoomos_v2.model.Product;
import by.zoomos_v2.model.RegionData;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static by.zoomos_v2.constant.BatchSize.BATCH_SIZE_DATA_SAVE;
import static by.zoomos_v2.util.HeapSize.getHeapSizeAsString;

@Slf4j
@Service
@Transactional
public class DataPersistenceService {


//    private static final String PRODUCT_PREFIX = "product";
//    private static final String REGION_PREFIX = "regiondata";
//    private static final String SITE_PREFIX = "competitordata";
//
//    private final EntityManager entityManager;
//
//    public DataPersistenceService(EntityManager entityManager) {
//        this.entityManager = entityManager;
//    }
//
//    public Map<String, Object> saveEntities(List<Map<String, String>> data, Long clientId, Map<String, String> mapping, Long fileId) {
//        int successCount = 0;
//        int errorCount = 0;
//        List<String> errors = new ArrayList<>();
//        int batchCounter = 0;
//
//        try {
//            List<Product> productBatch = new ArrayList<>();
//
//            for (Map<String, String> row : data) {
//                try {
//                    // Создаем сущности
//                    Product product = createProduct(row, clientId, mapping, fileId);
//
//                    if (hasEntityData(mapping, REGION_PREFIX)) {
//                        RegionData regionData = createRegionData(row, clientId, mapping);
//                        regionData.setProduct(product);
//                        product.getRegionDataList().add(regionData);
//                    }
//
//                    if (hasEntityData(mapping, SITE_PREFIX)) {
//                        CompetitorData competitorData = createSiteData(row, clientId, mapping);
//                        competitorData.setProduct(product);
//                        product.getCompetitorDataList().add(competitorData);
//                    }
//
//                    productBatch.add(product);
//                    batchCounter++;
//
//                    // Если набрался батч - сохраняем
//                    if (batchCounter >= BATCH_SIZE_DATA_SAVE) {
//                        saveBatch(productBatch);
//                        successCount += productBatch.size();
//                        productBatch.clear();
//                        batchCounter = 0;
//
//                        // Очищаем контекст персистентности для экономии памяти
//                        entityManager.clear();
//                    }
//
//                } catch (Exception e) {
//                    errorCount++;
//                    String errorMessage = String.format("Ошибка в строке %d: %s",
//                            successCount + errorCount, e.getMessage());
//                    errors.add(errorMessage);
//                    log.error("Ошибка обработки строки {}/{}: {}",
//                            successCount + errorCount, data.size(), e.getMessage());
//                }
//            }
//
//            // Сохраняем оставшиеся записи
//            if (!productBatch.isEmpty()) {
//                saveBatch(productBatch);
//                successCount += productBatch.size();
//                productBatch.clear();
//            }
//
//        } catch (Exception e) {
//            log.error("Критическая ошибка при сохранении данных: {}", e.getMessage(), e);
//            errors.add("Критическая ошибка: " + e.getMessage());
//        }
//
//        log.info("Обработка завершена. Успешно: {}, Ошибок: {}, Всего записей: {}",
//                successCount, errorCount, data.size());
//
//        Map<String, Object> result = new HashMap<>();
//        result.put("successCount", successCount);
//        result.put("errorCount", errorCount);
//        result.put("totalCount", data.size());
//        if (!errors.isEmpty()) {
//            result.put("errors", errors);
//        }
//
//        return result;
//    }
//
//    private void saveBatch(List<Product> products) {
//        for (Product product : products) {
//            entityManager.persist(product);
//        }
//        entityManager.flush();
//    }
//
//
//    private Product createProduct(Map<String, String> data, Long clientId, Map<String, String> mapping, Long fileId) {
//        Product product = new Product();
//        product.setClientId(clientId);
//        product.setFileId(fileId);
//        setEntityFields(product, data, mapping, PRODUCT_PREFIX);
//        return product;
//    }
//
//    private RegionData createRegionData(Map<String, String> data, Long clientId, Map<String, String> mapping) {
//        RegionData regionData = new RegionData();
//        regionData.setClientId(clientId);
//
//        setEntityFields(regionData, data, mapping, REGION_PREFIX);
//        return regionData;
//    }
//
//    private CompetitorData createSiteData(Map<String, String> data, Long clientId, Map<String, String> mapping) {
//        CompetitorData competitorData = new CompetitorData();
//        competitorData.setClientId(clientId);
//
//        setEntityFields(competitorData, data, mapping, SITE_PREFIX);
//        return competitorData;
//    }
//
//    private <T> void setEntityFields(T entity, Map<String, String> data, Map<String, String> mapping, String prefix) {
//        // Получаем все поля сущности через reflection
//        Field[] fields = entity.getClass().getDeclaredFields();
//
//        for (Field field : fields) {
//            try {
//                if (shouldSkipField(field)) {
//                    continue;
//                }
//
//                String fullFieldPath = field.getName();
//                String mappedField = getMappedField(mapping, fullFieldPath, prefix);
//
//                if (mappedField != null && data.containsKey(mappedField)) {
//                    field.setAccessible(true);
//                    String value = data.get(mappedField);
//                    if (value != null && !value.trim().isEmpty()) {
//                        setFieldValue(entity, field, value.trim());
//                    }
//                }
//            } catch (Exception e) {
//                log.warn("Error setting field {} for entity {}: {}",
//                        field.getName(), entity.getClass().getSimpleName(), e.getMessage());
//            }
//        }
//    }
//
//    private boolean shouldSkipField(Field field) {
//        // Проверяем аннотацию FieldDescription
//        FieldDescription fieldDesc = field.getAnnotation(FieldDescription.class);
//        return fieldDesc != null && fieldDesc.skipMapping();
//    }
//
//    private String getMappedField(Map<String, String> mapping, String entityField, String prefix) {
//        String fullFieldPath = prefix + "." + entityField;
//        return mapping.entrySet().stream()
//                .filter(entry -> entry.getValue().equals(fullFieldPath)) // Теперь сравниваем со значением маппинга
//                .map(Map.Entry::getKey) // Возвращаем ключ (заголовок из файла)
//                .findFirst()
//                .orElse(null);
//    }
//
//    private <T> void setFieldValue(T entity, Field field, String value) throws IllegalAccessException {
//        if (value == null || value.trim().isEmpty()) {
//            return;
//        }
//
//        Class<?> fieldType = field.getType();
//        try {
//            if (fieldType == String.class) {
//                field.set(entity, value);
//            } else if (fieldType == Long.class || fieldType == long.class) {
//                field.set(entity, Long.parseLong(value));
//            } else if (fieldType == Integer.class || fieldType == int.class) {
//                field.set(entity, Integer.parseInt(value));
//            } else if (fieldType == Double.class || fieldType == double.class) {
//                field.set(entity, Double.parseDouble(value));
//            } else if (fieldType == Boolean.class || fieldType == boolean.class) {
//                field.set(entity, Boolean.parseBoolean(value));
//            } else if (fieldType == BigDecimal.class) {
//                field.set(entity, new BigDecimal(value));
//            } else if (fieldType.isEnum()) {
//                field.set(entity, Enum.valueOf((Class<Enum>) fieldType, value));
//            }
//            // Добавьте другие типы по необходимости
//        } catch (Exception e) {
//            log.warn("Failed to convert value '{}' to type {} for field {}",
//                    value, fieldType.getSimpleName(), field.getName());
//        }
//    }
//
//    private boolean hasEntityData(Map<String, String> mapping, String prefix) {
//        return mapping.values().stream()
//                .anyMatch(value -> value.startsWith(prefix));
//    }

    private static final String PRODUCT_PREFIX = "product";
    private static final String REGION_PREFIX = "regiondata";
    private static final String SITE_PREFIX = "competitordata";
    private static final int CLEAR_CONTEXT_FREQUENCY = 10;

    private final EntityManager entityManager;

    public DataPersistenceService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Сохраняет список сущностей с поддержкой пакетной обработки
     *
     * @param data     список данных для обработки
     * @param clientId идентификатор клиента
     * @param mapping  маппинг полей
     * @param fileId   идентификатор файла
     * @return Map с результатами обработки, содержащий количество успешных и неуспешных операций
     */
    public Map<String, Object> saveEntities(List<Map<String, String>> data, Long clientId, Map<String, String> mapping, Long fileId) {
        int successCount = 0;
        int errorCount = 0;
        List<String> errors = new ArrayList<>();

        try {
            List<Product> productBatch = new ArrayList<>();
            int batchNumber = 0;

            for (Map<String, String> row : data) {
                try {
                    Product product = createProduct(row, clientId, mapping, fileId);
                    addRelatedEntities(product, row, clientId, mapping);
                    productBatch.add(product);

                    if (productBatch.size() >= BATCH_SIZE_DATA_SAVE) {
                        saveBatch(productBatch);
                        successCount += productBatch.size();
                        productBatch.clear();
                        batchNumber++;

                        if (batchNumber % CLEAR_CONTEXT_FREQUENCY == 0) {
                            clearPersistenceContext();
                        }
                    }
                } catch (Exception e) {
                    handleRowProcessingError(e, successCount, errorCount, data.size(), errors);
                    errorCount++;
                }
            }

            // Сохраняем оставшиеся записи
            if (!productBatch.isEmpty()) {
                saveBatch(productBatch);
                successCount += productBatch.size();
            }

        } catch (Exception e) {
            handleCriticalError(e, errors);
        } finally {
            clearPersistenceContext();
        }

        logProcessingResults(successCount, errorCount, data.size());
        return buildResult(successCount, errorCount, data.size(), errors);
    }

    /**
     * Добавляет связанные сущности к продукту
     */
    private void addRelatedEntities(Product product, Map<String, String> row, Long clientId, Map<String, String> mapping) {
        if (hasEntityData(mapping, REGION_PREFIX)) {
            RegionData regionData = createRegionData(row, clientId, mapping);
            regionData.setProduct(product);
            product.getRegionDataList().add(regionData);
        }

        if (hasEntityData(mapping, SITE_PREFIX)) {
            CompetitorData competitorData = createSiteData(row, clientId, mapping);
            competitorData.setProduct(product);
            product.getCompetitorDataList().add(competitorData);
        }
    }

    /**
     * Сохраняет батч продуктов в БД
     */
    private void saveBatch(List<Product> products) {
        try {
            for (Product product : products) {
                entityManager.persist(product);
            }
            entityManager.flush();
            log.debug("Сохранен батч из {} записей", products.size());
        } catch (Exception e) {
            log.error("Ошибка при сохранении батча: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Очищает контекст персистентности
     */
    private void clearPersistenceContext() {
        entityManager.flush();
        entityManager.clear();
        System.gc();
        log.debug("Контекст персистентности очищен");
    }

    private void handleRowProcessingError(Exception e, int successCount, int errorCount, int totalSize, List<String> errors) {
        String errorMessage = String.format("Ошибка в строке %d: %s", successCount + errorCount + 1, e.getMessage());
        errors.add(errorMessage);
        log.error("Ошибка обработки строки {}/{}: {}", successCount + errorCount + 1, totalSize, e.getMessage());
    }

    private void handleCriticalError(Exception e, List<String> errors) {
        String message = "Критическая ошибка при сохранении данных: " + e.getMessage();
        log.error(message, e);
        errors.add(message);
    }

    private void logProcessingResults(int successCount, int errorCount, int totalCount) {
        log.debug("Processed batch. Current heap: {}", getHeapSizeAsString());
        log.info("Обработка завершена. Успешно: {}, Ошибок: {}, Всего записей: {}",
                successCount, errorCount, totalCount);
    }

    private Map<String, Object> buildResult(int successCount, int errorCount, int totalCount, List<String> errors) {
        Map<String, Object> result = new HashMap<>();
        result.put("successCount", successCount);
        result.put("errorCount", errorCount);
        result.put("totalCount", totalCount);
        if (!errors.isEmpty()) {
            result.put("errors", errors);
        }
        return result;
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

    /**
     * Устанавливает значения полей сущности на основе данных из маппинга
     */
    private <T> void setEntityFields(T entity, Map<String, String> data, Map<String, String> mapping, String prefix) {
        Field[] fields = entity.getClass().getDeclaredFields();

        for (Field field : fields) {
            try {
                if (shouldSkipField(field)) {
                    continue;
                }

                String mappedField = getMappedField(mapping, field.getName(), prefix);
                if (mappedField != null && data.containsKey(mappedField)) {
                    field.setAccessible(true);
                    String value = data.get(mappedField);
                    if (value != null && !value.trim().isEmpty()) {
                        setFieldValue(entity, field, value.trim());
                    }
                }
            } catch (Exception e) {
                log.warn("Ошибка установки поля {} для сущности {}: {}",
                        field.getName(), entity.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    private boolean shouldSkipField(Field field) {
        FieldDescription fieldDesc = field.getAnnotation(FieldDescription.class);
        return fieldDesc != null && fieldDesc.skipMapping();
    }

    private String getMappedField(Map<String, String> mapping, String entityField, String prefix) {
        String fullFieldPath = prefix + "." + entityField;
        return mapping.entrySet().stream()
                .filter(entry -> entry.getValue().equals(fullFieldPath))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    /**
     * Устанавливает значение поля с учетом его типа
     */
    private <T> void setFieldValue(T entity, Field field, String value) throws IllegalAccessException {
        if (value == null || value.trim().isEmpty()) {
            return;
        }

        Class<?> fieldType = field.getType();
        try {
            Object convertedValue = convertToFieldType(value, fieldType);
            if (convertedValue != null) {
                field.set(entity, convertedValue);
            }
        } catch (Exception e) {
            log.warn("Не удалось преобразовать значение '{}' в тип {} для поля {}",
                    value, fieldType.getSimpleName(), field.getName());
        }
    }

    /**
     * Преобразует строковое значение в соответствующий тип поля
     */
    private Object convertToFieldType(String value, Class<?> fieldType) {
        try {
            if (fieldType == String.class) return value;
            if (fieldType == Long.class || fieldType == long.class) return Long.parseLong(value);
            if (fieldType == Integer.class || fieldType == int.class) return Integer.parseInt(value);
            if (fieldType == Double.class || fieldType == double.class) return Double.parseDouble(value);
            if (fieldType == Boolean.class || fieldType == boolean.class) return Boolean.parseBoolean(value);
            if (fieldType == BigDecimal.class) return new BigDecimal(value);
            if (fieldType.isEnum()) return Enum.valueOf((Class<Enum>) fieldType, value);
        } catch (Exception e) {
            log.debug("Ошибка преобразования значения '{}' в тип {}", value, fieldType.getSimpleName());
        }
        return null;
    }

    private boolean hasEntityData(Map<String, String> mapping, String prefix) {
        return mapping.values().stream()
                .anyMatch(value -> value.startsWith(prefix));
    }
}