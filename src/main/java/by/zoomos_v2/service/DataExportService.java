package by.zoomos_v2.service;

import by.zoomos_v2.exception.ValidationException;
import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.model.ExportField;
import by.zoomos_v2.model.FileMetadata;
import by.zoomos_v2.repository.FileMetadataRepository;
import by.zoomos_v2.repository.ProductRepository;
import by.zoomos_v2.service.processor.client.ClientDataProcessor;
import by.zoomos_v2.service.processor.client.ClientDataProcessorFactory;
import by.zoomos_v2.service.processor.client.ProcessingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Сервис для выгрузки данных.
 * Обеспечивает получение и обработку данных для экспорта.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataExportService {

    private final FileMetadataRepository fileMetadataRepository;
    private final ProductRepository productRepository;
    private final ClientDataProcessorFactory clientDataProcessorFactory;
    private final ExportFieldConfigService exportFieldConfigService;

    /**
     * Получает обработанные данные из последнего файла клиента
     */
    @Transactional(readOnly = true)
    public List<Map<String, String>> getDataFromLastFile(Long clientId) {
        log.debug("Получение данных из последнего файла для клиента: {}", clientId);

        FileMetadata lastFile = fileMetadataRepository.findFirstByClientIdAndStatusOrderByProcessingCompletedAtDesc(
                        clientId, "COMPLETED")
                .orElseThrow(() -> {
                    log.error("Не найдены обработанные файлы для клиента: {}", clientId);
                    return new ValidationException("Не найдены обработанные файлы");
                });

        return getDataByFile(lastFile.getId(), clientId);
    }

    /**
     * Получает данные из конкретного файла с применением конфигурации полей
     */
    @Transactional(readOnly = true)
    public List<Map<String, String>> getDataByFile(Long fileId, Long clientId) {
        log.debug("Получение данных из файла: {} для клиента: {}", fileId, clientId);

        // Проверяем файл
        FileMetadata file = validateAndGetFile(fileId, clientId);

        // Получаем конфигурацию полей
//        ExportConfig fieldConfig = exportFieldConfigService.getOrCreateConfig(clientId);
        ExportConfig fieldConfig = exportFieldConfigService.getConfigById(clientId);

        // Получаем сырые данные
        List<Map<String, String>> rawData = productRepository.findAllByFileId(fileId);
        log.debug("Получено {} записей из файла {}", rawData.size(), fileId);

        // Применяем процессор клиента
        List<Map<String, String>> processedData = processData(rawData, clientId);

        // Применяем конфигурацию полей
        List<Map<String, String>> result = applyFieldConfiguration(processedData, fieldConfig);

        log.info("Успешно обработано {} записей из файла {} для клиента {}",
                result.size(), fileId, clientId);

        return result;
    }

    private FileMetadata validateAndGetFile(Long fileId, Long clientId) {
        FileMetadata file = fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> {
                    log.error("Файл не найден: {}", fileId);
                    return new ValidationException("Файл не найден");
                });

        if (!file.getClientId().equals(clientId)) {
            log.error("Файл {} не принадлежит клиенту {}", fileId, clientId);
            throw new ValidationException("Файл не принадлежит указанному клиенту");
        }

        return file;
    }

    private List<Map<String, String>> processData(List<Map<String, String>> data, Long clientId) {
        ClientDataProcessor processor = clientDataProcessorFactory.getProcessor(clientId);
        ProcessingResult result = processor.processData(data, clientId);

        if (!result.isSuccess()) {
            log.error("Ошибка при обработке данных: {}", result.getErrors());
            throw new ValidationException("Ошибка при обработке данных: " +
                    String.join(", ", result.getErrors()));
        }

        return result.getProcessedData();
    }

    private List<Map<String, String>> applyFieldConfiguration(List<Map<String, String>> data, ExportConfig config) {
        return data.stream()
                .map(row -> {
                    Map<String, String> configuredRow = new LinkedHashMap<>(); // Сохраняем порядок полей

                    config.getFields().stream()
                            .filter(ExportField::isEnabled)
                            .sorted(Comparator.comparing(ExportField::getPosition))
                            .forEach(field -> {
                                String value = row.getOrDefault(field.getSourceField(), "");
                                configuredRow.put(field.getDisplayName(), value);
                            });

                    return configuredRow;
                })
                .collect(Collectors.toList());
    }
}
