package by.zoomos_v2.service.file.input.processor;

import by.zoomos_v2.model.FileMetadata;
import by.zoomos_v2.service.file.input.callback.ProcessingProgressCallback;

import java.io.IOException;
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
     * Конфигурирует процессор
     * @param config параметры конфигурации
     */
    default void configure(Map<String, Object> config) {
        // По умолчанию ничего не делаем
    }

    /**
     * Обрабатывает файл и возвращает результаты
     */
    Map<String, Object> processFile(Path filePath, FileMetadata metadata,
                                    ProcessingProgressCallback progressCallback) throws IOException;

}