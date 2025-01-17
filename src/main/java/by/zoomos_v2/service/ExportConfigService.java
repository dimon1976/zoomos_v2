package by.zoomos_v2.service;

import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.model.ExportField;
import by.zoomos_v2.repository.ClientRepository;
import by.zoomos_v2.repository.ExportConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportConfigService {
    private final ExportConfigRepository exportConfigRepository;
    private final ClientRepository clientRepository;

    @Transactional(readOnly = true)
    public ExportConfig getDefaultConfig(Long clientId) {
        return exportConfigRepository.findByClientIdAndIsDefaultTrue(clientId)
                .orElseGet(() -> createDefaultConfig(clientId));
    }

    @Transactional
    public ExportConfig createDefaultConfig(Long clientId) {
        ExportConfig config = new ExportConfig();
        config.setClient(clientRepository.getReferenceById(clientId));
        config.setDefault(true);
        config.setName("Default");

        List<ExportField> defaultFields = Arrays.asList(
                createField("id", "ID", 0, config),
                createField("competitorName", "Конкурент", 1, config)
                // добавьте другие поля по умолчанию
        );

        config.setFields(defaultFields);
        return exportConfigRepository.save(config);
    }

    private ExportField createField(String source, String display, int position, ExportConfig config) {
        ExportField field = new ExportField();
        field.setSourceField(source);
        field.setDisplayName(display);
        field.setPosition(position);
        field.setEnabled(true);
        field.setExportConfig(config);
        return field;
    }
}
