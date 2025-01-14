package by.zoomos_v2.util;

import by.zoomos_v2.model.FileType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;

/**
 * Утилитарный класс для определения типа загружаемых файлов
 */
@Slf4j
@Component
public class FileTypeDetector {

    /**
     * Определяет тип файла на основе его расширения и MIME-типа
     */
    public FileType detectFileType(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String contentType = file.getContentType();

        log.debug("Определение типа файла. Имя: {}, Content-Type: {}",
                originalFilename, contentType);

        // Проверяем по расширению
        if (originalFilename != null) {
            String extension = originalFilename.toLowerCase();
            if (extension.endsWith(".csv")) {
                return FileType.CSV;
            } else if (extension.endsWith(".xlsx")) {
                return FileType.EXCEL;
            } else if (extension.endsWith(".xls")) {
                return FileType.XLS;
            }
        }

        // Проверяем по MIME-типу
        if (contentType != null) {
            return FileType.fromContentType(contentType);
        }

        log.warn("Не удалось определить тип файла: {}", originalFilename);
        return null;
    }
}
