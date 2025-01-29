package by.zoomos_v2.exception;

public class TabDataException extends RuntimeException{
    public TabDataException(String message) {
        super(message);
    }

    public TabDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
