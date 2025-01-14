package by.zoomos_v2.util;

import by.zoomos_v2.exception.FileProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

/**
 * Утилитарный класс для работы с файлами
 */
@Slf4j
@Component
public class FileUtils {

    /**
     * Сохраняет файл в указанную директорию
     */
    public String saveFile(MultipartFile file, Path directory) {
        try {
            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
            }

            String originalFilename = file.getOriginalFilename();
            String uniqueFilename = generateUniqueFilename(originalFilename);
            Path targetPath = directory.resolve(uniqueFilename);

            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Файл сохранен: {}", targetPath);

            return uniqueFilename;
        } catch (IOException e) {
            log.error("Ошибка при сохранении файла: {}", e.getMessage(), e);
            throw new FileProcessingException("Не удалось сохранить файл", e);
        }
    }

    /**
     * Удаляет файл
     */
    public void deleteFile(Path filePath) {
        try {
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.debug("Файл удален: {}", filePath);
            }
        } catch (IOException e) {
            log.error("Ошибка при удалении файла: {}", e.getMessage(), e);
            throw new FileProcessingException("Не удалось удалить файл", e);
        }
    }

    /**
     * Перемещает файл в другую директорию
     */
    public void moveFile(Path source, Path target) {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Файл перемещен из {} в {}", source, target);
        } catch (IOException e) {
            log.error("Ошибка при перемещении файла: {}", e.getMessage(), e);
            throw new FileProcessingException("Не удалось переместить файл", e);
        }
    }

    /**
     * Создает директорию если она не существует
     */
    public void createDirectoryIfNotExists(Path directory) {
        try {
            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
                log.debug("Создана директория: {}", directory);
            }
        } catch (IOException e) {
            log.error("Ошибка при создании директории: {}", e.getMessage(), e);
            throw new FileProcessingException("Не удалось создать директорию", e);
        }
    }

    /**
     * Генерирует уникальное имя файла
     */
    private String generateUniqueFilename(String originalFilename) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return UUID.randomUUID().toString() + extension;
    }

    /**
     * Проверяет существование файла
     */
    public boolean exists(Path path) {
        return Files.exists(path);
    }

    /**
     * Получает размер файла
     */
    public long getFileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            log.error("Ошибка при получении размера файла: {}", e.getMessage(), e);
            throw new FileProcessingException("Не удалось получить размер файла", e);
        }
    }
}