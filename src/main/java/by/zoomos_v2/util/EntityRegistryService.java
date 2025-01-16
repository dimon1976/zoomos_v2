package by.zoomos_v2.util;

import by.zoomos_v2.annotations.FieldDescription;
import by.zoomos_v2.model.Product;
import by.zoomos_v2.model.RegionData;
import by.zoomos_v2.model.SiteData;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.*;

@Service
public class EntityRegistryService {

    private static final List<Class<?>> ENTITY_CLASSES = Arrays.asList(
            Product.class,
            RegionData.class,
            SiteData.class
    );

    /**
     * Получает все сущности для маппинга
     */
    public List<Class<?>> getEntityClasses() {
        return ENTITY_CLASSES;
    }

    /**
     * Получает структуру полей для маппинга
     */
    public List<EntityFieldGroup> getFieldsForMapping() {
        List<EntityFieldGroup> groups = new ArrayList<>();

        for (Class<?> entityClass : ENTITY_CLASSES) {
            EntityFieldGroup group = new EntityFieldGroup();
            group.setEntityName(entityClass.getSimpleName());

            List<EntityField> fields = new ArrayList<>();
            for (Field field : entityClass.getDeclaredFields()) {
                FieldDescription description = field.getAnnotation(FieldDescription.class);
                if (description != null && !description.skipMapping()) {
                    EntityField entityField = new EntityField();
                    entityField.setFieldName(field.getName());
                    entityField.setDescription(description.value());
                    entityField.setMappingKey(entityClass.getSimpleName().toLowerCase() + "." + field.getName());
                    fields.add(entityField);
                }
            }

            if (!fields.isEmpty()) {
                group.setFields(fields);
                groups.add(group);
            }
        }

        return groups;
    }

    @Data
    public static class EntityFieldGroup {
        private String entityName;
        private List<EntityField> fields;
    }

    @Data
    public static class EntityField {
        private String fieldName;
        private String description;
        private String mappingKey;
    }
}