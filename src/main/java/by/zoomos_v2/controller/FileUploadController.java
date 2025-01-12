package by.zoomos_v2.controller;

import by.zoomos_v2.mapping.ClientMappingConfig;
import by.zoomos_v2.service.ConfigurationService;
import by.zoomos_v2.service.FileProcessorService;
import by.zoomos_v2.service.MappingConfigService;
import by.zoomos_v2.util.FileTypeDetector;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping("/shop/{clientName}")
public class FileUploadController {

    private final MappingConfigService mappingConfigService;
    private final FileProcessorService fileProcessorService;
    private final ConfigurationService configurationService;

    public FileUploadController(MappingConfigService mappingConfigService, FileProcessorService fileProcessorService, ConfigurationService configurationService) {
        this.mappingConfigService = mappingConfigService;
        this.fileProcessorService = fileProcessorService;
        this.configurationService = configurationService;
    }

    @PostMapping("/upload-file")
    public String uploadFile(@PathVariable String clientName,
                             @RequestParam("file") MultipartFile file,
                             @RequestParam("configId") Long configId,
                             Model model) {
        // Проверка наличия файла
        if (file.isEmpty()) {
            model.addAttribute("error", "Файл не выбран.");
            return "redirect:/shop/" + clientName + "/upload"; // Возвращаемся на страницу настроек
        }

        // Определяем тип файла
        String fileType = FileTypeDetector.detectFileType(file.getOriginalFilename());
        if (fileType == null) {
            model.addAttribute("error", "Неподдерживаемый тип файла.");
            return "redirect:/shop/" + clientName + "/upload";
        }

        // Получаем настройки маппинга
//        try {
//            ClientMappingConfig config = mappingConfigService.getConfigurationById(configId);
//            if (config == null) {
//                model.addAttribute("error", "Настройка маппинга не найдена.");
//                return "redirect:/shop/" + clientName + "/upload";
//            }
//
//            // Обработка файла
////            fileProcessorService.processFile(file, fileType, config);
//
//            model.addAttribute("success", "Файл успешно загружен и обработан.");
//        } catch (Exception e) {
//            model.addAttribute("error", "Ошибка обработки файла: " + e.getMessage());
//        }

        return "redirect:/shop/" + clientName + "/upload";
    }
}
