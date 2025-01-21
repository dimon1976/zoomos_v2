package by.zoomos_v2.service.mapping;

import by.zoomos_v2.exception.ValidationException;
import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.model.ExportField;
import by.zoomos_v2.repository.ClientRepository;
import by.zoomos_v2.repository.ExportConfigRepository;
import by.zoomos_v2.util.EntityField;
import by.zoomos_v2.util.EntityFieldGroup;
import by.zoomos_v2.util.EntityRegistryService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Унифицированный сервис для работы с конфигурацией экспорта
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportConfigService {
    private final ExportConfigRepository exportConfigRepository;
    private final ClientRepository clientRepository;
    private final EntityRegistryService entityRegistryService;

    /**
     * Получает конфигурацию для клиента
     *
     * @param clientId идентификатор клиента
     * @return конфигурация экспорта
     */
    @Transactional(readOnly = true)
    public ExportConfig getConfigByClientId(Long clientId) {
        return exportConfigRepository.findByClientIdAndIsDefaultTrue(clientId)
                .orElseGet(() -> createConfig(clientId));
    }

    /**
     * Получает конфигурации клиента
     *
     * @param clientId идентификатор клиента
     * @return конфигурации экспорта
     */
    @Transactional(readOnly = true)
    public List<ExportConfig> getConfigsByClientId(Long clientId) {
        return exportConfigRepository.findByClientId(clientId)
                .orElseThrow(() -> new EntityNotFoundException("ExportConfig not found for clientId: " + clientId));
    }

    /**
     * Получает конфигурацию по ID
     *
     * @param configId идентификатор конфигурации
     * @return конфигурация экспорта
     */
    @Transactional(readOnly = true)
    public ExportConfig getConfigById(Long configId) {
        return exportConfigRepository.findById(configId)
                .orElseThrow(() -> new EntityNotFoundException("ExportConfig not found: " + configId));
    }

    /**
     * Создает новую конфигурацию для клиента
     *
     * @param clientId идентификатор клиента
     * @return созданная конфигурация
     */
    @Transactional
    public ExportConfig createConfig(Long clientId) {
        log.debug("Создание конфигурации для клиента: {}", clientId);

        ExportConfig config = new ExportConfig();
        config.setClient(clientRepository.getReferenceById(clientId));
        config.setDefault(true);
        config.setName("Default");

        // Получаем все поля из зарегистрированных сущностей
        List<EntityFieldGroup> fieldGroups = entityRegistryService.getFieldsForMapping();
        List<ExportField> exportFields = new ArrayList<>();
        int position = 0;

        // Преобразуем поля сущностей в поля экспорта
        for (EntityFieldGroup group : fieldGroups) {
            for (EntityField field : group.getFields()) {
                ExportField exportField = createExportField(field, position++, config);
                exportFields.add(exportField);
            }
        }

        config.setFields(exportFields);
        return exportConfigRepository.save(config);
    }

    /**
     * Обновляет конфигурацию полей
     *
     * @param clientId идентификатор клиента
     * @param fields список полей для обновления
     * @return обновленная конфигурация
     */
    @Transactional
    public ExportConfig updateConfig(Long clientId, List<EntityField> fields) {
        validateFields(fields);

        ExportConfig config = getConfigByClientId(clientId);
        updateFields(config, fields);

        return exportConfigRepository.save(config);
    }

    private ExportField createExportField(EntityField entityField, int position, ExportConfig config) {
        ExportField field = new ExportField();
        field.setExportConfig(config);
        field.setSourceField(entityField.getMappingKey()); // Используем mappingKey вместо fieldName
        field.setDisplayName(entityField.getDescription());
        field.setPosition(position);
        field.setEnabled(true);
        return field;
    }

    private void updateFields(ExportConfig config, List<EntityField> entityFields) {
        // Создаем мапу существующих полей по ключу маппинга
        Map<String, ExportField> existingFields = config.getFields().stream()
                .collect(Collectors.toMap(ExportField::getSourceField, f -> f));

        List<ExportField> updatedFields = entityFields.stream()
                .map(entityField -> {
                    // Ищем существующее поле по mappingKey
                    ExportField field = existingFields.getOrDefault(
                            entityField.getMappingKey(),
                            new ExportField()
                    );
                    updateFieldFromEntity(field, entityField, config);
                    return field;
                })
                .collect(Collectors.toList());

        config.setFields(updatedFields);
    }

    private void updateFieldFromEntity(ExportField exportField, EntityField entityField, ExportConfig config) {
        exportField.setExportConfig(config);
        exportField.setSourceField(entityField.getMappingKey()); // Используем mappingKey
        exportField.setDisplayName(entityField.getDescription());
        exportField.setPosition(entityField.getPosition());
        exportField.setEnabled(true);
    }

    private void validateFields(List<EntityField> fields) {
        if (fields == null || fields.isEmpty()) {
            throw new ValidationException("Список полей не может быть пустым");
        }

        Set<Integer> positions = new HashSet<>();
        Set<String> mappingKeys = new HashSet<>();

        for (EntityField field : fields) {
            if (field.getMappingKey() == null || field.getMappingKey().trim().isEmpty()) {
                throw new ValidationException("Ключ маппинга не может быть пустым");
            }
            if (field.getDescription() == null || field.getDescription().trim().isEmpty()) {
                throw new ValidationException("Описание поля не может быть пустым");
            }
            if (!positions.add(field.getPosition())) {
                throw new ValidationException("Дублирующаяся позиция поля: " + field.getPosition());
            }
            if (!mappingKeys.add(field.getMappingKey())) {
                throw new ValidationException("Дублирующийся ключ маппинга: " + field.getMappingKey());
            }
        }
    }
}
