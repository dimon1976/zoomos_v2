package by.zoomos_v2.service;

import by.zoomos_v2.DTO.MappingFieldDTO;
import by.zoomos_v2.annotations.FieldDescription;
import by.zoomos_v2.config.MappingException;
import by.zoomos_v2.mapping.ClientMappingConfig;
import by.zoomos_v2.model.Client;
import by.zoomos_v2.repository.ClientMappingConfigRepository;
import by.zoomos_v2.util.EntityRegistryService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.annotation.Id;
import org.springframework.stereotype.Service;

import javax.persistence.Column;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class MappingConfigService {

    private final ClientMappingConfigRepository clientMappingConfigRepository;
    private final EntityRegistryService entityRegistryService;
    private final ObjectMapper objectMapper;

    public MappingConfigService(
            ClientMappingConfigRepository clientMappingConfigRepository,
            EntityRegistryService entityRegistryService,
            ObjectMapper objectMapper) {
        this.clientMappingConfigRepository = clientMappingConfigRepository;
        this.entityRegistryService = entityRegistryService;
        this.objectMapper = objectMapper;
    }

    @Cacheable(value = "mappingConfigs", key = "#clientId")
    public List<ClientMappingConfig> getAllMappingConfigsForClient(Long clientId) {
        return clientMappingConfigRepository.findByClientId(clientId);
    }

    @Transactional
    public ClientMappingConfig saveMappingConfig(Client client, String configName, Map<String, String> mappingHeaders) {
        validateMappingHeaders(mappingHeaders);

        String mappingJson = serializeMappingToJson(mappingHeaders);

        ClientMappingConfig config = new ClientMappingConfig();
        config.setClient(client);
        config.setName(configName);
        config.setMappingData(mappingJson);
        config.setCreatedAt(LocalDateTime.now());

        return clientMappingConfigRepository.save(config);
    }

    @Transactional
    public ClientMappingConfig updateMappingConfig(Long configId, String configName, Map<String, String> mappingHeaders) {
        ClientMappingConfig config = clientMappingConfigRepository.findById(configId)
                .orElseThrow(() -> new MappingException("Mapping configuration not found"));

        validateMappingHeaders(mappingHeaders);

        String mappingJson = serializeMappingToJson(mappingHeaders);

        config.setName(configName);
        config.setMappingData(mappingJson);
        config.setUpdatedAt(LocalDateTime.now());

        return clientMappingConfigRepository.save(config);
    }

    public Map<String, MappingFieldDTO> getCombinedEntityFieldDescriptions(List<Class<?>> entityClasses) {
        Map<String, MappingFieldDTO> combinedFields = new LinkedHashMap<>();

        for (Class<?> entityClass : entityClasses) {
            processEntityFields(entityClass, combinedFields);
        }

        return combinedFields;
    }

    private void processEntityFields(Class<?> entityClass, Map<String, MappingFieldDTO> fields) {
        for (Field field : entityClass.getDeclaredFields()) {
            if (shouldSkipField(field)) {
                continue;
            }

            MappingFieldDTO fieldDTO = createFieldDTO(field, entityClass);
            fields.put(field.getName(), fieldDTO);
        }
    }

    private MappingFieldDTO createFieldDTO(Field field, Class<?> entityClass) {
        MappingFieldDTO fieldDTO = new MappingFieldDTO();
        fieldDTO.setFieldName(field.getName());
        fieldDTO.setFieldType(field.getType().getSimpleName());
        fieldDTO.setEntityName(entityClass.getSimpleName());

        // Получаем описание из аннотации FieldDescription
        if (field.isAnnotationPresent(FieldDescription.class)) {
            FieldDescription annotation = field.getAnnotation(FieldDescription.class);
            fieldDTO.setDescription(annotation.value());
        } else {
            // Если аннотации нет, используем имя поля как описание
            fieldDTO.setDescription(formatFieldName(field.getName()));
        }

        return fieldDTO;
    }

    // Вспомогательный метод для форматирования имени поля
    private String formatFieldName(String fieldName) {
        // Разбиваем camelCase и делаем первую букву заглавной
        String[] words = fieldName.split("(?<=\\w)(?=\\p{Lu})");
        StringBuilder formattedName = new StringBuilder();

        for (String word : words) {
            if (formattedName.length() > 0) {
                formattedName.append(" ");
            }
            formattedName.append(word.substring(0, 1).toUpperCase())
                    .append(word.substring(1).toLowerCase());
        }

        return formattedName.toString();
    }

    private boolean shouldSkipField(Field field) {
        if (field.isAnnotationPresent(FieldDescription.class)) {
            FieldDescription annotation = field.getAnnotation(FieldDescription.class);
            return annotation.value().equals("пропустить") || annotation.skipMapping();
        }
        return field.isAnnotationPresent(Id.class) ||
                field.isAnnotationPresent(javax.persistence.Version.class) ||
                Modifier.isStatic(field.getModifiers());
    }

    private String serializeMappingToJson(Map<String, String> mapping) {
        try {
            return objectMapper.writeValueAsString(mapping);
        } catch (JsonProcessingException e) {
            throw new MappingException("Error serializing mapping data");
        }
    }

//    private void validateMappingHeaders(Map<String, String> mappingHeaders) {
//        if (mappingHeaders == null || mappingHeaders.isEmpty()) {
//            throw new MappingException("Mapping headers cannot be empty");
//        }
//
//        List<Class<?>> entityClasses = entityRegistryService.getEntityClasses();
//        Map<String, MappingFieldDTO> validFields = getCombinedEntityFieldDescriptions(entityClasses);
//
//        for (String mappedField : mappingHeaders.values()) {
//            if (!validFields.containsKey(mappedField)) {
//                throw new MappingException("Invalid field mapping: " + mappedField);
//            }
//        }
//    }

    private void validateMappingHeaders(Map<String, String> mappingHeaders) {
        if (mappingHeaders == null || mappingHeaders.isEmpty()) {
            throw new MappingException("Mapping headers cannot be empty");
        }

        // Получаем все классы сущностей
        List<Class<?>> entityClasses = entityRegistryService.getEntityClasses();

        // Получаем валидные поля и обязательные поля из аннотаций
        Map<String, MappingFieldDTO> validFields = getCombinedEntityFieldDescriptions(entityClasses);
        Set<String> requiredFields = getRequiredFields(entityClasses);

        // Проверяем ключи на наличие в валидных полях
        for (String fieldKey : mappingHeaders.keySet()) {
            if (!validFields.containsKey(fieldKey)) {
                throw new MappingException("Invalid field mapping key: " + fieldKey);
            }
        }

        // Проверяем, что все обязательные поля имеют маппинг
        for (String requiredField : requiredFields) {
            if (!mappingHeaders.containsKey(requiredField)) {
                throw new MappingException("Missing required mapping for field: " + requiredField);
            }
        }
    }

    private Set<String> getRequiredFields(List<Class<?>> entityClasses) {
        Set<String> requiredFields = new HashSet<>();

        for (Class<?> entityClass : entityClasses) {
            for (Field field : entityClass.getDeclaredFields()) {
                if (isRequiredField(field)) {
                    requiredFields.add(field.getName());
                }
            }
        }

        return requiredFields;
    }

    private boolean isRequiredField(Field field) {
        // Проверка на аннотации обязательности
        if (field.isAnnotationPresent(Column.class)) {
            Column column = field.getAnnotation(Column.class);
            if (!column.nullable()) {
                return true;
            }
        }
        if (field.isAnnotationPresent(NotNull.class)) {
            return true;
        }

        // Добавьте сюда логику для любых других пользовательских аннотаций
        return false;
    }



    // Удалить маппинг
    public void deleteMappingConfig(Long configId) {
        ClientMappingConfig config = clientMappingConfigRepository.findById(configId)
                .orElseThrow(() -> new IllegalArgumentException("Mapping configuration not found."));
        clientMappingConfigRepository.delete(config);
    }
}
