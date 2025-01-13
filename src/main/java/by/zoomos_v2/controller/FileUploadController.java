package by.zoomos_v2.controller;

import by.zoomos_v2.mapping.ClientMappingConfig;
import by.zoomos_v2.service.ClientService;
import by.zoomos_v2.service.ConfigurationService;
import by.zoomos_v2.service.FileProcessingService;
import by.zoomos_v2.service.MappingConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/shop/{clientName}")
public class FileUploadController {

    private final MappingConfigService mappingConfigService;
    private final FileProcessingService fileProcessingService;
    private final ConfigurationService configurationService;
    private final ClientService clientService;

    public FileUploadController(MappingConfigService mappingConfigService, FileProcessingService fileProcessingService, ConfigurationService configurationService, ClientService clientService) {
        this.mappingConfigService = mappingConfigService;
        this.fileProcessingService = fileProcessingService;
        this.configurationService = configurationService;
        this.clientService = clientService;
    }

    @PostMapping("/upload-file")
    public String uploadFile(
            @PathVariable String clientName,
            @RequestParam("file") MultipartFile file,
            @RequestParam("configId") Long configId,
            RedirectAttributes redirectAttributes) {

        String baseRedirectUrl = "redirect:/shop/" + clientName + "/upload";

        try {
            // Базовая валидация
            if (file.isEmpty()) {
                throw new IllegalArgumentException("Файл не выбран");
            }

            // Проверяем расширение файла
            String filename = file.getOriginalFilename();
            if (filename == null || !isSupportedFileType(filename)) {
                throw new IllegalArgumentException("Неподдерживаемый тип файла");
            }

            // Проверяем размер файла (например, максимум 10MB)
            if (file.getSize() > 10_000_000) {
                throw new IllegalArgumentException("Файл слишком большой. Максимальный размер: 10MB");
            }

            // Получаем конфигурацию маппинга
            ClientMappingConfig mappingConfig = mappingConfigService.getConfigById(configId);

            // Обрабатываем файл и получаем данные
            List<Map<String, String>> processedData = fileProcessingService.readFile(file, configId);
            Long clientId = clientService.getClientIdByName(clientName);

            // Валидируем полученные данные
            validateProcessedData(processedData);

            // Сохраняем данные
            fileProcessingService.saveData(processedData, clientId, configId);

            // Добавляем сообщение об успехе
            redirectAttributes.addFlashAttribute("success",
                    String.format("Файл успешно обработан. Загружено %d записей.", processedData.size()));

            return baseRedirectUrl;

        } catch (IllegalArgumentException e) {
            // Ошибки валидации
            log.warn("Validation error during file upload: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return baseRedirectUrl;

        } catch (IOException e) {
            // Ошибки чтения файла
            log.error("Error reading uploaded file", e);
            redirectAttributes.addFlashAttribute("error", "Ошибка чтения файла: " + e.getMessage());
            return baseRedirectUrl;

        } catch (Exception e) {
            // Прочие ошибки
            log.error("Unexpected error during file processing", e);
            redirectAttributes.addFlashAttribute("error", "Произошла непредвиденная ошибка при обработке файла");
            return baseRedirectUrl;
        }
    }

    private boolean isSupportedFileType(String filename) {
        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        return Arrays.asList("csv", "xlsx", "xls", "txt").contains(extension);
    }

    private void validateProcessedData(List<Map<String, String>> data) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("В файле отсутствуют данные для обработки");
        }

        // Проверяем первую строку на наличие всех необходимых полей
        Map<String, String> firstRow = data.get(0);
        if (firstRow.isEmpty()) {
            throw new IllegalArgumentException("Не удалось сопоставить данные с настройками маппинга");
        }
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxSizeException(
            MaxUploadSizeExceededException e,
            RedirectAttributes redirectAttributes,
            @PathVariable String clientName) {

        redirectAttributes.addFlashAttribute("error",
                "Файл слишком большой. Пожалуйста, загрузите файл меньшего размера");
        return "redirect:/shop/" + clientName + "/upload";
    }
}
