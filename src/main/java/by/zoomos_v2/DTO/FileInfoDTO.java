package by.zoomos_v2.DTO;
import by.zoomos_v2.model.FileType;
import by.zoomos_v2.model.enums.OperationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO для отображения информации о файле в dashboard
 */
@Data
@Builder
public class FileInfoDTO {
    private Long id;
    private String originalFilename;
    private FileType fileType;
    private LocalDateTime uploadedAt;
    private OperationStatus status;
    private String errorMessage;
    private Integer totalRecords;
}
