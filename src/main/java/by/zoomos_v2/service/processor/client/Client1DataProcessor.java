package by.zoomos_v2.service.processor.client;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Процессор данных для первого клиента
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Client1DataProcessor implements ClientDataProcessor{
    private final List<String> excludedSites; // инжектим через конфигурацию

    @Override
    public ProcessingResult processData(List<Map<String, String>> data, Long clientId) {
        ProcessingResult result = new ProcessingResult();
        List<Map<String, String>> processedData = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (Map<String, String> row : data) {
            try {
                Map<String, String> processedRow = new HashMap<>(row);

                // Проверяем сайт на исключение
                String site = row.get("site");
                if (site != null && excludedSites.contains(site)) {
                    processedRow.put("screenshotUrl", ""); // очищаем ссылку на скриншот
                    log.debug("Очищена ссылка на скриншот для исключенного сайта: {}", site);
                }

                processedData.add(processedRow);
            } catch (Exception e) {
                String error = "Ошибка обработки строки: " + e.getMessage();
                errors.add(error);
                log.error(error, e);
            }
        }

        result.setSuccess(errors.isEmpty());
        result.setProcessedData(processedData);
        result.setErrors(errors);

        // Добавляем статистику
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("totalRows", data.size());
        statistics.put("processedRows", processedData.size());
        statistics.put("errorCount", errors.size());
        result.setStatistics(statistics);

        return result;
    }

    @Override
    public ValidationResult validateData(List<Map<String, String>> data) {
        ValidationResult result = new ValidationResult();
        List<String> errors = new ArrayList<>();

        // Проверяем наличие обязательных полей
        for (int i = 0; i < data.size(); i++) {
            Map<String, String> row = data.get(i);

            if (!row.containsKey("site")) {
                errors.add(String.format("Строка %d: отсутствует поле 'site'", i + 1));
            }
            if (!row.containsKey("screenshotUrl")) {
                errors.add(String.format("Строка %d: отсутствует поле 'screenshotUrl'", i + 1));
            }
        }

        result.setValid(errors.isEmpty());
        result.setErrors(errors);
        return result;
    }
}
