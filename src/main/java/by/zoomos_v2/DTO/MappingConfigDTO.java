package by.zoomos_v2.DTO;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class MappingConfigDTO {
    @NotBlank(message = "Config name is required")
    private String configName;
    private Map<String, String> mappingHeaders;
}
