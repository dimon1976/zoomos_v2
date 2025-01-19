package by.zoomos_v2.service.processing.client;

import by.zoomos_v2.model.Client;
import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.model.ExportField;
import by.zoomos_v2.model.FileMetadata;
import by.zoomos_v2.service.mapping.ExportConfigService;
import by.zoomos_v2.service.processing.processor.ProcessingStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Процессор данных для Client1
 */
@Slf4j
@Component
public class Client1DataProcessor implements ClientDataProcessor {
    /**
     * Список сайтов конкурентов, для которых необходимо очищать данные при обработке
     */
    private static final List<String> EXCLUDED_COMPETITORS = Arrays.asList(
            "auchan.ru",
            "lenta.com",
            "metro-cc.ru",
            "myspar.ru",
            "okeydostavka.ru",
            "perekrestok.ru",
            "winelab.ru"
    );

    @Override
    public ProcessingResult processData(List<Map<String, String>> data, ExportConfig config) {
        try {
            // Валидация данных
            ValidationResult validationResult = validateData(data);
            if (!validationResult.isValid()) {
                return ProcessingResult.error(validationResult.getErrors());
            }

            // Создаем статистику
            ProcessingStats stats = ProcessingStats.builder()
                    .totalCount(data.size())
                    .build();

            // Обрабатываем данные
            List<Map<String, String>> processedData = new ArrayList<>();
            for (Map<String, String> row : data) {
                try {
                    Map<String, String> processedRow = processRow(row, config);
                    processedData.add(processedRow);
                    stats.incrementSuccessCount();
                } catch (Exception e) {
                    log.error("Error processing row: {}", row, e);
                    stats.incrementErrorCount(e.getMessage(), "Error processing data row");
                }
            }

            return ProcessingResult.success(processedData, stats);

        } catch (Exception e) {
            log.error("Error processing data for client {}: {}", config.getClient().getName(), e.getMessage(), e);
            return ProcessingResult.error(e.getMessage());
        }
    }

    @Override
    public ValidationResult validateData(List<Map<String, String>> data) {
        ValidationResult result = new ValidationResult();
        List<String> errors = new ArrayList<>();

        try {
            if (data == null || data.isEmpty()) {
                errors.add("Отсутствуют данные для обработки");
                result.setValid(false);
                result.setErrors(errors);
                return result;
            }

            for (int i = 0; i < data.size(); i++) {
                Map<String, String> row = data.get(i);
                if (!row.containsKey("competitorName")) {
                    errors.add(String.format("Строка %d: отсутствует обязательное поле 'competitorName'", i + 1));
                }
            }

            result.setValid(errors.isEmpty());
            result.setErrors(errors);

        } catch (Exception e) {
            log.error("Error validating data: {}", e.getMessage(), e);
            errors.add("Ошибка валидации: " + e.getMessage());
            result.setValid(false);
            result.setErrors(errors);
        }

        return result;
    }

    @Override
    public void processFile(FileMetadata fileMetadata, List<Map<String, String>> data) {
        // Реализация обработки файла если нужно
    }

    @Override
    public void afterProcessing(FileMetadata metadata) {
        // Реализация пост-обработки если нужно
    }

    @Override
    public boolean supports(Client client) {
        // TODO: Добавить правильную идентификацию клиента
        return client.getId().equals(1L); // или другой способ идентификации клиента
    }

    private Map<String, String> processRow(Map<String, String> row, ExportConfig config) {
        Map<String, String> processedRow = new HashMap<>();

        // Обработка competitorName
        String competitorName = row.get("competitorName");
        if (competitorName != null && EXCLUDED_COMPETITORS.contains(competitorName.toLowerCase())) {
            competitorName = "";
            log.debug("Очищено значение конкурента: {}", competitorName);
        }

        // Применяем конфигурацию экспорта
        for (ExportField field : config.getFields()) {
            if (field.isEnabled()) {
                String value = field.getSourceField().equals("competitorName") ?
                        competitorName : row.get(field.getSourceField());
                processedRow.put(field.getDisplayName(), value != null ? value : "");
            }
        }
        return processedRow;
    }
}
