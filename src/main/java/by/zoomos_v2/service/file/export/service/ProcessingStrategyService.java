package by.zoomos_v2.service.file.export.service;

import by.zoomos_v2.model.Client;
import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.service.file.export.strategy.DataProcessingStrategy;
import by.zoomos_v2.service.file.export.strategy.ProcessingStrategyType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessingStrategyService {

    private final List<DataProcessingStrategy> strategies;

    /**
     * Получает список доступных стратегий для клиента
     */
    public List<ProcessingStrategyType> getAvailableStrategies(Long clientId) {
        log.debug("Получение списка доступных стратегий для клиента: {}", clientId);

        // Создаем тестовый конфиг для проверки стратегий
        ExportConfig testConfig = new ExportConfig();
        Client client = new Client();
        client.setId(clientId);
        testConfig.setClient(client);

        // Фильтруем стратегии, которые поддерживают данного клиента
        return strategies.stream()
                .filter(strategy -> {
                    testConfig.setStrategyType(strategy.getStrategyType());
                    return strategy.supports(testConfig);
                })
                .map(DataProcessingStrategy::getStrategyType)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Находит подходящую стратегию для конфигурации экспорта
     */
    public DataProcessingStrategy findStrategy(ExportConfig exportConfig) {
        log.debug("Поиск стратегии для конфигурации: {}, тип стратегии: {}",
                exportConfig.getId(), exportConfig.getStrategyType());

        return strategies.stream()
                .filter(strategy -> strategy.supports(exportConfig))
                .findFirst()
                .orElseThrow(() -> {
                    log.error("Стратегия не найдена для конфигурации: {}", exportConfig.getId());
                    return new IllegalStateException(
                            String.format("Стратегия типа %s не найдена для клиента %s",
                                    exportConfig.getStrategyType(),
                                    exportConfig.getClient().getId())
                    );
                });
    }

    /**
     * Проверяет, существует ли стратегия указанного типа для клиента
     */
    public boolean isStrategyAvailable(Long clientId, ProcessingStrategyType strategyType) {
        ExportConfig testConfig = new ExportConfig();
        Client client = new Client();
        client.setId(clientId);
        testConfig.setClient(client);
        testConfig.setStrategyType(strategyType);

        return strategies.stream()
                .anyMatch(strategy -> strategy.supports(testConfig));
    }
}
