package by.zoomos_v2.service.file.export.strategy;

import by.zoomos_v2.model.Client;
import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.service.file.ProcessingStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Стратегия обработки данных для клиента №1
 * Выполняет очистку URL конкурентов перед экспортом
 */
@Slf4j
@Component
public class Client1ProcessingStrategy implements DataProcessingStrategy{

    private static final List<String> COMPETITORS = Arrays.asList(
            "auchan.ru", "lenta.com", "metro-cc.ru", "myspar.ru",
            "okeydostavka.ru", "perekrestok.ru", "winelab.ru"
    );

    private static final String CLIENT_ID = "1"; // ID клиента в системе

    @Override
    public List<Map<String, Object>> processData(List<Map<String, Object>> data,
                                                 ExportConfig exportConfig,
                                                 ProcessingStats processingStats) {
        log.debug("Начало обработки данных для клиента №1");

        data.forEach(record -> {
            // Получаем значение URL из записи
            Object webCacheUrl = record.get("competitorWebCacheUrl");
            if (webCacheUrl != null) {
                String url = webCacheUrl.toString();
                // Проверяем, содержит ли URL любое из значений списка конкурентов
                if (COMPETITORS.stream().anyMatch(url::contains)) {
                    // Очищаем URL
                    record.put("competitorWebCacheUrl", "");
                    processingStats.incrementSuccessCount();
                }
            }
        });

        log.info("Обработка данных для клиента №1 завершена. Обработано записей: {}",
                processingStats.getSuccessCount());
        return data;
    }

    @Override
    public boolean supports(ExportConfig exportConfig) {
        if (exportConfig == null || exportConfig.getClient() == null) {
            return false;
        }
        Client client = exportConfig.getClient();
        return CLIENT_ID.equals(client.getId().toString());
    }
}
