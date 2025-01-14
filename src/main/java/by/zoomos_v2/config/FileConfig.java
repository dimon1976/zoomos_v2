package by.zoomos_v2.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Конфигурация для работы с файлами.
 * Настраивает пути для хранения файлов и доступ к ним.
 */
@Configuration
public class FileConfig implements WebMvcConfigurer {

    @Value("${app.file.upload-dir}")
    private String uploadDir;

    @Value("${app.file.temp-dir:temp}")
    private String tempDir;

    @Value("${app.file.archive-dir:archive}")
    private String archiveDir;

    /**
     * Инициализирует необходимые директории при запуске приложения
     */
    @PostConstruct
    public void init() {
        try {
            // Создаем основные директории если они не существуют
            Files.createDirectories(getUploadPath());
            Files.createDirectories(getTempPath());
            Files.createDirectories(getArchivePath());
        } catch (Exception e) {
            throw new RuntimeException("Не удалось создать директории для файлов", e);
        }
    }

    /**
     * Получает путь к директории для загрузки файлов
     */
    public Path getUploadPath() {
        return Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    /**
     * Получает путь к временной директории
     */
    public Path getTempPath() {
        return getUploadPath().resolve(tempDir);
    }

    /**
     * Получает путь к архивной директории
     */
    public Path getArchivePath() {
        return getUploadPath().resolve(archiveDir);
    }

    /**
     * Конфигурирует обработку статических ресурсов
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadPath = getUploadPath().toString();
        // Добавляем обработчик для доступа к загруженным файлам
        registry.addResourceHandler("/files/**")
                .addResourceLocations("file:" + uploadPath + "/");
    }
}