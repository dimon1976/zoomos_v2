package by.zoomos_v2.service.statistics.dashboard;

import by.zoomos_v2.model.enums.OperationStatus;
import by.zoomos_v2.model.enums.OperationType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class ClientDashboardStatsDTO {
    private long totalFiles;
    private long totalSizeBytes;
    private String formattedTotalSize;
    private long recentFilesCount; // за последние 2 недели
    private long activeOperationsCount;
    private Map<OperationStatus, Long> operationsByStatus;
    private Map<OperationType, Long> operationsByType;
    private double overallSuccessRate;
    private LocalDateTime lastOperationDate;
}
