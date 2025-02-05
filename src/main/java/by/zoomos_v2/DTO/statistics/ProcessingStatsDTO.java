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
    private int progress;
    private String message;
    private String status;
    private Integer totalRecords;
    private Integer processedRecords;
    private Double processingSpeed;
    private Map<String, Object> metadata;

    public static ProcessingStatsDTO fromOperation(BaseOperation operation, FileProcessingService.ProcessingStatus currentStatus) {
        return ProcessingStatsDTO.builder()
                .progress(currentStatus.getProgress())
                .message(currentStatus.getMessage())
                .status(operation.getStatus().name())
                .totalRecords(operation.getTotalRecords())
                .processedRecords(operation.getProcessedRecords())
                .processingSpeed(operation.getProcessingSpeed())
                .metadata(operation.getMetadata())
                .build();
    }
}
