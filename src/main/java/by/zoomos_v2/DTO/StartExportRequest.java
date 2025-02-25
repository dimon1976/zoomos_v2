package by.zoomos_v2.DTO;

import lombok.Data;

import java.util.Map;

@Data
public class StartExportRequest {
    private Long fileId;
    private Long configId;
    private String fileType;
    private Map<String, String> strategyParams;
}
