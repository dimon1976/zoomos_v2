package by.zoomos_v2.controller;

import by.zoomos_v2.aspect.LogExecution;
import by.zoomos_v2.model.Client;
import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.model.ExportField;
import by.zoomos_v2.model.ExportResult;
import by.zoomos_v2.service.client.ClientService;
import by.zoomos_v2.service.file.export.service.FileExportService;
import by.zoomos_v2.service.file.export.service.ProcessingStrategyService;
import by.zoomos_v2.service.file.export.strategy.ProcessingStrategyType;
import by.zoomos_v2.service.file.metadata.FileMetadataService;
import by.zoomos_v2.service.mapping.ExportFieldConfigService;
import by.zoomos_v2.util.EntityField;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Контроллер для экспорта данных
 */
@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping
public class ExportController {

    private final FileExportService fileExportService;
    private final ExportFieldConfigService exportFieldConfigService;
    private final ProcessingStrategyService processingStrategyService;
    private final FileMetadataService fileMetadataService;
    private final ClientService clientService;
    private final ObjectMapper objectMapper;

    /**
     * Отображает страницу экспорта данных
     */
    @GetMapping("/client/{id}/export")
    @LogExecution("Просмотр страницы экспорта")
    public String showExportPage(@PathVariable Long id, Model model) {
        log.debug("Отображение страницы экспорта для клиента {}", id);
        try {
            model.addAttribute("client", clientService.getClientById(id));
            model.addAttribute("files", fileMetadataService.getFilesByClientId(id));
            model.addAttribute("configs", exportFieldConfigService.getMappingsForClient(id)
                    .orElse(Collections.emptyList()));
            return "client/export/index";
        } catch (Exception e) {
            log.error("Ошибка при загрузке страницы экспорта: {}", e.getMessage(), e);
            model.addAttribute("error", "Ошибка при загрузке страницы экспорта");
            return "error";
        }
    }

    /**
     * Отображает список настроек маппинга для экспорта
     */
    @GetMapping("/client/{id}/export-mappings")
    @LogExecution("Просмотр списка маппингов экспорта")
    public String showMappings(@PathVariable Long id, Model model) {
        log.debug("Запрошен список маппингов экспорта для магазина с ID: {}", id);
        try {
            Client client = clientService.getClientById(id);
            List<ExportConfig> mappings = exportFieldConfigService.getMappingsForClient(id)
                    .orElse(Collections.emptyList());

            model.addAttribute("client", client);
            model.addAttribute("mappings", mappings);
            return "client/export-mappings/list";
        } catch (Exception e) {
            log.error("Ошибка при получении маппингов: {}", e.getMessage(), e);
            model.addAttribute("error", "Ошибка при загрузке настроек маппинга");
            return "error";
        }
    }

    /**
     * Отображает форму создания нового маппинга
     */
    @GetMapping("/client/{id}/export-mappings/new")
    @LogExecution("Создание нового маппинга экспорта")
    public String showNewMappingForm(@PathVariable Long id, Model model) {
        log.debug("Создание нового маппинга экспорта для клиента: {}", id);
        try {
            prepareEditForm(id, null, model);
            return "client/export-mappings/edit";
        } catch (Exception e) {
            log.error("Ошибка при создании маппинга: {}", e.getMessage(), e);
            model.addAttribute("error", "Ошибка при создании маппинга");
            return "error";
        }
    }

    /**
     * Отображает форму редактирования маппинга
     */
    @GetMapping("/client/{id}/export-mappings/{mappingId}/edit")
    @LogExecution("Редактирование маппинга экспорта")
    public String editMapping(@PathVariable Long id,
                              @PathVariable Long mappingId,
                              Model model) {
        log.debug("Редактирование маппинга {} для клиента {}", mappingId, id);
        try {
            prepareEditForm(id, mappingId, model);
            return "client/export-mappings/edit";
        } catch (Exception e) {
            log.error("Ошибка при загрузке маппинга: {}", e.getMessage(), e);
            model.addAttribute("error", "Ошибка при загрузке маппинга");
            return "error";
        }
    }

    /**
     * Создает новый маппинг
     */
    @PostMapping("/client/{id}/export-mappings/create")
    @LogExecution("Сохранение нового маппинга экспорта")
    public String createMapping(@PathVariable Long id,
                                @RequestParam String positionsJson,
                                @RequestParam String configName,
                                @RequestParam String configDescription,
                                @RequestParam ProcessingStrategyType strategyType,
                                RedirectAttributes redirectAttributes) {
        log.debug("Создание нового маппинга экспорта для клиента {}", id);
        try {
            log.debug("Параметры: positionsJson={}, configName={}, description={}, strategyType={}",
                    positionsJson, configName, configDescription, strategyType);

            if (positionsJson == null || positionsJson.isEmpty()) {
                throw new IllegalArgumentException("Не указаны поля для маппинга");
            }

            List<EntityField> fields = objectMapper.readValue(positionsJson,
                    new TypeReference<>() {
                    });

            exportFieldConfigService.createConfig(id, configName, fields, configDescription, strategyType);
            redirectAttributes.addFlashAttribute("success", "Маппинг успешно создан");
        } catch (Exception e) {
            log.error("Ошибка при создании маппинга: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка при создании маппинга: " + e.getMessage());
        }
        return "redirect:/client/{id}/export-mappings";
    }

    /**
     * Обновляет существующий маппинг
     */
    @PostMapping("/client/{id}/export-mappings/{mappingId}/update")
    @LogExecution("Обновление маппинга экспорта")
    public String updateMapping(@PathVariable Long id,
                                @PathVariable Long mappingId,
                                @RequestParam String positionsJson,
                                @RequestParam String configName,
                                @RequestParam String configDescription,
                                @RequestParam ProcessingStrategyType strategyType,
                                RedirectAttributes redirectAttributes) {
        log.debug("Обновление маппинга {} для клиента {}", mappingId, id);
        try {
            List<EntityField> fields = objectMapper.readValue(positionsJson,
                    new TypeReference<List<EntityField>>() {
                    });

            exportFieldConfigService.updateFieldsConfig(
                    id,
                    fields,
                    configName,
                    mappingId,
                    configDescription,
                    strategyType
            );
            redirectAttributes.addFlashAttribute("success", "Маппинг успешно обновлен");
        } catch (Exception e) {
            log.error("Ошибка при обновлении маппинга: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка при обновлении маппинга: " + e.getMessage());
        }
        return "redirect:/client/{id}/export-mappings";
    }

    /**
     * Удаляет маппинг
     */
    @PostMapping("/client/{id}/export-mappings/{mappingId}/delete")
    @LogExecution("Удаление маппинга экспорта")
    public String deleteMapping(@PathVariable Long id,
                                @PathVariable Long mappingId,
                                RedirectAttributes redirectAttributes) {
        log.debug("Удаление маппинга {} для клиента {}", mappingId, id);
        try {
            ExportConfig config = exportFieldConfigService.getConfigById(mappingId);
            validateMappingOwnership(config, id);

            exportFieldConfigService.deleteMapping(mappingId);
            redirectAttributes.addFlashAttribute("success", "Маппинг успешно удален");
        } catch (Exception e) {
            log.error("Ошибка при удалении маппинга: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка при удалении маппинга: " + e.getMessage());
        }
        return "redirect:/client/{id}/export-mappings";
    }

    /**
     * Скачивание экспортированного файла
     */
    @GetMapping("/client/{id}/export/download/{fileId}")
    @LogExecution("Скачивание экспортированного файла")
    public ResponseEntity<Resource> downloadExportedFile(
            @PathVariable Long id,
            @PathVariable Long fileId,
            @RequestParam Long configId,
            @RequestParam String fileType) {

        log.debug("Запрос на экспорт файла. FileId: {}, ConfigId: {}, FileType: {}",
                fileId, configId, fileType);

        try {
            ExportConfig exportConfig = exportFieldConfigService.getConfigById(configId);
            ExportResult exportResult = fileExportService.exportFileData(fileId, exportConfig, fileType);

            if (!exportResult.isSuccess()) {
                log.error("Ошибка при экспорте файла: {}", exportResult.getErrorMessage());
                return ResponseEntity.badRequest().build();
            }

            String contentType = switch (fileType.toLowerCase()) {
                case "csv" -> "text/csv";
                case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                default -> "application/octet-stream";
            };

            String filename = exportResult.getFileName();
            String encodedFilename = new String(filename.getBytes(StandardCharsets.UTF_8),
                    StandardCharsets.ISO_8859_1);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.setContentDispositionFormData("attachment", encodedFilename);

            ByteArrayResource resource = new ByteArrayResource(exportResult.getFileContent());

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(exportResult.getFileContent().length)
                    .body(resource);
        } catch (Exception e) {
            log.error("Ошибка при экспорте файла: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * API для получения параметров стратегии обработки
     */
    @GetMapping("/client/{id}/export-mappings/strategy-params/{strategyType}")
    @ResponseBody
    public Map<String, Object> getStrategyParams(@PathVariable Long id,
                                                 @PathVariable ProcessingStrategyType strategyType) {
        return processingStrategyService.getStrategyParameters(id, strategyType);
    }

    /**
     * API для обновления параметров стратегии
     */
    @PostMapping("/client/{id}/export-mappings/strategy-params/{strategyType}")
    @ResponseBody
    public void updateStrategyParams(@PathVariable Long id,
                                     @PathVariable ProcessingStrategyType strategyType,
                                     @RequestBody Map<String, Object> parameters) {
        processingStrategyService.addStrategyToClient(id, strategyType, parameters);
    }

    /**
     * Получение списка поддерживаемых форматов
     */
    @GetMapping("/client/{id}/export/formats")
    @ResponseBody
    public List<String> getSupportedFormats() {
        return List.of("CSV", "XLSX");
    }

    // Вспомогательные методы
    private void prepareEditForm(Long clientId, Long mappingId, Model model) throws Exception {
        Client client = clientService.getClientById(clientId);
        model.addAttribute("client", client);

        ExportConfig config;
        if (mappingId != null) {
            config = exportFieldConfigService.getConfigById(mappingId);
            validateMappingOwnership(config, clientId);
        } else {
            config = exportFieldConfigService.createTemporaryConfig(clientId);
        }

        Map<String, List<ExportField>> groupedEnabledFields = config.getFields().stream()
                .filter(ExportField::isEnabled)
                .collect(Collectors.groupingBy(field -> field.getSourceField().split("\\.")[0]));

        Map<String, List<ExportField>> groupedDisabledFields = config.getFields().stream()
                .filter(field -> !field.isEnabled())
                .collect(Collectors.groupingBy(field -> field.getSourceField().split("\\.")[0]));

        List<ProcessingStrategyType> availableStrategies =
                processingStrategyService.getAvailableStrategies(clientId);

        model.addAttribute("mapping", config);  // Изменили с config на mapping
        model.addAttribute("strategies", availableStrategies);
        model.addAttribute("groupedEnabledFields", groupedEnabledFields);
        model.addAttribute("groupedDisabledFields", groupedDisabledFields);
    }

    private void validateMappingOwnership(ExportConfig mapping, Long clientId) {
        if (!mapping.getClient().getId().equals(clientId)) {
            throw new IllegalArgumentException("Маппинг не принадлежит указанному клиенту");
        }
    }
}
