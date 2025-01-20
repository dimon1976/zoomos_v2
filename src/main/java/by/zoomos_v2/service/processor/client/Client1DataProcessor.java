package by.zoomos_v2.service.processor.client;

import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.model.ExportField;
import by.zoomos_v2.service.ExportConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class Client1DataProcessor implements ClientDataProcessor{
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

    private final ExportConfigService exportConfigService;

    @Override
    public ProcessingResult processData(List<Map<String, String>> data, Long clientId) {
        ProcessingResult result = new ProcessingResult();
        List<Map<String, String>> processedData = new ArrayList<>();

        try {
            // Сначала валидируем данные
            ValidationResult validationResult = validateData(data);
            if (!validationResult.isValid()) {
                result.setSuccess(false);
                result.setErrors(validationResult.getErrors());
                return result;
            }

            // Если валидация прошла успешно, обрабатываем данные
            ExportConfig config = exportConfigService.getConfig(clientId);

            for (Map<String, String> row : data) {
                Map<String, String> processedRow = processRow(row, config);
                processedData.add(processedRow);
            }

            result.setProcessedData(processedData);
            result.setSuccess(true);

        } catch (Exception e) {
            log.error("Ошибка обработки данных для клиента {}: {}", clientId, e.getMessage(), e);
            result.setSuccess(false);
            result.getErrors().add(e.getMessage());
        }

        return result;
    }

    @Override
    public ValidationResult validateData(List<Map<String, String>> data) {
        ValidationResult result = new ValidationResult();
        List<String> errors = new ArrayList<>();

        try {
            // Проверяем наличие данных
            if (data == null || data.isEmpty()) {
                errors.add("Отсутствуют данные для обработки");
                result.setValid(false);
                result.setErrors(errors);
                return result;
            }

            // Проверяем структуру данных
            for (int i = 0; i < data.size(); i++) {
                Map<String, String> row = data.get(i);

                // Проверяем наличие обязательного поля competitorName
                if (!row.containsKey("competitorName")) {
                    errors.add(String.format("Строка %d: отсутствует обязательное поле 'competitorName'", i + 1));
                }

                // Проверяем другие обязательные поля, если они есть
                // ...
            }

            result.setValid(errors.isEmpty());
            result.setErrors(errors);

        } catch (Exception e) {
            log.error("Ошибка валидации данных: {}", e.getMessage(), e);
            errors.add("Ошибка валидации: " + e.getMessage());
            result.setValid(false);
            result.setErrors(errors);
        }

        return result;
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
