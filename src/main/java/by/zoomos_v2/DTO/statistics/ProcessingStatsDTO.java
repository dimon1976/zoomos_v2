package by.zoomos_v2.DTO.statistics;

import by.zoomos_v2.model.enums.OperationStatus;
import by.zoomos_v2.model.operation.BaseOperation;
import by.zoomos_v2.service.file.input.service.FileProcessingService;
import lombok.Builder;
import lombok.Data;

import java.time.temporal.ChronoUnit;
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

//    public static ProcessingStatsDTO fromOperation(BaseOperation operation, String currentStatus) {
//        return ProcessingStatsDTO.builder()
//                .status(operation.getStatus().name())
//                .progress(calculateProgress(operation))
//                .message(currentStatus)
//                .totalRecords(operation.getTotalRecords())
//                .processedRecords(operation.getProcessedRecords())
//                .processingSpeed(operation.getProcessingSpeed())
//                .build();
//    }

    public static ProcessingStatsDTO fromOperation(BaseOperation operation, String message) {
        Double speed = operation.getProcessingSpeed();
        if (speed == null && operation.getProcessedRecords() != null &&
                operation.getStartTime() != null && operation.getEndTime() != null) {
            // Если скорость не установлена, но есть все данные для расчета
            long seconds = ChronoUnit.SECONDS.between(operation.getStartTime(), operation.getEndTime());
            if (seconds > 0) {
                speed = operation.getProcessedRecords().doubleValue() / seconds;
            }
        }

        // Гарантируем, что прогресс будет 100% для завершенных операций
        int progress = operation.getStatus() == OperationStatus.COMPLETED ? 100 :
                calculateProgress(operation);

        return ProcessingStatsDTO.builder()
                .status(operation.getStatus().name())
                .progress(progress)
                .message(message)
                .totalRecords(operation.getTotalRecords())
                .processedRecords(operation.getProcessedRecords())
                .processingSpeed(speed)
                .build();
    }


    private static int calculateProgress(BaseOperation operation) {
        if (operation.getTotalRecords() == null || operation.getTotalRecords() == 0) {
            return 0;
        }
        return (int) ((operation.getProcessedRecords() * 100.0) / operation.getTotalRecords());
    }
}
