package by.zoomos_v2.service.file.export.strategy;

import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.service.file.ProcessingStats;

import java.util.List;
import java.util.Map;

/**
 * Интерфейс стратегии обработки данных перед экспортом.
 * Каждая конкретная реализация будет определять специфическую логику обработки данных для клиента.
 */
public interface DataProcessingStrategy {

    /**
     * Обрабатывает данные в соответствии со стратегией
     *
     * @param data исходные данные для обработки
     * @param exportConfig конфигурация экспорта
     * @param processingStats статистика обработки
     * @return обработанные данные
     */
    List<Map<String, Object>> processData(List<Map<String, Object>> data,
                                          ExportConfig exportConfig,
                                          ProcessingStats processingStats);

    /**
     * Проверяет, подходит ли данная стратегия для указанной конфигурации
     *
     * @param exportConfig конфигурация экспорта
     * @return true если стратегия применима
     */
    boolean supports(ExportConfig exportConfig);
}
