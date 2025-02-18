package by.zoomos_v2.service.file.export.strategy;

import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.service.file.BatchProcessingData;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Интерфейс стратегии обработки данных перед экспортом.
 * Каждая конкретная реализация будет определять специфическую логику обработки данных для клиента.
 */
public interface DataProcessingStrategy {

    /**
     * Обрабатывает данные в соответствии со стратегией
     *
     * @param data                исходные данные для обработки
     * @param exportConfig        конфигурация экспорта
     * @param batchProcessingData статистика обработки
     * @return обработанные данные
     */
    List<Map<String, Object>> processData(List<Map<String, Object>> data,
                                          ExportConfig exportConfig,
                                          BatchProcessingData batchProcessingData);

    /**
     * Проверяет, подходит ли данная стратегия для указанной конфигурации
     *
     * @param exportConfig конфигурация экспорта
     * @return true если стратегия применима
     */
    boolean supports(ExportConfig exportConfig);

    /**
     * Возвращает тип стратегии
     */
    ProcessingStrategyType getStrategyType();

    /**
     * Возвращает список требуемых параметров
     */
    default Set<String> getRequiredParameters() {
        return Set.of();
    }

    /**
     * Валидирует параметры стратегии
     *
     * @throws IllegalArgumentException если параметры невалидны
     */
    default void validateParameters(ExportConfig exportConfig) {
        Set<String> required = getRequiredParameters();
        for (String param : required) {
            if (exportConfig.getParam(param) == null) {
                throw new IllegalArgumentException(
                        String.format("Отсутствует обязательный параметр: %s", param)
                );
            }
        }
    }
}
