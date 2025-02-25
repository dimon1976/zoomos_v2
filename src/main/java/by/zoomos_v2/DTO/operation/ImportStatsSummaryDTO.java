package by.zoomos_v2.DTO.operation;

import by.zoomos_v2.model.enums.OperationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ImportStatsSummaryDTO {
    private Long totalFiles;
    private Long totalRecordsProcessed;
    private Double averageSuccessRate;
    private LocalDateTime lastImportDate;
    private List<ImportOperationDTO> recentImports;
    private Map<String, Long> errorsByType;
    private Map<OperationStatus, Long> operationsByStatus;
}
