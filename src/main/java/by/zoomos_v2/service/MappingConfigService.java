package by.zoomos_v2.service;

import by.zoomos_v2.model.MappingConfig;
import by.zoomos_v2.repository.MappingConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MappingConfigService {

    @Autowired
    private MappingConfigRepository mappingConfigRepository;

    public void saveMappingConfig(MappingConfig mappingConfig) {
        mappingConfigRepository.save(mappingConfig);
    }
}
