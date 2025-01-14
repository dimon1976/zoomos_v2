package by.zoomos_v2.exception;

/**
 * Исключение, выбрасываемое при ошибках обработки файлов
 */
public class FileProcessingException extends RuntimeException{
    public FileProcessingException(String message) {
        super(message);
    }

    public FileProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
