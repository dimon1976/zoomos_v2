package by.zoomos_v2.service;

import by.zoomos_v2.exception.FileProcessingException;
import by.zoomos_v2.mapping.ClientMappingConfig;
import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.model.ExportField;
import by.zoomos_v2.repository.ClientRepository;
import by.zoomos_v2.repository.ExportConfigRepository;
import by.zoomos_v2.util.EntityField;
import by.zoomos_v2.util.EntityRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Function;
import java.util.List;
import java.util.Map;
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
     * Получает или создает конфигурацию для клиента
     */
//    @Transactional
//    public ExportConfig getOrCreateConfig(Long clientId) {
//        return exportConfigRepository.findByClientIdAndIsDefaultTrue(clientId)
//                .orElseGet(() -> createDefaultConfig(clientId));
//    }

    /**
     * Получает конфигурацию для клиента по ID
     */
    @Transactional
    public ExportConfig getConfigById(Long configId) {
        return exportConfigRepository.findById(configId)
                .orElseGet(() -> createDefaultConfig(configId));
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

    /**
     * Обновляет настройки полей (добавляет новые или отключает существующие)
     */
    @Transactional
    public void updateFieldsConfig(Long clientId,
                                   List<String> enabledFields,
                                   List<String> disabledFields,
                                   List<EntityField> positions,
                                   String configName,
                                   Long mappingId) {
        log.info("Updating config for client {}: name={}, fields={}, mappingID={}", clientId, configName, positions, mappingId);
        ExportConfig config = getConfigById(mappingId);

        if(enabledFields.size()!=config.getFields().size()){
            config.setDefault(false);
        }
        // Обновляем имя конфигурации
        if (configName != null && !configName.trim().isEmpty()) {
            config.setName(configName.trim());
        }

        // Обновляем статусы включения/выключения
        if (enabledFields != null || disabledFields != null) {
            updateFieldStatuses(config, enabledFields);
        }

        // Обновляем позиции полей
        if (positions != null) {
            updateFieldPositions(config, positions);
        }

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
}
