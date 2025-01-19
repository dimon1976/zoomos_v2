package by.zoomos_v2.service.processing.client;
import by.zoomos_v2.model.Client;
import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.model.FileMetadata;
import by.zoomos_v2.service.processing.processor.ProcessingStats;
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
public class DefaultClientDataProcessor implements ClientDataProcessor {

    @Override
    public ProcessingResult processData(List<Map<String, String>> data, ExportConfig config) {
        log.debug("Processing data with default processor for client id: {}",
                config.getClient().getId());

        // Создаем статистику
        ProcessingStats stats = ProcessingStats.createNew();
        stats.setTotalCount(data.size());
        stats.setSuccessCount(data.size()); // все записи считаем успешными
        stats.setProcessedData(data);        // сохраняем данные без изменений

        // Добавляем дополнительную статистику
        stats.addAdditionalStat("clientId", config.getClient().getId());
        stats.addAdditionalStat("configName", config.getName());

        // Возвращаем результат
        return ProcessingResult.success(data, stats);
    }

    @Override
    public ValidationResult validateData(List<Map<String, String>> data) {
        ValidationResult result = new ValidationResult();
        result.setValid(true);
        result.setErrors(new ArrayList<>());
        return result;
    }

    @Override
    public void processFile(FileMetadata fileMetadata, List<Map<String, String>> data) {
        // По умолчанию ничего не делаем с файлом
    }

    @Override
    public void afterProcessing(FileMetadata metadata) {
        // По умолчанию нет пост-обработки
    }

    @Override
    public boolean supports(Client client) {
        // Этот процессор поддерживает всех клиентов
        return true;
    }

}
