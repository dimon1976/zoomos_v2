package by.zoomos_v2.DTO.operation;

import by.zoomos_v2.model.enums.OperationType;
import by.zoomos_v2.model.enums.OperationStatus;
import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Базовый DTO класс для передачи статистики операций
 */
@Data
@SuperBuilder
public class OperationStatsDTO {
    private Long id;
    private Long clientId;
    private OperationType type;
    private OperationStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String sourceIdentifier;
    private Map<String, Object> metadata;
    private List<String> errors;
    private Integer totalRecords;
    private Integer processedRecords;
    private Integer failedRecords;
    private Long processingTimeSeconds;
    private Double successRate;
}
