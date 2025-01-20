package by.zoomos_v2.service.processing.processor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Класс для хранения статистики обработки файлов.
 * Содержит информацию о количестве обработанных записей, ошибках и дополнительной статистике.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingStats {

    /**
     * Общее количество записей для обработки
     */
    private int totalCount;

    /**
     * Количество успешно обработанных записей
     */
    private int successCount;

    /**
     * Количество записей с ошибками
     */
    private int errorCount;

    /**
     * Время обработки в секундах
     */
    private long processingTimeSeconds;

    /**
     * Дополнительная статистика в виде пар ключ-значение
     */
    private Map<String, Object> additionalStats;

    /**
     * Список сообщений об ошибках
     */
    private List<String> errors;

    /**
     * Типы ошибок и их количество
     */
    private Map<String, Integer> errorTypes;

    /**
     * Обработанные данные
     */
    private List<Map<String, String>> processedData;

    /**
     * Объединяет несколько объектов статистики в один.
     * Суммирует числовые показатели и объединяет списки ошибок и дополнительную статистику.
     *
     * @param stats массив объектов статистики для объединения
     * @return объединенная статистика
     */
    /**
     * Объединяет несколько статистик в одну
     */
    public static ProcessingStats merge(ProcessingStats... stats) {
        ProcessingStats result = ProcessingStats.createNew();

        for (ProcessingStats stat : stats) {
            if (stat == null) continue;

            // Суммируем числовые показатели
            result.setTotalCount(result.getTotalCount() + stat.getTotalCount());
            result.setSuccessCount(result.getSuccessCount() + stat.getSuccessCount());
            result.setErrorCount(result.getErrorCount() + stat.getErrorCount());
            result.setProcessingTimeSeconds(result.getProcessingTimeSeconds() + stat.getProcessingTimeSeconds());

            // Объединяем списки данных
            if (stat.getProcessedData() != null) {
                result.getProcessedData().addAll(stat.getProcessedData());
            }

            // Объединяем ошибки
            if (stat.getErrors() != null) {
                result.getErrors().addAll(stat.getErrors());
            }

            // Объединяем типы ошибок
            if (stat.getErrorTypes() != null) {
                stat.getErrorTypes().forEach((key, value) ->
                        result.getErrorTypes().merge(key, value, Integer::sum));
            }

            // Объединяем дополнительную статистику
            if (stat.getAdditionalStats() != null) {
                result.getAdditionalStats().putAll(stat.getAdditionalStats());
            }
        }

        return result;
    }
    /**
     * Создает новый экземпляр статистики
     */
    public static ProcessingStats createNew() {
        return ProcessingStats.builder()
                .totalCount(0)
                .successCount(0)
                .errorCount(0)
                .processingTimeSeconds(0)
                .errors(new ArrayList<>())
                .errorTypes(new HashMap<>())
                .additionalStats(new HashMap<>())
                .processedData(new ArrayList<>())
                .build();
    }

    /**
     * Увеличивает счетчик успешно обработанных записей
     */
    public synchronized void incrementSuccessCount() {
        this.successCount++;
    }

    /**
     * Увеличивает счетчик ошибок и сохраняет информацию об ошибке
     */
    public synchronized void incrementErrorCount(String error, String errorType) {
        this.errorCount++;
        if (errors == null) {
            errors = new ArrayList<>();
        }
        if (errorTypes == null) {
            errorTypes = new HashMap<>();
        }
        errors.add(error);
        errorTypes.merge(errorType, 1, Integer::sum);
    }

    /**
     * Добавляет дополнительную статистику
     */
    public void addAdditionalStat(String key, Object value) {
        if (additionalStats == null) {
            additionalStats = new HashMap<>();
        }
        additionalStats.put(key, value);
    }

    /**
     * Вычисляет процент успешно обработанных записей
     */
    public double getSuccessRate() {
        if (totalCount == 0) return 0;
        return (double) successCount / totalCount * 100;
    }

    /**
     * Вычисляет процент ошибок
     */
    public double getErrorRate() {
        if (totalCount == 0) return 0;
        return (double) errorCount / totalCount * 100;
    }

    /**
     * Возвращает скорость обработки (записей в секунду)
     */
    public double getProcessingSpeed() {
        if (processingTimeSeconds == 0) return 0;
        return (double) totalCount / processingTimeSeconds;
    }

    /**
     * Обновляет время выполнения
     */
    public void updateProcessingTime(long startTimeMillis) {
        this.processingTimeSeconds = (System.currentTimeMillis() - startTimeMillis) / 1000;
    }
}
