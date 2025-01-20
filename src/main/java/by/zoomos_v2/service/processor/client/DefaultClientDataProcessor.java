package by.zoomos_v2.service.processor.client;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Процессор данных по умолчанию
 * Пропускает данные без изменений
 */
@Slf4j
@Component
public class DefaultClientDataProcessor implements ClientDataProcessor{

    @Override
    public ProcessingResult processData(List<Map<String, String>> data, Long clientId) {
        ProcessingResult result = new ProcessingResult();
        result.setSuccess(true);
        result.setProcessedData(data); // возвращаем данные без изменений
        result.setErrors(new ArrayList<>()); // явно устанавливаем пустой список ошибок

        // Базовая статистика
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("totalRows", data.size());
        statistics.put("processedRows", data.size());
        statistics.put("errorCount", 0);
        result.setStatistics(statistics);

        return result;
    }

    @Override
    public ValidationResult validateData(List<Map<String, String>> data) {
        ValidationResult result = new ValidationResult();
        result.setValid(true); // считаем все данные валидными
        result.setErrors(new ArrayList<>()); // также инициализируем список ошибок
        return result;
    }
}
