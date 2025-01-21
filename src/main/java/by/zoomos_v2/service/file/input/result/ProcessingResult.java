package by.zoomos_v2.service.file.input.result;

import by.zoomos_v2.service.file.ProcessingStats;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Универсальный класс для результатов обработки и экспорта данных
 */
@Data
@Builder
public class ProcessingResult {

    private boolean success;
    private List<String> errors;
    private List<Map<String, String>> processedData;
    private Map<String, Object> statistics;
    private ProcessingStats processingStats;

    // Дополнительные поля для экспорта
    private String fileName;
    private byte[] fileContent;


    /**
     * Создает успешный результат обработки
     */
    public static ProcessingResult success(List<Map<String, String>> data, ProcessingStats stats) {
        return ProcessingResult.builder()
                .success(true)
                .processedData(data)
                .processingStats(stats)
                .errors(new ArrayList<>())
                .build();
    }

    /**
     * Создает результат с ошибкой
     */
    public static ProcessingResult error(String error) {
        List<String> errors = new ArrayList<>();
        errors.add(error);
        return ProcessingResult.builder()
                .success(false)
                .errors(errors)
                .build();
    }

    /**
     * Создает результат с ошибками
     */
    public static ProcessingResult error(List<String> errors) {
        return ProcessingResult.builder()
                .success(false)
                .errors(errors)
                .build();
    }

    /**
     * Добавляет информацию об экспорте в результат
     */
    public ProcessingResult withExportResult(String fileName, byte[] content) {
        this.fileName = fileName;
        this.fileContent = content;
        return this;
    }
}

