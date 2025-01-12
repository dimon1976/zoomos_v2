package by.zoomos_v2.config;

public class UploadFileException extends RuntimeException{
    public UploadFileException(String message) {
        super(message);
    }

    public UploadFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
