package by.zoomos_v2.service;

import by.zoomos_v2.model.Configuration;
import by.zoomos_v2.repository.ConfigurationRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class ConfigurationService {

    private final ConfigurationRepository configurationRepository;

    public ConfigurationService(ConfigurationRepository configurationRepository) {
        this.configurationRepository = configurationRepository;
    }

    public List<Configuration> getConfigurationsForClient(String clientName) {
        return configurationRepository.findByClientName(clientName);
    }

    public void addConfigurationForClient(String configName, String clientName) {
        Configuration config = new Configuration();
        config.setName(configName);
        config.setClientName(clientName);
        configurationRepository.save(config);
    }


    public Configuration getConfigurationById(Long id) {
        return configurationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Настройка не найдена"));
    }

    public void uploadFileForClient(MultipartFile file, Long configId, String clientName) {
        // Логика обработки файла, связанного с определенным клиентом
    }

    public void deleteConfiguration(Long id) {
        configurationRepository.deleteById(id);
    }
}
