package by.zoomos_v2.service.mapping;

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

import java.util.*;
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
    public Optional<List<ExportConfig>> getMappingsForClient(Long clientId) {
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

        List<ExportField> exportFields = new ArrayList<>();
        for (EntityField entityField : fields) {
            ExportField field = new ExportField();
            field.setExportConfig(config);
            field.setSourceField(entityField.getMappingKey());
            field.setDisplayName(entityField.getDescription());
            field.setPosition(entityField.getPosition());
            field.setEnabled(entityField.isEnabled()); // передаем значение enabled из entityField
            exportFields.add(field);
        }
        config.setFields(exportFields);
        config.setDescription(configDescription);
        // Проверяем, является ли конфигурация дефолтной
        ExportConfig defaultConfig = createTemporaryConfig(clientId);
        boolean isDefaultConfig = areFieldsEqual(config.getFields(), defaultConfig.getFields());
        config.setDefault(isDefaultConfig);

        return exportConfigRepository.save(config);
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

        // Получаем все доступные поля из сущностей
        List<EntityFieldGroup> allGroups = entityRegistryService.getFieldsForMapping();
        List<EntityField> allFields = allGroups.stream()
                .flatMap(group -> group.getFields().stream())
                .collect(Collectors.toList());

        // Получаем список дефолтных полей для сравнения
        List<EntityField> defaultFields = DefaultExportField.getDefaultFields();
        Set<String> defaultMappingKeys = defaultFields.stream()
                .map(EntityField::getMappingKey)
                .collect(Collectors.toSet());

        // Создаем Map с позициями и именами дефолтных полей
        Map<String, EntityField> defaultFieldsMap = defaultFields.stream()
                .collect(Collectors.toMap(
                        EntityField::getMappingKey,
                        field -> field
                ));

        // Преобразуем все поля в ExportField
        List<ExportField> exportFields = allFields.stream()
                .map(entityField -> {
                    ExportField field = new ExportField();
                    field.setExportConfig(config);
                    field.setSourceField(entityField.getMappingKey());

                    // Если поле есть в дефолтных - берем позицию и имя из дефолтного
                    if (defaultMappingKeys.contains(entityField.getMappingKey())) {
                        EntityField defaultField = defaultFieldsMap.get(entityField.getMappingKey());
                        field.setPosition(defaultField.getPosition());
                        field.setDisplayName(defaultField.getDescription());
                        field.setEnabled(true);
                    } else {
                        // Для остальных полей
                        field.setPosition(0);  // или другая логика назначения позиций
                        field.setDisplayName(entityField.getDescription());
                        field.setEnabled(false);
                    }
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
                                   List<EntityField> fields,
                                   String configName,
                                   Long mappingId,
                                   String configDescription) {
        ExportConfig config = getConfigById(mappingId);

        // Проверка принадлежности конфигурации клиенту
        if (!config.getClient().getId().equals(clientId)) {
            throw new FileProcessingException("Конфигурация не принадлежит указанному клиенту");
        }

        // Обновляем базовую информацию
        if (configName != null && !configName.trim().isEmpty()) {
            config.setName(configName.trim());
        }
        config.setDescription(configDescription);

        // Обновляем все поля
        Map<String, EntityField> updateMap = fields.stream()
                .collect(Collectors.toMap(
                        EntityField::getMappingKey,
                        Function.identity()
                ));

        config.getFields().forEach(field -> {
            EntityField updateData = updateMap.get(field.getSourceField());
            if (updateData != null) {
                field.setEnabled(updateData.isEnabled());
                field.setPosition(updateData.getPosition());
                field.setDisplayName(updateData.getDescription());
            }
        });

        // Проверяем, является ли конфигурация дефолтной
        ExportConfig defaultConfig = createTemporaryConfig(clientId);
        boolean isDefaultConfig = areFieldsEqual(config.getFields(), defaultConfig.getFields());
        config.setDefault(isDefaultConfig);

        exportConfigRepository.save(config);
        log.debug("Конфигурация '{}' успешно обновлена", config.getName());
    }

    /**
     * Сравнивает два списка полей на идентичность (только enabled поля)
     */
    private boolean areFieldsEqual(List<ExportField> configFields, List<ExportField> defaultFields) {
        // Получаем множества sourceField только для включенных полей
        Set<String> configEnabledFields = configFields.stream()
                .filter(ExportField::isEnabled)
                .map(ExportField::getSourceField)
                .collect(Collectors.toSet());

        Set<String> defaultEnabledFields = defaultFields.stream()
                .filter(ExportField::isEnabled)
                .map(ExportField::getSourceField)
                .collect(Collectors.toSet());

        // Сравниваем множества
        return configEnabledFields.equals(defaultEnabledFields);
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
}
