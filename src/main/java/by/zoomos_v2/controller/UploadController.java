package by.zoomos_v2.controller;

import by.zoomos_v2.aspect.LogExecution;
import by.zoomos_v2.model.FileMetadata;
import by.zoomos_v2.model.operation.ImportOperation;
import by.zoomos_v2.service.client.ClientService;
import by.zoomos_v2.service.file.input.service.FileProcessingService;
import by.zoomos_v2.service.file.input.service.FileUploadService;
import by.zoomos_v2.service.file.metadata.FileMetadataService;
import by.zoomos_v2.service.statistics.OperationStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Контроллер для управления операциями с файлами
 */
@Slf4j
@Controller
@RequestMapping("/client/{clientName}/files")
@RequiredArgsConstructor
public class UploadController {
    private final FileUploadService fileUploadService;
    private final FileProcessingService fileProcessingService;
    private final OperationStatsService operationStatsService;
    private final ClientService clientService;
    private final FileMetadataService fileMetadataService;


    /**
     * Загрузка файла
     */
    @PostMapping("/upload")
    @LogExecution("Загрузка файла")
    public String uploadFile(@PathVariable String clientName,
                             @RequestParam("file") MultipartFile file,
                             @RequestParam(required = false) Long mappingId,
                             RedirectAttributes redirectAttributes) {
        log.debug("Загрузка файла {} для магазина {}", file.getOriginalFilename(), clientName);

        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Выберите файл для загрузки");
            return "redirect:/client/" + clientName + "/dashboard";
        }

        try {
            FileMetadata metadata = fileUploadService.uploadFile(file, clientService.getClientByName(clientName).getId(), mappingId);
            fileProcessingService.processFileAsync(metadata.getId());

            redirectAttributes.addFlashAttribute("success",
                    "Файл успешно загружен и поставлен в очередь на обработку");
            return "redirect:/client/" + clientName + "/files/status/" + metadata.getId();
        } catch (Exception e) {
            log.error("Ошибка при загрузке файла: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка при загрузке файла: " + e.getMessage());
            return "redirect:/client/" + clientName + "/dashboard";
        }
    }

    /**
     * Страница статуса обработки файла
     */
    @GetMapping("/status/{fileId}")
    public String showFileStatus(@PathVariable String clientName,
                                 @PathVariable Long fileId,
                                 Model model) {
        log.debug("Просмотр статуса файла {} для магазина {}", fileId, clientName);

        try {
            FileMetadata metadata = fileUploadService.getFileMetadata(fileId);
            validateFileOwnership(metadata, clientService.getClientByName(clientName).getId());

            operationStatsService.findOperationByFileId(fileId).ifPresent(operation -> {
                Map<String, Object> currentProgress =
                        (Map<String, Object>) operation.getMetadata().getOrDefault("currentProgress", new HashMap<>());

                model.addAttribute("file", fileMetadataService.createFileInfo(metadata, clientService.getClientByName(clientName).getId()));
                model.addAttribute("operation", operation);
                model.addAttribute("currentProgress", currentProgress);
                model.addAttribute("clientId", clientService.getClientByName(clientName).getId());
                model.addAttribute("client", clientService.getClientByName(clientName));
            });

            return "files/status";
        } catch (Exception e) {
            log.error("Ошибка при получении статуса файла: {}", e.getMessage(), e);
            model.addAttribute("error", "Ошибка при получении информации о файле");
            return "error";
        }
    }

    /**
     * Страница статистики обработки файла
     */
    @GetMapping("/{fileId}/statistics")
    public String showFileStatistics(@PathVariable String clientName,
                                     @PathVariable Long fileId,
                                     Model model) {
        log.debug("Запрошена статистика обработки файла {} для клиента {}", fileId, clientName);

        try {
            FileMetadata metadata = fileUploadService.getFileMetadata(fileId);
            validateFileOwnership(metadata, clientService.getClientByName(clientName).getId());

            // Получаем операцию импорта для данного файла
            ImportOperation operation = operationStatsService.findLastOperationBySourceAndClient(
                    metadata.getOriginalFilename(),
                    clientService.getClientByName(clientName).getId()
            );

            if (operation != null) {
                // Базовая статистика
                Map<String, Object> statistics = new HashMap<>();
                statistics.put("totalRecords", operation.getTotalRecords());
                statistics.put("processedRecords", operation.getProcessedRecords());
                statistics.put("failedRecords", operation.getFailedRecords());
                statistics.put("processingTimeSeconds", operation.getProcessingTimeSeconds());
                statistics.put("processingSpeed", operation.getProcessingSpeed());

                // Статистика ошибок
                if (!operation.getErrorTypes().isEmpty()) {
                    statistics.put("errorTypes", operation.getErrorTypes());
                }

                // Расчет времени обработки
                if (operation.getStartTime() != null && operation.getEndTime() != null) {
                    Duration duration = Duration.between(operation.getStartTime(), operation.getEndTime());
                    statistics.put("processingDuration", duration.getSeconds());
                }

                // Добавляем метаданные операции
                if (operation.getMetadata() != null && !operation.getMetadata().isEmpty()) {
                    // Фильтруем и преобразуем метаданные для отображения
                    Map<String, Object> displayMetadata = new HashMap<>();

                    // Добавляем метрики производительности
                    if (operation.getMetadata().containsKey("performanceMetrics")) {
                        displayMetadata.put("performance", operation.getMetadata().get("performanceMetrics"));
                    }

                    // Добавляем прогресс обработки
                    if (operation.getMetadata().containsKey("progressMetrics")) {
                        displayMetadata.put("progress", operation.getMetadata().get("progressMetrics"));
                    }

                    // Добавляем конфигурацию обработки
                    if (operation.getMetadata().containsKey("processorConfig")) {
                        displayMetadata.put("configuration", operation.getMetadata().get("processorConfig"));
                    }

                    statistics.put("metadata", displayMetadata);
                }

                model.addAttribute("statistics", statistics);
                model.addAttribute("operation", operation);
            }

            model.addAttribute("file", metadata);
            model.addAttribute("clientId", clientService.getClientByName(clientName).getId());

            return "files/statistics";
        } catch (Exception e) {
            log.error("Ошибка при получении статистики обработки файла: {}", e.getMessage(), e);
            model.addAttribute("error", "Ошибка при получении статистики: " + e.getMessage());
            return "error";
        }
    }

    /**
     * Отмена обработки файла
     */
    @PostMapping("/status/{fileId}/cancel")
    @LogExecution("Отмена обработки файла")
    public String cancelProcessing(@PathVariable String clientName,
                                   @PathVariable Long fileId,
                                   RedirectAttributes redirectAttributes) {
        log.debug("Отмена обработки файла {} для магазина {}", fileId, clientName);

        try {
            FileMetadata metadata = fileUploadService.getFileMetadata(fileId);
            validateFileOwnership(metadata, clientService.getClientByName(clientName).getId());

            fileProcessingService.cancelProcessing(fileId);
            redirectAttributes.addFlashAttribute("success", "Обработка файла отменена");
        } catch (Exception e) {
            log.error("Ошибка при отмене обработки файла: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка при отмене обработки файла: " + e.getMessage());
        }

        return "redirect:/client/" + clientName + "/files/status/" + fileId;
    }

    /**
     * Удаление файла
     */
    @PostMapping("/{fileId}/delete")
    @LogExecution("Удаление файла")
    public String deleteFile(@PathVariable String clientName,
                             @PathVariable Long fileId,
                             RedirectAttributes redirectAttributes) {
        log.debug("Удаление файла {} для магазина {}", fileId, clientName);

        try {
            fileUploadService.deleteFile(fileId, clientService.getClientByName(clientName).getId());
            redirectAttributes.addFlashAttribute("success", "Файл успешно удален");
        } catch (Exception e) {
            log.error("Ошибка при удалении файла: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка при удалении файла: " + e.getMessage());
        }

        return "redirect:/client/" + clientName + "/dashboard";
    }

    /**
     * Проверка принадлежности файла клиенту
     */
    private void validateFileOwnership(FileMetadata metadata, Long clientId) {
        if (!metadata.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("Файл не принадлежит указанному магазину");
        }
    }
}
