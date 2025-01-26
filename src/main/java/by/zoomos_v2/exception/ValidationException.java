package by.zoomos_v2.exception;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Исключение при валидации данных
 */
@Getter
public class ValidationException extends RuntimeException{
    private final List<ValidationError> errors;

    public ValidationException(String message) {
        super(message);
        this.errors = new ArrayList<>();
    }

    public ValidationException(String message, List<ValidationError> errors) {
        super(message);
        this.errors = errors;
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
        this.errors = new ArrayList<>();
    }

}
