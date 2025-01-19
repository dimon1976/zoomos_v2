package by.zoomos_v2.exception;


import by.zoomos_v2.config.UploadFileException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Глобальный обработчик исключений приложения
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {


    /**
     * Обрабатывает ошибки маппинга данных
     */
    @ExceptionHandler(MappingException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleMappingException(MappingException ex) {
        log.error("Mapping error occurred: {}", ex.getMessage(), ex);
        return buildErrorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    /**
     * Обрабатывает ошибки загрузки файлов
     */
    @ExceptionHandler(UploadFileException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleUploadFileException(UploadFileException ex) {
        log.error("File upload error: {}", ex.getMessage(), ex);
        return buildErrorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    /**
     * Обрабатывает ошибки превышения размера файла
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex) {
        String message = String.format("Файл слишком большой. Максимальный размер: %d МБ",
                ex.getMaxUploadSize() / (1024 * 1024));
        log.error(message, ex);
        return buildErrorResponse(message, HttpStatus.BAD_REQUEST);
    }

    /**
     * Обрабатывает ошибки валидации
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> String.format("%s: %s", error.getField(), error.getDefaultMessage()))
                .collect(Collectors.toList());

        String message = String.format("Ошибка валидации данных: %s",
                String.join("; ", errors));
        log.error(message, ex);
        return buildErrorResponse(message, HttpStatus.BAD_REQUEST);
    }

    /**
     * Обрабатывает все остальные исключения
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred: {}", ex.getMessage(), ex);
        return buildErrorResponse("Внутренняя ошибка сервера", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(String message, HttpStatus status) {
        ErrorResponse errorResponse = new ErrorResponse(
                message,
                LocalDateTime.now(),
                status.value()
        );
        return new ResponseEntity<>(errorResponse, status);
    }
}

@Data
@AllArgsConstructor
class ErrorResponse {
    private String message;
    private LocalDateTime timestamp;
    private int status;
}

