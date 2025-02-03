package by.zoomos_v2.service.file;
import by.zoomos_v2.exception.FileProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Класс для хранения статистики обработки файлов.
 * Содержит информацию о количестве обработанных записей, ошибках и дополнительной статистике.
 */
@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingData {

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
     * Путь к временному файлу с обработанными данными
     */
    private Path tempFilePath;
    /**
     * Заголовки файла
     */
    private List<String> headers;

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
    /**
     * Объединяет несколько статистик в одну с учетом временных файлов
     */
    public static ProcessingData merge(ProcessingData... stats) {
        ProcessingData result = ProcessingData.createNew();
        Path mergedTempFile = null;

        try {
            // Создаем временный файл для объединенных данных
            mergedTempFile = Files.createTempFile("merged_data_", ".tmp");

            try (BufferedWriter writer = Files.newBufferedWriter(mergedTempFile, StandardCharsets.UTF_8)) {
                // Объединяем данные из всех временных файлов
                for (ProcessingData stat : stats) {
                    if (stat == null) continue;

                    // Суммируем числовые показатели
                    result.setTotalCount(result.getTotalCount() + stat.getTotalCount());
                    result.setSuccessCount(result.getSuccessCount() + stat.getSuccessCount());
                    result.setErrorCount(result.getErrorCount() + stat.getErrorCount());
                    result.setProcessingTimeSeconds(result.getProcessingTimeSeconds() + stat.getProcessingTimeSeconds());

                    // Копируем данные из временного файла, если он есть
                    if (stat.getTempFilePath() != null && Files.exists(stat.getTempFilePath())) {
                        try (BufferedReader reader = Files.newBufferedReader(stat.getTempFilePath())) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                writer.write(line);
                                writer.newLine();
                            }
                        }
                        // Удаляем использованный временный файл
                        Files.deleteIfExists(stat.getTempFilePath());
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

                    // Сохраняем заголовки от первой статистики
                    if (result.getHeaders() == null && stat.getHeaders() != null) {
                        result.setHeaders(new ArrayList<>(stat.getHeaders()));
                    }
                }
            }

            // Устанавливаем путь к объединенному временному файлу
            result.setTempFilePath(mergedTempFile);

        } catch (IOException e) {
            cleanupTempFile(mergedTempFile);
            throw new FileProcessingException("Ошибка при объединении статистик", e);
        }

        return result;
    }

    /**
     * Очищает временные файлы при завершении работы
     */
    public void cleanup() {
        cleanupTempFile(tempFilePath);
    }
    /**
     * Безопасно удаляет временный файл
     */
    private static void cleanupTempFile(Path tempFile) {
        if (tempFile != null) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException e) {
                log.warn("Не удалось удалить временный файл: {}", tempFile, e);
            }
        }
    }
    /**
     * Сохраняет запись во временный файл
     */
    public synchronized void writeRecord(Map<String, String> record) throws IOException {
        if (tempFilePath == null) {
            tempFilePath = Files.createTempFile("processing_data_", ".tmp");
        }
        try (BufferedWriter writer = Files.newBufferedWriter(tempFilePath,
                StandardCharsets.UTF_8, StandardOpenOption.APPEND)) {
            writer.write(new ObjectMapper().writeValueAsString(record));
            writer.newLine();
        }
    }
    /**
     * Читает записи из временного файла батчами
     */
    public void processTempFileInBatches(int batchSize, Consumer<List<Map<String, String>>> batchProcessor)
            throws IOException {
        if (tempFilePath == null || !Files.exists(tempFilePath)) {
            return;
        }

        List<Map<String, String>> batch = new ArrayList<>(batchSize);
        try (BufferedReader reader = Files.newBufferedReader(tempFilePath)) {
            String line;
            ObjectMapper mapper = new ObjectMapper();
            while ((line = reader.readLine()) != null) {
                batch.add(mapper.readValue(line, new TypeReference<Map<String, String>>() {}));

                if (batch.size() >= batchSize) {
                    batchProcessor.accept(new ArrayList<>(batch));
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                batchProcessor.accept(batch);
            }
        }
    }

    /**
     * Создает новый экземпляр статистики
     */
    public static ProcessingData createNew() {
        return ProcessingData.builder()
                .totalCount(0)
                .successCount(0)
                .errorCount(0)
                .processingTimeSeconds(0)
                .errors(new ArrayList<>())
                .errorTypes(new HashMap<>())
                .additionalStats(new HashMap<>())
                .headers(new ArrayList<>())
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
