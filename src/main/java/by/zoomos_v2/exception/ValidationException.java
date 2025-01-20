package by.zoomos_v2.exception;

/**
 * Исключение при валидации данных
 */
public class ValidationException extends RuntimeException{
    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
