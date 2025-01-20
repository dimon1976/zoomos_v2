package by.zoomos_v2.service.processor;
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
    public static ProcessingStats merge(ProcessingStats... stats) {
        ProcessingStatsBuilder builder = ProcessingStats.builder()
                .totalCount(0)
                .successCount(0)
                .errorCount(0)
                .processingTimeSeconds(0)
                .additionalStats(new HashMap<>())
                .errors(new ArrayList<>())
                .errorTypes(new HashMap<>());
//                .processedData(new ArrayList<>());

        for (ProcessingStats stat : stats) {
            if (stat == null) continue;

            builder.totalCount(builder.totalCount + stat.totalCount);
            builder.successCount(builder.successCount + stat.successCount);
            builder.errorCount(builder.errorCount + stat.errorCount);
            builder.processingTimeSeconds(builder.processingTimeSeconds + stat.processingTimeSeconds);

            if (stat.additionalStats != null) {
                builder.additionalStats.putAll(stat.additionalStats);
            }

            if (stat.errors != null) {
                builder.errors.addAll(stat.errors);
            }

            if (stat.errorTypes != null) {
                stat.errorTypes.forEach((key, value) ->
                        builder.errorTypes.merge(key, value, Integer::sum));
            }

//            if (stat.processedData != null) {
//                builder.processedData.addAll(stat.processedData);
//            }
        }

        return builder.build();
    }

    /**
     * Добавляет информацию об ошибке в статистику
     *
     * @param error сообщение об ошибке
     * @param errorType тип ошибки
     */
    public void addError(String error, String errorType) {
        if (errors == null) {
            errors = new ArrayList<>();
        }
        if (errorTypes == null) {
            errorTypes = new HashMap<>();
        }

        errors.add(error);
        errorTypes.merge(errorType, 1, Integer::sum);
        errorCount++;
    }

    /**
     * Добавляет дополнительную статистику
     *
     * @param key ключ статистики
     * @param value значение статистики
     */
    public void addAdditionalStat(String key, Object value) {
        if (additionalStats == null) {
            additionalStats = new HashMap<>();
        }
        additionalStats.put(key, value);
    }

    /**
     * Вычисляет скорость обработки (записей в секунду)
     *
     * @return скорость обработки или 0, если время обработки равно 0
     */
    public double getProcessingSpeed() {
        return processingTimeSeconds > 0 ?
                (double) totalCount / processingTimeSeconds : 0;
    }
}
