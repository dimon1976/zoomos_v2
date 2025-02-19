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
     * Возвращает описание параметров стратегии
     */
    default List<StrategyParameterDescriptor> getParameterDescriptors() {
        return List.of();
    }

    /**
     * Возвращает список требуемых параметров
     */
    default Set<String> getRequiredParameters() {
        return Set.of();
    }

    /**
     * Валидирует параметры стратегии
     * @throws IllegalArgumentException если параметры невалидны
     */
    default void validateParameters(Map<String, String> parameters) {
        List<StrategyParameterDescriptor> descriptors = getParameterDescriptors();

        // Проверяем каждый параметр
        for (StrategyParameterDescriptor descriptor : descriptors) {
            String value = parameters.get(descriptor.getKey());
            descriptor.validate(value);
        }
    }
}
