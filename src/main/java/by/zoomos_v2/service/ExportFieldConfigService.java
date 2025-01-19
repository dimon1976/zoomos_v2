package by.zoomos_v2.service;

import by.zoomos_v2.exception.FileProcessingException;
import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.model.ExportField;
import by.zoomos_v2.repository.ClientRepository;
import by.zoomos_v2.repository.ExportConfigRepository;
import by.zoomos_v2.util.EntityField;
import by.zoomos_v2.util.EntityFieldGroup;
import by.zoomos_v2.util.EntityRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Сервис для управления настройками полей экспорта
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportFieldConfigService {

    private final ExportConfigRepository exportConfigRepository;
    private final ClientRepository clientRepository;
    private final EntityRegistryService entityRegistryService;


    /**
     * Получает все конфигурации маппинга для магазина
     *
     * @param clientId идентификатор магазина
     * @return список конфигураций маппинга
     */
    @Transactional(readOnly = true)
    public List<ExportConfig> getMappingsForClient(Long clientId) {
        log.debug("Получение конфигураций маппинга для магазина: {}", clientId);
        return exportConfigRepository.findByClientId(clientId);
    }

    /**
     * Получает конфигурацию для клиента по ID
     */
    @Transactional(readOnly = true)
    public ExportConfig getConfigById(Long configId) {
        return exportConfigRepository.findById(configId)
                .orElseThrow(() -> new FileProcessingException("Конфигурация не найдена"));
    }

    /**
     * Создает конфигурацию по умолчанию со стандартным набором полей
     */
    @Transactional
    public ExportConfig createDefaultConfig(Long clientId) {
        log.debug("Создание конфигурации по умолчанию для клиента: {}", clientId);

        ExportConfig config = new ExportConfig();
        config.setClient(clientRepository.getReferenceById(clientId));
        config.setDefault(true);
        config.setName("Конфигурация по умолчанию");

        // Получаем дефолтный список полей
        List<EntityField> defaultFields = DefaultExportField.getDefaultFields();

        // Преобразуем в ExportField
        List<ExportField> exportFields = defaultFields.stream()
                .map(entityField -> {
                    ExportField field = new ExportField();
                    field.setExportConfig(config);
                    field.setSourceField(entityField.getMappingKey());
                    field.setDisplayName(entityField.getDescription());
                    field.setPosition(entityField.getPosition());
                    field.setEnabled(true);
                    return field;
                })
                .collect(Collectors.toList());

        config.setFields(exportFields);

        log.info("Создана конфигурация по умолчанию для клиента: {} с {} полями",
                clientId, exportFields.size());

        return exportConfigRepository.save(config);
    }

    @Transactional
    public ExportConfig createConfig(Long clientId, String name, List<EntityField> fields, String configDescription) {
        ExportConfig config = new ExportConfig();
        config.setClient(clientRepository.getReferenceById(clientId));
        config.setDefault(false);
        config.setName(name);

        List<ExportField> exportFields = fields.stream()
                .map(entityField -> {
                    ExportField field = new ExportField();
                    field.setExportConfig(config);
                    field.setSourceField(entityField.getMappingKey());
                    field.setDisplayName(entityField.getDescription());
                    field.setPosition(entityField.getPosition());
                    field.setEnabled(true);
                    return field;
                })
                .collect(Collectors.toList());

        config.setFields(exportFields);
        config.setDescription(configDescription);
        return exportConfigRepository.save(config);
    }

    @Transactional(readOnly = true)
    public boolean existsById(Long configId) {
        return exportConfigRepository.existsById(configId);
    }

    /**
     * Создает временную конфигурацию с полями по умолчанию (без сохранения в БД)
     */
    public ExportConfig createTemporaryConfig(Long clientId) {
        log.debug("Создание временной конфигурации для клиента: {}", clientId);

        ExportConfig config = new ExportConfig();
        config.setClient(clientRepository.getReferenceById(clientId));
        config.setDefault(true);
        config.setName("Новая конфигурация");

        // Получаем дефолтный список полей
        List<EntityField> defaultFields = DefaultExportField.getDefaultFields();

        // Преобразуем в ExportField
        List<ExportField> exportFields = defaultFields.stream()
                .map(entityField -> {
                    ExportField field = new ExportField();
                    field.setExportConfig(config);
                    field.setSourceField(entityField.getMappingKey());
                    field.setDisplayName(entityField.getDescription());
                    field.setPosition(entityField.getPosition());
                    field.setEnabled(true);
                    return field;
                })
                .collect(Collectors.toList());

        config.setFields(exportFields);
        return config; // Не сохраняем в БД
    }

    /**
     * Обновляет настройки полей (добавляет новые или отключает существующие)
     */
    @Transactional
    public void updateFieldsConfig(Long clientId,
                                   List<String> enabledFields,
                                   List<String> disabledFields,
                                   List<EntityField> positions,
                                   String configName,
                                   Long mappingId,
                                   String configDescription) {
        ExportConfig config = getConfigById(mappingId);

        // Проверка принадлежности конфигурации клиенту
        if (!config.getClient().getId().equals(clientId)) {
            throw new FileProcessingException("Конфигурация не принадлежит указанному клиенту");
        }

        // Если это дефолтная конфигурация и меняется набор полей
        if (config.isDefault() && enabledFields != null &&
                enabledFields.size() != config.getFields().stream()
                        .filter(ExportField::isEnabled).count()) {
            config.setDefault(false);
        }

        // Обновление имени
        if (configName != null && !configName.trim().isEmpty()) {
            config.setName(configName.trim());
        }

        // Обновление статусов полей
        if (enabledFields != null || disabledFields != null) {
            updateFieldStatuses(config, enabledFields);
        }

        // Обновление позиций
        if (positions != null) {
            updateFieldPositions(config, positions);
        }
        config.setDescription(configDescription);
        exportConfigRepository.save(config);
        log.debug("Конфигурация '{}' успешно обновлена", config.getName());
    }

    private void updateFieldStatuses(ExportConfig config,
                                     List<String> enabledFields) {
        // Сначала выключаем все поля
        config.getFields().forEach(field -> field.setEnabled(false));

        // Затем включаем нужные
        if (enabledFields != null) {
            enabledFields.forEach(fieldId ->
                    config.getFields().stream()
                            .filter(f -> f.getSourceField().equals(fieldId))
                            .forEach(f -> f.setEnabled(true))
            );
        }
    }

    private void updateFieldPositions(ExportConfig config, List<EntityField> positions) {
        Map<String, EntityField> positionMap = positions.stream()
                .collect(Collectors.toMap(
                        EntityField::getMappingKey,
                        Function.identity()
                ));

        config.getFields().forEach(field -> {
            EntityField entityField = positionMap.get(field.getSourceField());

            if (entityField != null) {
                // Обновляем позицию
                field.setPosition(entityField.getPosition());

                // Обновляем displayName
                if (entityField.getDescription() != null) {
                    field.setDisplayName(entityField.getDescription());
                }
            }
        });
    }

    /**
     * Удаляет конфигурацию маппинга
     *
     * @param mappingId идентификатор конфигурации
     * @throws FileProcessingException если конфигурация не найдена
     */
    @Transactional
    public void deleteMapping(Long mappingId) {
        log.debug("Удаление конфигурации маппинга с ID: {}", mappingId);
        if (!exportConfigRepository.existsById(mappingId)) {
            throw new FileProcessingException("Конфигурация маппинга не найдена");
        }
        exportConfigRepository.deleteById(mappingId);
    }

    /**
     * Получает доступные поля для добавления в конфигурацию
     *
     * @param config текущая конфигурация экспорта
     * @return список групп с доступными полями
     */
    public List<EntityFieldGroup> getAvailableFieldsForConfig(ExportConfig config) {
        List<EntityFieldGroup> allFields = entityRegistryService.getFieldsForMapping();

        // Получаем список ключей ВСЕХ полей из конфигурации (не только активных)
        Set<String> configFieldKeys = config.getFields().stream()
                .map(ExportField::getSourceField)
                .collect(Collectors.toSet());

        // Фильтруем группы, убирая все поля, которые есть в конфигурации
        return allFields.stream()
                .map(group -> filterGroup(group, configFieldKeys))
                .filter(group -> !group.getFields().isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Фильтрует группу полей, исключая активные поля
     */
    private EntityFieldGroup filterGroup(EntityFieldGroup group, Set<String> configFieldKeys) {
        EntityFieldGroup filteredGroup = new EntityFieldGroup();
        filteredGroup.setEntityName(group.getEntityName());

        List<EntityField> filteredFields = group.getFields().stream()
                .filter(field -> !configFieldKeys.contains(field.getMappingKey()))
                .collect(Collectors.toList());

        filteredGroup.setFields(filteredFields);
        return filteredGroup;
    }

    /**
     * Проверяет, существует ли поле в реестре
     */
    public boolean isFieldExists(String mappingKey) {
        return entityRegistryService.getFieldsForMapping().stream()
                .flatMap(group -> group.getFields().stream())
                .anyMatch(field -> field.getMappingKey().equals(mappingKey));
    }

    /**
     * Получает описание поля по ключу маппинга
     */
    public EntityField getFieldByMappingKey(String mappingKey) {
        return entityRegistryService.getFieldsForMapping().stream()
                .flatMap(group -> group.getFields().stream())
                .filter(field -> field.getMappingKey().equals(mappingKey))
                .findFirst()
                .orElse(null);
    }
}
