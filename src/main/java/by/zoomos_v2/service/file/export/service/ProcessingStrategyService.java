package by.zoomos_v2.service.file.export.service;

import by.zoomos_v2.model.Client;
import by.zoomos_v2.model.ClientProcessingStrategy;
import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.repository.ClientProcessingStrategyRepository;
import by.zoomos_v2.service.file.export.strategy.DataProcessingStrategy;
import by.zoomos_v2.service.file.export.strategy.ProcessingStrategyType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessingStrategyService {

    private final List<DataProcessingStrategy> strategies;
    private final ClientProcessingStrategyRepository clientProcessingStrategyRepository;
    private final ObjectMapper objectMapper;

    public List<ProcessingStrategyType> getAvailableStrategies(Long clientId) {
        return Arrays.asList(ProcessingStrategyType.values());
    }

    // Добавляем метод получения параметров стратегии
    public Map<String, Object> getStrategyParameters(Long clientId, ProcessingStrategyType strategyType) {
        log.debug("Получение параметров стратегии {} для клиента {}", strategyType, clientId);

        return clientProcessingStrategyRepository
                .findByClientIdAndStrategyTypeAndIsActiveTrue(clientId, strategyType)
                .map(strategy -> {
                    try {
                        // Проверяем на пустые параметры
                        String params = strategy.getParameters();
                        if (params == null || params.isEmpty()) {
                            return getDefaultParameters(strategyType);
                        }
                        return objectMapper.readValue(
                                params,
                                new TypeReference<Map<String, Object>>() {}
                        );
                    } catch (JsonProcessingException e) {
                        log.error("Ошибка при чтении параметров стратегии: {}", e.getMessage(), e);
                        return getDefaultParameters(strategyType);
                    }
                })
                .orElseGet(() -> getDefaultParameters(strategyType));
    }

    // Метод для получения параметров по умолчанию для разных типов стратегий
    private Map<String, Object> getDefaultParameters(ProcessingStrategyType strategyType) {
        Map<String, Object> defaultParams = new HashMap<>();

        switch (strategyType) {
            case CLEAN_URLS:
                defaultParams.put("competitors", Arrays.asList(
                        "auchan.ru", "lenta.com", "metro-cc.ru", "myspar.ru",
                        "okeydostavka.ru", "perekrestok.ru", "winelab.ru"
                ));
                break;
            case DEFAULT:
            default:
                // Для стандартной стратегии параметры не требуются
                break;
        }

        return defaultParams;
    }

    public DataProcessingStrategy findStrategy(ExportConfig exportConfig) {
        return strategies.stream()
                .filter(strategy -> strategy.supports(exportConfig))
                .findFirst()
                .orElseGet(this::getDefaultStrategy);
    }

    private DataProcessingStrategy getDefaultStrategy() {
        return strategies.stream()
                .filter(s -> ProcessingStrategyType.DEFAULT.equals(s.getStrategyType()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Default strategy not found"));
    }

    @Transactional
    public void addStrategyToClient(Long clientId, ProcessingStrategyType strategyType,
                                    Map<String, Object> parameters) {
        log.debug("Добавление стратегии {} для клиента {}", strategyType, clientId);

        // Деактивируем текущую стратегию
        clientProcessingStrategyRepository
                .findByClientIdAndStrategyTypeAndIsActiveTrue(clientId, strategyType)
                .ifPresent(strategy -> {
                    strategy.setActive(false);
                    clientProcessingStrategyRepository.save(strategy);
                });

        // Создаем новую
        Client client = new Client();
        client.setId(clientId);

        ClientProcessingStrategy strategy = ClientProcessingStrategy.builder()
                .client(client)
                .strategyType(strategyType)
                .isActive(true)
                .parameters(writeParametersAsJson(parameters))
                .build();

        clientProcessingStrategyRepository.save(strategy);
    }

    private String writeParametersAsJson(Map<String, Object> parameters) {
        try {
            return objectMapper.writeValueAsString(parameters);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize parameters to JSON", e);
        }
    }
}
