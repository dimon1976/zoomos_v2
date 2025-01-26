package by.zoomos_v2.exception;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ValidationError {
    private final String code;
    private final String message;
    private final Integer row;
    private final String field;

    public ValidationError(String code, String message, Integer row, String field) {
        this.code = code;
        this.message = message;
        this.row = row;
        this.field = field;
    }
}
