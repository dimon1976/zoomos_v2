package by.zoomos_v2.model;
import by.zoomos_v2.service.file.ProcessingData;
import lombok.Builder;
import lombok.Data;

/**
 * Класс, представляющий результат экспорта данных
 */
@Data
@Builder
public class ExportResult {
    private boolean success;
    private String errorMessage;
    private String fileName;
    private ProcessingData processingData;

    /**
     * Содержимое экспортированного файла
     */
    private byte[] fileContent;

    /**
     * Создает успешный результат экспорта
     */
    public static ExportResult success(ProcessingData stats, String fileName) {
        return ExportResult.builder()
                .success(true)
                .processingData(stats)
                .fileName(fileName)
                .build();
    }

    /**
     * Создает результат с ошибкой
     */
    public static ExportResult error(String message) {
        return ExportResult.builder()
                .success(false)
                .errorMessage(message)
                .build();
    }
}
