package by.zoomos_v2.DTO.operation;

import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
public class ImportOperationDTO extends OperationStatsDTO {
    private String fileName;
    private Long fileSize;
    private String fileFormat;
    private Double processingSpeed;
    private String encoding;
    private String delimiter;
}
