package by.zoomos_v2.controller;

import by.zoomos_v2.model.FileMetadata;
import by.zoomos_v2.service.FileProcessingService;
import by.zoomos_v2.service.FileUploadService;
import by.zoomos_v2.aspect.LogExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Контроллер для обработки загрузки и обработки файлов.
 * Обеспечивает функционал загрузки файлов и их последующей обработки
 * в соответствии с настройками маппинга.
 */
@Slf4j
@Controller
@RequestMapping("/shops/{shopId}/files")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileUploadService fileUploadService;
    private final FileProcessingService fileProcessingService;

    /**
     * Отображает страницу загрузки файлов
     */
    @GetMapping("/upload")
    public String showUploadForm(@PathVariable Long shopId, Model model) {
        log.debug("Отображение формы загрузки файлов для магазина: {}", shopId);
        model.addAttribute("shopId", shopId);
        model.addAttribute("recentFiles", fileUploadService.getRecentFiles(shopId));
        return "files/upload";
    }

    /**
     * Обрабатывает загрузку файла
     */
    @PostMapping("/upload")
    @LogExecution("Загрузка файла")
    public String handleFileUpload(@PathVariable Long shopId,
                                   @RequestParam("file") MultipartFile file,
                                   @RequestParam(value = "mappingId", required = false) Long mappingId,
                                   RedirectAttributes redirectAttributes) {
        log.debug("Загрузка файла {} для магазина {}", file.getOriginalFilename(), shopId);

        try {
            FileMetadata metadata = fileUploadService.uploadFile(file, shopId, mappingId);
            log.info("Файл {} успешно загружен, ID: {}", file.getOriginalFilename(), metadata.getId());

            // Начинаем асинхронную обработку файла
            fileProcessingService.processFileAsync(metadata.getId());

            redirectAttributes.addFlashAttribute("success",
                    "Файл успешно загружен и поставлен в очередь на обработку");
            return "redirect:/shops/{shopId}/files/status/" + metadata.getId();
        } catch (Exception e) {
            log.error("Ошибка при загрузке файла: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка при загрузке файла: " + e.getMessage());
            return "redirect:/shops/{shopId}/files/upload";
        }
    }

    /**
     * Отображает статус обработки файла
     */
    @GetMapping("/status/{fileId}")
    public String showFileStatus(@PathVariable Long shopId,
                                 @PathVariable Long fileId,
                                 Model model) {
        log.debug("Проверка статуса файла {} для магазина {}", fileId, shopId);

        try {
            FileMetadata metadata = fileUploadService.getFileMetadata(fileId);

            // Проверяем, принадлежит ли файл магазину
            if (!metadata.getShopId().equals(shopId)) {
                throw new IllegalArgumentException("Файл не принадлежит указанному магазину");
            }

            model.addAttribute("file", metadata);
            model.addAttribute("processingStatus",
                    fileProcessingService.getProcessingStatus(fileId));
            return "files/status";
        } catch (Exception e) {
            log.error("Ошибка при получении статуса файла: {}", e.getMessage(), e);
            model.addAttribute("error", "Ошибка при получении статуса файла");
            return "error";
        }
    }

    /**
     * Отменяет обработку файла
     */
    @PostMapping("/status/{fileId}/cancel")
    @LogExecution("Отмена обработки файла")
    public String cancelProcessing(@PathVariable Long shopId,
                                   @PathVariable Long fileId,
                                   RedirectAttributes redirectAttributes) {
        log.debug("Отмена обработки файла {} для магазина {}", fileId, shopId);

        try {
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
     * Удаляет файл
     */
    @PostMapping("/delete/{fileId}")
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
        return "redirect:/shops/{shopId}/files/upload";
    }
}