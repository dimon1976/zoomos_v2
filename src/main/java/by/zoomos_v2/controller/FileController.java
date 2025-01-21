package by.zoomos_v2.controller;

import by.zoomos_v2.model.FileMetadata;
import by.zoomos_v2.repository.FileMetadataRepository;
import by.zoomos_v2.service.file.input.service.FileUploadService;
import by.zoomos_v2.service.file.input.service.FileProcessingService;
import by.zoomos_v2.service.mapping.MappingConfigService;
import by.zoomos_v2.aspect.LogExecution;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.fasterxml.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.Map;

/**
 * Контроллер для управления файлами в интерфейсе магазина
 */
@Slf4j
@Controller
@RequestMapping("/client/{clientId}/files")
@RequiredArgsConstructor
public class FileController {

    private final FileUploadService fileUploadService;
    private final FileProcessingService fileProcessingService;
    private final MappingConfigService mappingConfigService;
    private final FileMetadataRepository fileMetadataRepository;
    private final ObjectMapper objectMapper;


    /**
     * Страница загрузки файлов
     */
    @GetMapping
    public String showFileUploadPage(@PathVariable Long clientId, Model model) {
        log.debug("Отображение страницы загрузки файлов для магазина {}", clientId);

        model.addAttribute("files", fileUploadService.getRecentFiles(clientId));
        model.addAttribute("mappings", mappingConfigService.getMappingsForClient(clientId));
        return "files/upload";
    }

    /**
     * Отображает страницу со статистикой обработки файла.
     *
     * @param clientId идентификатор магазина
     * @param fileId   идентификатор файла
     * @param model    модель представления
     * @return имя представления
     */
    @GetMapping("/{fileId}/statistics")
    public String showFileStatistics(@PathVariable Long clientId,
                                     @PathVariable Long fileId,
                                     Model model) {
        log.debug("Запрошена статистика обработки файла {} для клиента {}", fileId, clientId);

        try {
            FileMetadata metadata = fileMetadataRepository.findById(fileId)
                    .orElseThrow(() -> new EntityNotFoundException("Файл не найден"));

            if (!metadata.getClientId().equals(clientId)) {
                throw new IllegalArgumentException("Файл не принадлежит указанному клиенту");
            }
            // Рассчитываем разницу во времени обработки
            if (metadata.getProcessingStartedAt() != null && metadata.getProcessingCompletedAt() != null) {
                Duration duration = Duration.between(metadata.getProcessingStartedAt(), metadata.getProcessingCompletedAt());
                model.addAttribute("processingDuration", duration.getSeconds()); // разница в секундах
            }
            model.addAttribute("clientId", clientId);
            model.addAttribute("file", metadata);
            // Читаем статистику из JSON
            if (metadata.getProcessingResults() != null) {
                Map<String, Object> statistics = objectMapper.readValue(
                        metadata.getProcessingResults(),
                        new TypeReference<>() {
                        }
                );

                log.debug("Loaded statistics: {}", statistics); // добавляем лог
                model.addAttribute("statistics", statistics);

            }

            return "files/statistics";
        } catch (Exception e) {
            log.error("Ошибка при получении статистики обработки файла: {}", e.getMessage(), e);
            return "error";
        }
    }

    /**
     * Обработка загрузки файла
     */
    @PostMapping("/upload")
    @LogExecution("Загрузка файла")
    public String handleFileUpload(@PathVariable Long clientId,
                                   @RequestParam("file") MultipartFile file,
                                   @RequestParam(required = false) Long mappingId,
                                   RedirectAttributes redirectAttributes) {
        log.debug("Загрузка файла {} для магазина {}", file.getOriginalFilename(), clientId);

        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Выберите файл для загрузки");
            return "redirect:/client/{clientId}/files";
        }

        try {
            FileMetadata metadata = fileUploadService.uploadFile(file, clientId, mappingId);
            fileProcessingService.processFileAsync(metadata.getId());

            redirectAttributes.addFlashAttribute("success",
                    "Файл успешно загружен и поставлен в очередь на обработку");
            return "redirect:/client/{clientId}/files/status/" + metadata.getId();
        } catch (Exception e) {
            log.error("Ошибка при загрузке файла: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка при загрузке файла: " + e.getMessage());
            return "redirect:/client/{clientId}/files";
        }
    }

    /**
     * Страница со статусом обработки файла
     */
    @GetMapping("/status/{fileId}")
    public String showFileStatus(@PathVariable Long clientId,
                                 @PathVariable Long fileId,
                                 Model model) {
        log.debug("Просмотр статуса файла {} для магазина {}", fileId, clientId);

        try {
            FileMetadata metadata = fileUploadService.getFileMetadata(fileId);
            if (!metadata.getClientId().equals(clientId)) {
                throw new IllegalArgumentException("Файл не принадлежит указанному магазину");
            }

            model.addAttribute("file", metadata);
            model.addAttribute("processingStatus", fileProcessingService.getProcessingStatus(fileId));
            model.addAttribute("clientId", clientId);

            return "files/status";
        } catch (Exception e) {
            log.error("Ошибка при получении статуса файла: {}", e.getMessage(), e);
            model.addAttribute("error", "Ошибка при получении информации о файле");
            return "error";
        }
    }

    /**
     * Отмена обработки файла
     */
    @PostMapping("/status/{fileId}/cancel")
    @LogExecution("Отмена обработки файла")
    public String cancelProcessing(@PathVariable Long clientId,
                                   @PathVariable Long fileId,
                                   RedirectAttributes redirectAttributes) {
        log.debug("Отмена обработки файла {} для магазина {}", fileId, clientId);

        try {
            FileMetadata metadata = fileUploadService.getFileMetadata(fileId);
            if (!metadata.getClientId().equals(clientId)) {
                throw new IllegalArgumentException("Файл не принадлежит указанному магазину");
            }

            fileProcessingService.cancelProcessing(fileId);
            redirectAttributes.addFlashAttribute("success", "Обработка файла отменена");
        } catch (Exception e) {
            log.error("Ошибка при отмене обработки файла: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка при отмене обработки файла: " + e.getMessage());
        }

        return "redirect:/client/{clientId}/files/status/" + fileId;
    }

    /**
     * Удаление файла
     */
    @PostMapping("/{fileId}/delete")
    @LogExecution("Удаление файла")
    public String deleteFile(@PathVariable Long clientId,
                             @PathVariable Long fileId,
                             RedirectAttributes redirectAttributes) {
        log.debug("Удаление файла {} для магазина {}", fileId, clientId);

        try {
            fileUploadService.deleteFile(fileId, clientId);
            redirectAttributes.addFlashAttribute("success", "Файл успешно удален");
        } catch (Exception e) {
            log.error("Ошибка при удалении файла: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка при удалении файла: " + e.getMessage());
        }

        return "redirect:/client/{clientId}/files";
    }

    /**
     * Получение результатов обработки файла
     */
    @GetMapping("/{fileId}/results")
    public String showFileResults(@PathVariable Long clientId,
                                  @PathVariable Long fileId,
                                  Model model) {
        log.debug("Просмотр результатов обработки файла {} для магазина {}", fileId, clientId);

        try {
            FileMetadata metadata = fileUploadService.getFileMetadata(fileId);
            if (!metadata.getClientId().equals(clientId)) {
                throw new IllegalArgumentException("Файл не принадлежит указанному магазину");
            }

            model.addAttribute("file", metadata);
            model.addAttribute("clientId", clientId);
            return "files/results";

        } catch (Exception e) {
            log.error("Ошибка при получении результатов обработки: {}", e.getMessage(), e);
            model.addAttribute("error", "Ошибка при получении результатов обработки");
            return "error";
        }
    }
}
