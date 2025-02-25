package by.zoomos_v2.service.file.export.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Сервис для управления стратегиями обработки данных.
 * Предоставляет централизованный доступ к стратегиям и их параметрам.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyManager {

    private final List<DataProcessingStrategy> strategies;

    /**
     * Получает стратегию по типу
     */
    public DataProcessingStrategy getStrategy(ProcessingStrategyType type) {
        if (type == null) {
            throw new IllegalArgumentException("Тип стратегии не может быть null");
        }
        
        log.debug("Поиск стратегии для типа: {}. Доступные стратегии: {}", type, 
            strategies.stream().map(DataProcessingStrategy::getStrategyType).collect(Collectors.toList()));
        
        return strategies.stream()
                .filter(strategy -> type.equals(strategy.getStrategyType()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Стратегия не найдена: " + type));
    }

    /**
     * Получает список всех доступных типов стратегий
     */
    public List<ProcessingStrategyType> getAvailableStrategyTypes() {
        return strategies.stream()
                .map(DataProcessingStrategy::getStrategyType)
                .toList();
    }

    /**
     * Получает требуемые параметры для стратегии
     */
    public Set<String> getRequiredParameters(ProcessingStrategyType type) {
        return getStrategy(type).getRequiredParameters();

    }

    /**
     * Валидирует параметры стратегии
     * @throws IllegalArgumentException если параметры невалидны
     */
    public void validateStrategyParameters(ProcessingStrategyType type, Map<String, String> parameters) {
        DataProcessingStrategy strategy = getStrategy(type);
        Set<String> required = strategy.getRequiredParameters();

        for (String param : required) {
            if (!parameters.containsKey(param) || parameters.get(param) == null || parameters.get(param).isEmpty()) {
                log.error("Отсутствует обязательный параметр {} для стратегии {}", param, type);
                throw new IllegalArgumentException(
                        String.format("Отсутствует обязательный параметр: %s", param)
                );
            }
        }

        log.debug("Параметры стратегии {} успешно провалидированы", type);
    }
}
