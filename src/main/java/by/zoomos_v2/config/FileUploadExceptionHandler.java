package by.zoomos_v2.config;

import by.zoomos_v2.exception.FileProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Глобальный обработчик исключений для обработки ошибок при работе с файлами
 */
@Slf4j
@ControllerAdvice
public class FileUploadExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxSizeException(MaxUploadSizeExceededException e,
                                         RedirectAttributes redirectAttributes) {
        log.error("Превышен максимальный размер файла", e);
        redirectAttributes.addFlashAttribute("error",
                "Файл слишком большой. Максимальный размер: 10MB");
        return "redirect:/error";
    }

    @ExceptionHandler(FileProcessingException.class)
    public String handleFileProcessingException(FileProcessingException e,
                                                RedirectAttributes redirectAttributes) {
        log.error("Ошибка обработки файла: {}", e.getMessage(), e);
        redirectAttributes.addFlashAttribute("error", "Ошибка обработки файла: " + e.getMessage());
        return "redirect:/error";
    }

    @ExceptionHandler(Exception.class)
    public String handleGenericException(Exception e,
                                         RedirectAttributes redirectAttributes) {
        log.error("Непредвиденная ошибка при обработке файла: {}", e.getMessage(), e);
        redirectAttributes.addFlashAttribute("error", "Произошла ошибка при обработке запроса");
        return "redirect:/error";
    }
}
