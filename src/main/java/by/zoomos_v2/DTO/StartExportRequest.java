package by.zoomos_v2.DTO;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class StartExportRequest {
    private Long fileId;
    private List<Long> fileIds;
    private Long configId;
    private String fileType;
    private Map<String, String> strategyParams;
}
