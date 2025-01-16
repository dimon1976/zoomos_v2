package by.zoomos_v2.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Утилитарный класс для работы с путями файлов
 */
@Component
public class PathResolver {

    @Value("${app.file.upload-dir}")
    private String uploadDir;

    /**
     * Получает базовую директорию для загрузки файлов
     */
    public Path getUploadDirectory() {
        return Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    /**
     * Получает директорию для файлов конкретного магазина
     */
    public Path getClientDirectory(Long clientId) {
        return getUploadDirectory().resolve(String.valueOf(clientId));
    }

    /**
     * Получает путь к конкретному файлу
     */
    public Path getFilePath(Long clientId, String filename) {
        return getClientDirectory(clientId).resolve(filename);
    }

    /**
     * Получает временную директорию для обработки файлов
     */
    public Path getTempDirectory() {
        return getUploadDirectory().resolve("temp");
    }

    /**
     * Получает архивную директорию
     */
    public Path getArchiveDirectory() {
        return getUploadDirectory().resolve("archive");
    }

    /**
     * Получает путь для архивного файла
     */
    public Path getArchiveFilePath(Long clientId, String filename) {
        return getArchiveDirectory()
                .resolve(String.valueOf(clientId))
                .resolve(filename);
    }
}