package by.zoomos_v2.controller;
import by.zoomos_v2.model.FileMetadata;
import by.zoomos_v2.repository.FileMetadataRepository;
import by.zoomos_v2.service.FileUploadService;
import by.zoomos_v2.service.FileProcessingService;
import by.zoomos_v2.service.MappingConfigService;
import by.zoomos_v2.aspect.LogExecution;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;

/**
 * Контроллер для управления файлами в интерфейсе магазина
 */
@Slf4j
@Controller
@RequestMapping("/shops/{shopId}/files")
@RequiredArgsConstructor
public class FileController {

    private final FileUploadService fileUploadService;
    private final FileProcessingService fileProcessingService;
    private final MappingConfigService mappingConfigService;
    private final FileMetadataRepository fileMetadataRepository;

    /**
     * Страница загрузки файлов
     */
    @GetMapping
    public String showFileUploadPage(@PathVariable Long shopId, Model model) {
        log.debug("Отображение страницы загрузки файлов для магазина {}", shopId);

        model.addAttribute("files", fileUploadService.getRecentFiles(shopId));
        model.addAttribute("mappings", mappingConfigService.getMappingsForClient(shopId));
        return "files/upload";
    }

    /**
     * Отображает страницу со статистикой обработки файла.
     *
     * @param shopId идентификатор магазина
     * @param fileId идентификатор файла
     * @param model модель представления
     * @return имя представления
     */
    @GetMapping("/{fileId}/statistics")
    public String showFileStatistics(@PathVariable Long shopId,
                                     @PathVariable Long fileId,
                                     Model model) {
        log.debug("Запрошена статистика обработки файла {} для магазина {}", fileId, shopId);

        try {
            FileMetadata metadata = fileMetadataRepository.findById(fileId)
                    .orElseThrow(() -> new EntityNotFoundException("Файл не найден"));

            if (!metadata.getShopId().equals(shopId)) {
                throw new IllegalArgumentException("Файл не принадлежит указанному магазину");
            }

            model.addAttribute("shopId", shopId);
            model.addAttribute("file", metadata);

            return "files/statistics";
        } catch (Exception e) {
            log.error("Ошибка при получении статистики обработки файла: {}", e.getMessage(), e);
            model.addAttribute("error", "Ошибка при получении статистики обработки файла");
            return "error";
        }
    }

    /**
     * Обработка загрузки файла
     */
    @PostMapping("/upload")
    @LogExecution("Загрузка файла")
    public String handleFileUpload(@PathVariable Long shopId,
                                   @RequestParam("file") MultipartFile file,
                                   @RequestParam(required = false) Long mappingId,
                                   RedirectAttributes redirectAttributes) {
        log.debug("Загрузка файла {} для магазина {}", file.getOriginalFilename(), shopId);

        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Выберите файл для загрузки");
            return "redirect:/shops/{shopId}/files";
        }

        try {
            FileMetadata metadata = fileUploadService.uploadFile(file, shopId, mappingId);
            fileProcessingService.processFileAsync(metadata.getId());

            redirectAttributes.addFlashAttribute("success",
                    "Файл успешно загружен и поставлен в очередь на обработку");
            return "redirect:/shops/{shopId}/files/status/" + metadata.getId();
        } catch (Exception e) {
            log.error("Ошибка при загрузке файла: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка при загрузке файла: " + e.getMessage());
            return "redirect:/shops/{shopId}/files";
        }
    }

    /**
     * Страница со статусом обработки файла
     */
    @GetMapping("/status/{fileId}")
    public String showFileStatus(@PathVariable Long shopId,
                                 @PathVariable Long fileId,
                                 Model model) {
        log.debug("Просмотр статуса файла {} для магазина {}", fileId, shopId);

        try {
            FileMetadata metadata = fileUploadService.getFileMetadata(fileId);
            if (!metadata.getShopId().equals(shopId)) {
                throw new IllegalArgumentException("Файл не принадлежит указанному магазину");
            }

            model.addAttribute("file", metadata);
            model.addAttribute("processingStatus", fileProcessingService.getProcessingStatus(fileId));
            model.addAttribute("shopId", shopId);

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
    public String cancelProcessing(@PathVariable Long shopId,
                                   @PathVariable Long fileId,
                                   RedirectAttributes redirectAttributes) {
        log.debug("Отмена обработки файла {} для магазина {}", fileId, shopId);

        try {
            FileMetadata metadata = fileUploadService.getFileMetadata(fileId);
            if (!metadata.getShopId().equals(shopId)) {
                throw new IllegalArgumentException("Файл не принадлежит указанному магазину");
            }

            fileProcessingService.cancelProcessing(fileId);
            redirectAttributes.addFlashAttribute("success", "Обработка файла отменена");
        } catch (Exception e) {
            log.error("Ошибка при отмене обработки файла: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка при отмене обработки файла: " + e.getMessage());
        }

        return "redirect:/shops/{shopId}/files/status/" + fileId;
    }

    /**
     * Удаление файла
     */
    @PostMapping("/{fileId}/delete")
    @LogExecution("Удаление файла")
    public String deleteFile(@PathVariable Long shopId,
                             @PathVariable Long fileId,
                             RedirectAttributes redirectAttributes) {
        log.debug("Удаление файла {} для магазина {}", fileId, shopId);

        try {
            fileUploadService.deleteFile(fileId, shopId);
            redirectAttributes.addFlashAttribute("success", "Файл успешно удален");
        } catch (Exception e) {
            log.error("Ошибка при удалении файла: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка при удалении файла: " + e.getMessage());
        }

        return "redirect:/shops/{shopId}/files";
    }

    /**
     * Получение результатов обработки файла
     */
    @GetMapping("/{fileId}/results")
    public String showFileResults(@PathVariable Long shopId,
                                  @PathVariable Long fileId,
                                  Model model) {
        log.debug("Просмотр результатов обработки файла {} для магазина {}", fileId, shopId);

        try {
            FileMetadata metadata = fileUploadService.getFileMetadata(fileId);
            if (!metadata.getShopId().equals(shopId)) {
                throw new IllegalArgumentException("Файл не принадлежит указанному магазину");
            }

            model.addAttribute("file", metadata);
            model.addAttribute("shopId", shopId);
            return "files/results";

        } catch (Exception e) {
            log.error("Ошибка при получении результатов обработки: {}", e.getMessage(), e);
            model.addAttribute("error", "Ошибка при получении результатов обработки");
            return "error";
        }
    }
}
