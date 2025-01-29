package by.zoomos_v2.exception;

public class HomePageException extends RuntimeException{

    public HomePageException(String message) {
        super(message);
    }

    public HomePageException(String message, Throwable cause) {
        super(message, cause);
    }
}
