package by.zoomos_v2.service;

import by.zoomos_v2.annotations.FieldDescription;
import by.zoomos_v2.mapping.ClientMappingConfig;
import by.zoomos_v2.model.Client;
import by.zoomos_v2.model.Product;
import by.zoomos_v2.model.RegionData;
import by.zoomos_v2.model.SiteData;
import by.zoomos_v2.repository.ClientMappingConfigRepository;
import by.zoomos_v2.util.EntityRegistryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MappingConfigService {

    private final ClientMappingConfigRepository clientMappingConfigRepository;
    private final EntityRegistryService entityRegistryService;

    @Autowired
    public MappingConfigService(ClientMappingConfigRepository clientMappingConfigRepository, EntityRegistryService entityRegistryService) {
        this.clientMappingConfigRepository = clientMappingConfigRepository;
        this.entityRegistryService = entityRegistryService;
    }

    // Получить все маппинги для клиента по ID
    public List<ClientMappingConfig> getAllMappingConfigsForClient(Long clientId) {
        return clientMappingConfigRepository.findByClientId(clientId);
    }

    // Также можно добавить поиск по имени клиента
    public List<ClientMappingConfig> getAllMappingConfigsForClientByName(String clientName) {
        return clientMappingConfigRepository.findByClientName(clientName);
    }

    // Сохранить новый маппинг
    public void saveMappingConfig(Client client, String configName, String mappingJson) {
        // Создаем новый маппинг
        ClientMappingConfig config = new ClientMappingConfig();
        config.setClient(client);
        config.setName(configName);  // Устанавливаем имя конфигурации
        config.setMappingData(mappingJson);

        // Сохраняем конфигурацию, даже если она уже существует для этого типа
        clientMappingConfigRepository.save(config);
    }

    // Получить конфигурацию маппинга по типу для клиента
    public String getMappingConfigByType(Long clientId) {
        ClientMappingConfig config = clientMappingConfigRepository.findByClientId(clientId)
                .stream()
                .findFirst()
                .orElse(null);
        return config != null ? config.getMappingData() : null;
    }

    // Обновить существующий маппинг
    public void updateMappingConfig(Long clientId, String configName, String mappingJson) {
        // Ищем конфигурацию по типу и клиенту
        ClientMappingConfig config = clientMappingConfigRepository.findByClientId(clientId)
                .stream()
                .findFirst()
                .orElse(null);

        if (config != null) {
            config.setName(configName);  // Обновляем имя конфигурации
            config.setMappingData(mappingJson);  // Обновляем данные
            clientMappingConfigRepository.save(config);
        } else {
            throw new IllegalArgumentException("Mapping configuration not found.");
        }
    }

    // Удалить маппинг
    public void deleteMappingConfig(Long configId) {
        ClientMappingConfig config = clientMappingConfigRepository.findById(configId)
                .orElseThrow(() -> new IllegalArgumentException("Mapping configuration not found."));
        clientMappingConfigRepository.delete(config);
    }

    // Получить конфигурацию маппинга по ID
    public ClientMappingConfig getConfigById(Long configId) {
        return clientMappingConfigRepository.findById(configId)
                .orElseThrow(() -> new IllegalArgumentException("Mapping configuration not found."));
    }

    // метод для получения полей сущности с учётом аннотаций
    public Map<String, String> getEntityFieldDescriptions(Class<?> entityClass) {
        Map<String, String> fieldDescriptions = new LinkedHashMap<>();

        for (Field field : entityClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(FieldDescription.class)) {
                FieldDescription annotation = field.getAnnotation(FieldDescription.class);
                if (!annotation.value().equals("пропустить")) {
                    fieldDescriptions.put(field.getName(), annotation.value());
                }
            } else {
                // Если аннотация отсутствует, используем имя поля
                fieldDescriptions.put(field.getName(), field.getName());
            }
        }
        return fieldDescriptions;
    }

    // метод для получения полей всех сущностей с учётом аннотаций
    public Map<String, String> getCombinedEntityFieldDescriptions(List<Class<?>> entityClasses) {
        Map<String, String> combinedFields = new LinkedHashMap<>();
        for (Class<?> entityClass : entityClasses) {
            Map<String, String> fieldDescriptions = getEntityFieldDescriptions(entityClass);
            combinedFields.putAll(fieldDescriptions); // Объединяем все поля в один список
        }
        return combinedFields;
    }
}
