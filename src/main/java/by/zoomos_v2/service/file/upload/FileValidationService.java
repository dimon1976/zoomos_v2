package by.zoomos_v2.service.file.upload;

import by.zoomos_v2.exception.FileProcessingException;
import by.zoomos_v2.model.FileType;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Сервис для валидации загружаемых файлов
 */
@Slf4j
@Service
public class FileValidationService {

    private final Set<String> ALLOWED_CONTENT_TYPES = new HashSet<>(Arrays.asList(
            "text/csv",
            "application/csv",
            "text/plain",
            "text/x-csv",
            "application/x-csv",
            "application/octet-stream",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "application/x-tika-ooxml"
    ));

    private final Tika tika = new Tika();

    /**
     * Проверяет файл на соответствие требованиям
     */
    public void validateFile(MultipartFile file) {
        log.debug("Валидация файла: {}", file.getOriginalFilename());

        // Проверка на пустой файл
        if (file.isEmpty()) {
            throw new FileProcessingException("Файл пуст");
        }

        // Проверка размера файла
        if (file.getSize() > 600 * 1024 * 1024) { // 10 MB
            throw new FileProcessingException("Размер файла превышает 600 MB");
        }

        // Проверка типа файла
        String detectedType = detectContentType(file);
        if (!ALLOWED_CONTENT_TYPES.contains(detectedType)) {
            throw new FileProcessingException("Неподдерживаемый тип файла. Поддерживаются только CSV и Excel файлы");
        }
    }

    /**
     * Определяет тип содержимого файла
     */
    private String detectContentType(MultipartFile file) {
        try {
            byte[] fileBytes = file.getBytes();
            return tika.detect(fileBytes);
        } catch (IOException e) {
            log.error("Ошибка при определении типа файла: {}", e.getMessage(), e);
            throw new FileProcessingException("Не удалось определить тип файла", e);
        }
    }

    /**
     * Проверяет соответствие файла указанному типу
     */
    public boolean isFileTypeValid(MultipartFile file, FileType expectedType) {
        String detectedType = detectContentType(file);
        return expectedType.matches(detectedType);
    }

    /**
     * Проверяет заголовки CSV файла
     */
    public void validateCsvHeaders(Set<String> actualHeaders, Set<String> requiredHeaders) {
        if (!actualHeaders.containsAll(requiredHeaders)) {
            Set<String> missingHeaders = new HashSet<>(requiredHeaders);
            missingHeaders.removeAll(actualHeaders);
            throw new FileProcessingException(
                    "Отсутствуют обязательные заголовки: " + String.join(", ", missingHeaders));
        }
    }
}