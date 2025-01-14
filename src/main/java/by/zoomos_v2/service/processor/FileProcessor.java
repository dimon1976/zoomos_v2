package by.zoomos_v2.service.processor;

import by.zoomos_v2.model.FileMetadata;
import java.nio.file.Path;
import java.util.Map;

/**
 * Интерфейс для обработчиков различных типов файлов
 */
public interface FileProcessor {
    /**
     * Проверяет, поддерживает ли процессор данный тип файла
     */
    boolean supports(FileMetadata fileMetadata);

    /**
     * Обрабатывает файл и возвращает результаты
     */
    Map<String, Object> processFile(Path filePath, FileMetadata metadata,
                                    ProcessingProgressCallback progressCallback);
}