package by.zoomos_v2.exception;

/**
 * Исключение при обработке файлов
 */
public class FileProcessingException extends RuntimeException{
    public FileProcessingException(String message) {
        super(message);
    }

    public FileProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
