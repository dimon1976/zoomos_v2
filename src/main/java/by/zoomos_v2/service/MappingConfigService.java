package by.zoomos_v2.service;

import by.zoomos_v2.annotations.FieldDescription;
import by.zoomos_v2.mapping.ClientMappingConfig;
import by.zoomos_v2.model.Client;
import by.zoomos_v2.repository.ClientMappingConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MappingConfigService {

    @Autowired
    private ClientMappingConfigRepository clientMappingConfigRepository;

    @Autowired
    private ObjectMapper objectMapper; // Для преобразования JSON в объекты и обратно

    // Получить все маппинги для клиента по ID
    public List<ClientMappingConfig> getAllMappingConfigsForClient(Long clientId) {
        return clientMappingConfigRepository.findByClientId(clientId);
    }

    // Также можно добавить поиск по имени клиента
    public List<ClientMappingConfig> getAllMappingConfigsForClientByName(String clientName) {
        return clientMappingConfigRepository.findByClientName(clientName);
    }

    // Сохранить новый маппинг
    public void saveMappingConfig(Client client, String configName, String type, String mappingJson) {
        // Создаем новый маппинг
        ClientMappingConfig config = new ClientMappingConfig();
        config.setClient(client);
        config.setName(configName);  // Устанавливаем имя конфигурации
        config.setType(type);
        config.setMappingData(mappingJson);

        // Сохраняем конфигурацию, даже если она уже существует для этого типа
        clientMappingConfigRepository.save(config);
    }

    // Получить конфигурацию маппинга по типу для клиента
    public String getMappingConfigByType(Long clientId, String type) {
        ClientMappingConfig config = clientMappingConfigRepository.findByClientId(clientId)
                .stream()
                .filter(c -> c.getType().equals(type))
                .findFirst()
                .orElse(null);
        return config != null ? config.getMappingData() : null;
    }

    // Обновить существующий маппинг
    public void updateMappingConfig(Long clientId, String type, String configName, String mappingJson) {
        // Ищем конфигурацию по типу и клиенту
        ClientMappingConfig config = clientMappingConfigRepository.findByClientId(clientId)
                .stream()
                .filter(c -> c.getType().equals(type))
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

    // метод для получения полей сущности с учётом аннотаций
    public Map<String, String> getEntityFieldDescriptions(Class<?> entityClass) {
        Map<String, String> fieldDescriptions = new LinkedHashMap<>();

        for (Field field : entityClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(FieldDescription.class)) {
                FieldDescription annotation = field.getAnnotation(FieldDescription.class);
                fieldDescriptions.put(field.getName(), annotation.value());
            } else {
                // Если аннотация отсутствует, используем имя поля
                fieldDescriptions.put(field.getName(), field.getName());
            }
        }

        return fieldDescriptions;
    }
}
