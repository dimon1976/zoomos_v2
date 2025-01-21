package by.zoomos_v2.service.file.input.result;

import lombok.Data;

import java.util.List;

@Data
public class ValidationResult {
    private boolean valid;
    private List<String> errors;
}
