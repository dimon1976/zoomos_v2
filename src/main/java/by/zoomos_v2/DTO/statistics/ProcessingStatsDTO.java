package by.zoomos_v2.DTO.statistics;

import by.zoomos_v2.model.operation.BaseOperation;
import by.zoomos_v2.service.file.input.service.FileProcessingService;
import lombok.Builder;
import lombok.Data;

import java.util.Map;
/**
 * DTO для отображения статистики обработки файла
 */
@Data
@Builder
public class ProcessingStatsDTO {
    private String status;
    private int progress;
    private String message;
    private Integer totalRecords;
    private Integer processedRecords;
    private Double processingSpeed;

    public static ProcessingStatsDTO fromOperation(BaseOperation operation, String currentStatus) {
        return ProcessingStatsDTO.builder()
                .status(operation.getStatus().name())
                .progress(calculateProgress(operation))
                .message(currentStatus)
                .totalRecords(operation.getTotalRecords())
                .processedRecords(operation.getProcessedRecords())
                .processingSpeed(operation.getProcessingSpeed())
                .build();
    }
    private static int calculateProgress(BaseOperation operation) {
        if (operation.getTotalRecords() == null || operation.getTotalRecords() == 0) {
            return 0;
        }
        return (int) ((operation.getProcessedRecords() * 100.0) / operation.getTotalRecords());
    }
}
