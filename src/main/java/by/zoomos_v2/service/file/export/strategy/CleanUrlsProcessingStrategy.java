package by.zoomos_v2.service.file.export.strategy;

import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.repository.ClientProcessingStrategyRepository;
import by.zoomos_v2.service.file.BatchProcessingData;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class CleanUrlsProcessingStrategy implements DataProcessingStrategy {
    private static final List<String> DEFAULT_COMPETITORS = Arrays.asList(
            "auchan.ru", "lenta.com", "metro-cc.ru", "myspar.ru",
            "okeydostavka.ru", "perekrestok.ru", "winelab.ru"
    );

    private final ClientProcessingStrategyRepository strategyRepository;
    private final ObjectMapper objectMapper;

    public CleanUrlsProcessingStrategy(
            ClientProcessingStrategyRepository strategyRepository,
            ObjectMapper objectMapper) {
        this.strategyRepository = strategyRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Map<String, Object>> processData(List<Map<String, Object>> data,
                                                 ExportConfig exportConfig,
                                                 BatchProcessingData batchProcessingData) {
        log.debug("Начало обработки данных - очистка URL конкурентов");

        List<String> competitors = getCompetitorsFromConfig(exportConfig);

        for (Map<String, Object> record : data) {
            // Сначала проверяем имя конкурента
            Object competitorName = record.get("competitordata.competitorName");
            if (competitorName != null) {
                String name = competitorName.toString();
                // Если имя конкурента есть в списке
                if (competitors.stream().anyMatch(name::contains)) {
                    // Очищаем URL
                    Object webCacheUrl = record.get("competitordata.competitorWebCacheUrl");
                    if (webCacheUrl != null && !webCacheUrl.toString().isEmpty()) {
                        record.put("competitordata.competitorWebCacheUrl", "");
                        log.debug("Очищен URL для конкурента: {}", name);
                    }
                }
            }
        }
        return data;
    }

    private List<String> getCompetitorsFromConfig(ExportConfig exportConfig) {
        try {
            return strategyRepository
                    .findByClientIdAndStrategyTypeAndIsActiveTrue(
                            exportConfig.getClient().getId(),
                            getStrategyType())
                    .map(strategy -> {
                        try {
                            Map<String, Object> parameters = objectMapper.readValue(
                                    strategy.getParameters(),
                                    new TypeReference<>() {
                                    });
                            return (List<String>) parameters.getOrDefault("competitors", DEFAULT_COMPETITORS);
                        } catch (Exception e) {
                            log.error("Ошибка при чтении параметров стратегии: {}", e.getMessage());
                            return DEFAULT_COMPETITORS;
                        }
                    })
                    .orElse(DEFAULT_COMPETITORS);
        } catch (Exception e) {
            log.error("Ошибка при получении конфигурации стратегии: {}", e.getMessage());
            return DEFAULT_COMPETITORS;
        }
    }

    @Override
    public boolean supports(ExportConfig exportConfig) {
        return ProcessingStrategyType.CLEAN_URLS.equals(exportConfig.getStrategyType());
    }

    @Override
    public ProcessingStrategyType getStrategyType() {
        return ProcessingStrategyType.CLEAN_URLS;
    }

    }
