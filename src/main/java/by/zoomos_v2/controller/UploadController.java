package by.zoomos_v2.controller;

import by.zoomos_v2.aspect.LogExecution;
import by.zoomos_v2.model.Client;
import by.zoomos_v2.model.FileMetadata;
import by.zoomos_v2.model.operation.ImportOperation;
import by.zoomos_v2.service.client.ClientService;
import by.zoomos_v2.service.file.input.service.FileProcessingService;
import by.zoomos_v2.service.file.input.service.FileUploadService;
import by.zoomos_v2.service.file.metadata.FileMetadataService;
import by.zoomos_v2.service.statistics.OperationStatsService;
import by.zoomos_v2.util.TimeUtils;
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
import java.util.Optional;

import static by.zoomos_v2.util.TimeUtils.formatDuration;

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
            Client client = clientService.getClientByName(clientName);
            model.addAttribute("client", client); // Вынесли за пределы ifPresent

            FileMetadata metadata = fileUploadService.getFileMetadata(fileId);
            validateFileOwnership(metadata, client.getId());

            // Добавляем базовую информацию о файле
            model.addAttribute("file", fileMetadataService.createFileInfo(metadata, client.getId()));

            // Добавляем информацию об операции, если она есть
            operationStatsService.findOperationByFileId(fileId).ifPresent(operation -> {
                Map<String, Object> currentProgress = (Map<String, Object>)
                        operation.getMetadata().getOrDefault("currentProgress", new HashMap<>());
                model.addAttribute("operation", operation);
                model.addAttribute("currentProgress", currentProgress);
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
        try {
            FileMetadata metadata = fileUploadService.getFileMetadata(fileId);
            validateFileOwnership(metadata, clientService.getClientByName(clientName).getId());

            Optional<ImportOperation> operationOpt = operationStatsService.findOperationByFileId(fileId);

            if (operationOpt.isPresent()) {
                ImportOperation operation = operationOpt.get();
                Map<String, Object> statistics = new HashMap<>();

                // Основные показатели
                statistics.put("totalCount", operation.getTotalRecords());
                statistics.put("successCount", operation.getProcessedRecords());
                statistics.put("errorCount", operation.getFailedRecords());

                // Форматируем время обработки
                String processingTime = operation.getProcessingTimeSeconds() != null ?
                        formatDuration(operation.getProcessingTimeSeconds()) :
                        "Нет данных";
                statistics.put("processingTime", processingTime);

                // Получаем метрики производительности и скорость обработки
                String speedFormatted = "Нет данных";
                if (operation.getMetadata().containsKey("performanceMetrics")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> performanceMetrics =
                            (Map<String, Object>) operation.getMetadata().get("performanceMetrics");

                    Object speedObj = performanceMetrics.get("recordsPerSecond");
                    if (speedObj != null) {
                        double speed = speedObj instanceof Integer ?
                                ((Integer) speedObj).doubleValue() :
                                (Double) speedObj;
                        speedFormatted = String.format("%.2f записей/сек", speed);
                    }

                    Map<String, String> additionalStats = new HashMap<>();
                    additionalStats.put("Пиковое использование памяти",
                            String.valueOf(performanceMetrics.get("peakMemoryUsage")));
                    additionalStats.put("Формат файла", operation.getFileFormat());
                    additionalStats.put("Кодировка", operation.getEncoding());
                    if (operation.getDelimiter() != null) {
                        additionalStats.put("Разделитель", operation.getDelimiter());
                    }
                    statistics.put("additionalStats", additionalStats);
                }
                statistics.put("averageSpeed", speedFormatted);

                if (!operation.getErrors().isEmpty()) {
                    statistics.put("errors", operation.getErrors());
                    statistics.put("errorTypes", operation.getErrorTypes());
                }

                model.addAttribute("statistics", statistics);
                model.addAttribute("operation", operation);
            }

            model.addAttribute("file", metadata);
            model.addAttribute("client", clientService.getClientByName(clientName));

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
