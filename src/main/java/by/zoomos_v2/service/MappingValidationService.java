package by.zoomos_v2.service;

import by.zoomos_v2.exception.FileProcessingException;
import by.zoomos_v2.mapping.ClientMappingConfig;
import by.zoomos_v2.model.FileMetadata;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Сервис для валидации настроек маппинга данных.
 * Проверяет соответствие конфигурации маппинга структуре файла
 * и выполняет предварительную проверку данных.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MappingValidationService {

    private final ObjectMapper objectMapper;

    /**
     * Проверяет соответствие конфигурации маппинга заголовкам файла
     *
     * @param mapping конфигурация маппинга
     * @param fileHeaders заголовки из файла
     * @throws FileProcessingException если конфигурация не соответствует структуре файла
     */
    public void validateMappingConfig(ClientMappingConfig mapping, Set<String> fileHeaders) {
        log.debug("Проверка конфигурации маппинга для заголовков: {}", fileHeaders);

        try {
            Map<String, String> columnsMapping = objectMapper.readValue(
                    mapping.getColumnsConfig(),
                    new TypeReference<Map<String, String>>() {}
            );

            Set<String> mappingHeaders = columnsMapping.keySet();
            Set<String> missingHeaders = mappingHeaders.stream()
                    .filter(header -> !fileHeaders.contains(header))
                    .collect(Collectors.toSet());

            if (!missingHeaders.isEmpty()) {
                log.error("В файле отсутствуют необходимые колонки: {}", missingHeaders);
                throw new FileProcessingException(
                        "В файле отсутствуют следующие колонки: " + String.join(", ", missingHeaders));
            }

        } catch (Exception e) {
            log.error("Ошибка при проверке конфигурации маппинга: {}", e.getMessage(), e);
            throw new FileProcessingException("Ошибка при проверке конфигурации маппинга", e);
        }
    }

    /**
     * Проверяет обязательные поля в данных
     *
     * @param mapping конфигурация маппинга
     * @param data данные для проверки
     * @throws FileProcessingException если отсутствуют обязательные поля
     */
    public void validateRequiredFields(ClientMappingConfig mapping, List<Map<String, String>> data) {
        log.debug("Проверка обязательных полей для {} записей", data.size());

        try {
            Map<String, Boolean> requiredFields = objectMapper.readValue(
                    mapping.getColumnsConfig(),
                    new TypeReference<Map<String, Boolean>>() {}
            );

            for (int i = 0; i < data.size(); i++) {
                Map<String, String> row = data.get(i);
                for (Map.Entry<String, Boolean> field : requiredFields.entrySet()) {
                    if (field.getValue() && isEmptyOrNull(row.get(field.getKey()))) {
                        log.error("Отсутствует обязательное поле {} в строке {}", field.getKey(), i + 1);
                        throw new FileProcessingException(
                                String.format("Отсутствует обязательное поле '%s' в строке %d",
                                        field.getKey(), i + 1));
                    }
                }
            }

        } catch (Exception e) {
            log.error("Ошибка при проверке обязательных полей: {}", e.getMessage(), e);
            throw new FileProcessingException("Ошибка при проверке обязательных полей", e);
        }
    }

    /**
     * Проверяет метаданные файла на соответствие указанному маппингу
     *
     * @param metadata метаданные файла
     * @param mapping конфигурация маппинга
     * @throws FileProcessingException если тип файла не соответствует конфигурации
     */
    public void validateFileMetadata(FileMetadata metadata, ClientMappingConfig mapping) {
        log.debug("Проверка метаданных файла {} для маппинга {}",
                metadata.getOriginalFilename(), mapping.getName());

        if (!metadata.getClientId().equals(mapping.getClientId())) {
            log.error("Маппинг {} не принадлежит магазину {}",
                    mapping.getId(), metadata.getClientId());
            throw new FileProcessingException("Указанный маппинг не принадлежит магазину");
        }

        String configuredFileType = mapping.getFileType();
        if (configuredFileType != null &&
                !configuredFileType.equalsIgnoreCase(metadata.getFileType().name())) {
            log.error("Тип файла {} не соответствует настроенному в маппинге: {}",
                    metadata.getFileType(), configuredFileType);
            throw new FileProcessingException(
                    "Тип файла не соответствует настроенному в конфигурации маппинга");
        }
    }

    /**
     * Проверяет, является ли значение пустым или null
     */
    private boolean isEmptyOrNull(String value) {
        return value == null || value.trim().isEmpty();
    }
}