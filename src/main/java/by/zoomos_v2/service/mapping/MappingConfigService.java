package by.zoomos_v2.service.mapping;

import by.zoomos_v2.mapping.ClientMappingConfig;
import by.zoomos_v2.repository.ClientMappingConfigRepository;
import by.zoomos_v2.exception.FileProcessingException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Сервис для управления конфигурациями маппинга.
 * Обеспечивает создание, получение, обновление и удаление
 * конфигураций маппинга для магазинов.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MappingConfigService {

    private final ClientMappingConfigRepository mappingRepository;
    private final ObjectMapper objectMapper;

    /**
     * Получает все конфигурации маппинга для магазина
     *
     * @param clientId идентификатор магазина
     * @return список конфигураций маппинга
     */
    @Transactional(readOnly = true)
    public List<ClientMappingConfig> getMappingsForClient(Long clientId) {
        log.debug("Получение конфигураций маппинга для магазина: {}", clientId);
        return mappingRepository.findByClientId(clientId);
    }

    /**
     * Получает конфигурацию маппинга по ID
     *
     * @param id идентификатор конфигурации
     * @return конфигурация маппинга
     * @throws FileProcessingException если конфигурация не найдена
     */
    @Transactional(readOnly = true)
    public ClientMappingConfig getMappingById(Long id) {
        log.debug("Получение конфигурации маппинга по ID: {}", id);
        return mappingRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Конфигурация маппинга с ID {} не найдена", id);
                    return new FileProcessingException("Конфигурация маппинга не найдена");
                });
    }

    /**
     * Создает новую конфигурацию маппинга
     *
     * @param mapping новая конфигурация
     * @return сохраненная конфигурация
     */
    @Transactional
    public ClientMappingConfig createMapping(ClientMappingConfig mapping) {
        log.debug("Создание нового маппинга: {}", mapping);

        // Проверяем формат JSON в columnsConfig
        validateColumnsConfig(mapping.getColumnsConfig());

        // Создаем новый объект маппинга
        ClientMappingConfig newMapping = new ClientMappingConfig();
        newMapping.setClientId(mapping.getClientId());
        newMapping.setName(mapping.getName());
        newMapping.setFileType(mapping.getFileType());
        newMapping.setDescription(mapping.getDescription());
        newMapping.setColumnsConfig(mapping.getColumnsConfig());
        newMapping.setActive(mapping.isActive());

        // Сохраняем маппинг
        ClientMappingConfig savedMapping = mappingRepository.save(newMapping);
        log.info("Создан новый маппинг с ID: {}", savedMapping.getId());

        return savedMapping;
    }

    private void validateColumnsConfig(String columnsConfig) {
        try {
            // Проверяем, что это валидный JSON-объект
            JsonNode jsonNode = objectMapper.readTree(columnsConfig);
            if (!jsonNode.isObject()) {
                throw new IllegalArgumentException("Конфигурация колонок должна быть объектом");
            }

            // Проверяем, что все значения являются строками
            jsonNode.fields().forEachRemaining(entry -> {
                if (!entry.getValue().isTextual()) {
                    throw new IllegalArgumentException(
                            "Все значения в конфигурации колонок должны быть строками");
                }
            });
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Неверный формат JSON в конфигурации колонок");
        }
    }

    /**
     * Обновляет существующую конфигурацию маппинга
     *
     * @param mapping обновленная конфигурация
     * @return обновленная конфигурация
     * @throws FileProcessingException если конфигурация не найдена
     */
    @Transactional
    public ClientMappingConfig updateMapping(ClientMappingConfig mapping) {
        log.debug("Обновление конфигурации маппинга с ID: {}", mapping.getId());
        validateMapping(mapping);

        ClientMappingConfig existingMapping = mappingRepository.findById(mapping.getId())
                .orElseThrow(() -> new IllegalArgumentException("Маппинг не найден"));

        existingMapping.setName(mapping.getName());
        existingMapping.setFileType(mapping.getFileType());
        existingMapping.setDescription(mapping.getDescription());
        existingMapping.setColumnsConfig(mapping.getColumnsConfig());
        existingMapping.setActive(mapping.isActive());

        return mappingRepository.save(existingMapping);
    }

    /**
     * Удаляет конфигурацию маппинга
     *
     * @param id идентификатор конфигурации
     * @throws FileProcessingException если конфигурация не найдена
     */
    @Transactional
    public void deleteMapping(Long id) {
        log.debug("Удаление конфигурации маппинга с ID: {}", id);
        if (!mappingRepository.existsById(id)) {
            throw new FileProcessingException("Конфигурация маппинга не найдена");
        }
        mappingRepository.deleteById(id);
    }

    /**
     * Проверяет базовую валидность конфигурации маппинга
     *
     * @param mapping конфигурация для проверки
     * @throws FileProcessingException если конфигурация невалидна
     */
    private void validateMapping(ClientMappingConfig mapping) {
        if (mapping == null) {
            throw new FileProcessingException("Конфигурация маппинга не может быть null");
        }
        if (mapping.getClientId() == null) {
            throw new FileProcessingException("ID магазина не может быть null");
        }
        if (mapping.getName() == null || mapping.getName().trim().isEmpty()) {
            throw new FileProcessingException("Название конфигурации не может быть пустым");
        }
        if (mapping.getColumnsConfig() == null || mapping.getColumnsConfig().trim().isEmpty()) {
            throw new FileProcessingException("Конфигурация колонок не может быть пустой");
        }
    }
}