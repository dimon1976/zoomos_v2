package by.zoomos_v2.service.processor.client;

import lombok.Data;

import java.util.List;

@Data
public class ValidationResult {
    private boolean valid;
    private List<String> errors;
}
