package by.zoomos_v2.service.delete;

import by.zoomos_v2.model.ClientProcessingStrategy;
import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.repository.ClientProcessingStrategyRepository;
import by.zoomos_v2.service.file.ProcessingStats;
import by.zoomos_v2.service.file.export.strategy.DataProcessingStrategy;
import by.zoomos_v2.service.file.export.strategy.ProcessingStrategyType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Стратегия обработки данных для клиента №1
 * Выполняет очистку URL конкурентов перед экспортом
 */
@Slf4j
@Component
public class Client1ProcessingStrategy implements DataProcessingStrategy {

    private static final List<String> COMPETITORS = Arrays.asList(
            "auchan.ru", "lenta.com", "metro-cc.ru", "myspar.ru",
            "okeydostavka.ru", "perekrestok.ru", "winelab.ru"
    );

    private final ClientProcessingStrategyRepository strategyRepository;
    private final ObjectMapper objectMapper;

    private static final String CLIENT_ID = "5"; // ID клиента в системе

    @Autowired
    public Client1ProcessingStrategy(
            ClientProcessingStrategyRepository strategyRepository,
            ObjectMapper objectMapper) {
        this.strategyRepository = strategyRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Map<String, Object>> processData(List<Map<String, Object>> data,
                                                 ExportConfig exportConfig,
                                                 ProcessingStats processingStats) {
        log.debug("Начало обработки данных для клиента №1");

        // Получаем параметры стратегии из БД
        ClientProcessingStrategy strategy = strategyRepository
                .findByClientIdAndStrategyTypeAndIsActiveTrue(
                        exportConfig.getClient().getId(),
                        getStrategyType())
                .orElseThrow(() -> new IllegalStateException("Strategy not found"));

        Map<String, Object> parameters;
        try {
            parameters = objectMapper.readValue(
                    strategy.getParameters(),
                    new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Error parsing strategy parameters", e);
            parameters = new HashMap<>();
        }

        // Используем параметры из БД или значения по умолчанию
        List<String> competitors = (List<String>) parameters.getOrDefault("competitors", COMPETITORS);

        data.forEach(record -> {
            Object webCacheUrl = record.get("competitorWebCacheUrl");
            if (webCacheUrl != null) {
                String url = webCacheUrl.toString();
                if (competitors.stream().anyMatch(url::contains)) {
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
        return strategyRepository
                .findByClientIdAndStrategyTypeAndIsActiveTrue(
                        exportConfig.getClient().getId(),
                        getStrategyType())
                .isPresent();
    }

    @Override
    public ProcessingStrategyType getStrategyType() {
        return ProcessingStrategyType.DEFAULT;

    }
    @Override
    public void validateParameters(Map<String, Object> parameters) {
        if (parameters != null && parameters.containsKey("competitors")) {
            Object competitors = parameters.get("competitors");
            if (!(competitors instanceof List)) {
                throw new IllegalArgumentException("Parameter 'competitors' must be a list");
            }
        }
    }
}
