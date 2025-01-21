package by.zoomos_v2.service.file.export.processor;

import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.service.file.ProcessingStats;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * Абстрактный класс процессора данных в цепочке обработки.
 * Реализует паттерн Chain of Responsibility.
 */
@Slf4j
public abstract class AbstractDataProcessor {
    @Setter
    private AbstractDataProcessor nextProcessor;

    /**
     * Обрабатывает данные и передает их следующему процессору в цепочке
     *
     * @param data данные для обработки
     * @param exportConfig конфигурация экспорта
     * @param processingStats статистика обработки
     * @return обработанные данные
     */
    public List<Map<String, Object>> process(List<Map<String, Object>> data,
                                             ExportConfig exportConfig,
                                             ProcessingStats processingStats) {
        log.debug("Начало обработки данных процессором {}", this.getClass().getSimpleName());

        // Выполняем обработку в текущем процессоре
        List<Map<String, Object>> processedData = doProcess(data, exportConfig, processingStats);

        // Если есть следующий процессор, передаем ему обработанные данные
        if (nextProcessor != null) {
            return nextProcessor.process(processedData, exportConfig, processingStats);
        }

        return processedData;
    }

    /**
     * Метод для реализации конкретной логики обработки
     *
     * @param data данные для обработки
     * @param exportConfig конфигурация экспорта
     * @param processingStats статистика обработки
     * @return обработанные данные
     */
    protected abstract List<Map<String, Object>> doProcess(List<Map<String, Object>> data,
                                                           ExportConfig exportConfig,
                                                           ProcessingStats processingStats);

    /**
     * Проверяет, должен ли процессор обрабатывать данные для указанной конфигурации
     *
     * @param exportConfig конфигурация экспорта
     * @return true если процессор должен обработать данные
     */
    public abstract boolean supports(ExportConfig exportConfig);
}
