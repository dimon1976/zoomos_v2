package by.zoomos_v2.DTO.operation;

import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
public class ExportOperationDTO extends OperationStatsDTO {
    private String exportFormat;
    private String targetPath;
    private Integer filesGenerated;
    private String processingStrategy;
}