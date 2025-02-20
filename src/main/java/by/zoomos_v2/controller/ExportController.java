package by.zoomos_v2.controller;

import by.zoomos_v2.DTO.StartExportRequest;
import by.zoomos_v2.aspect.LogExecution;
import by.zoomos_v2.model.Client;
import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.model.ExportField;
import by.zoomos_v2.model.ExportResult;
import by.zoomos_v2.model.operation.ExportOperation;
import by.zoomos_v2.service.client.ClientService;
import by.zoomos_v2.service.file.export.service.FileExportService;
import by.zoomos_v2.service.file.export.service.ProcessingStrategyService;
import by.zoomos_v2.service.file.export.strategy.ProcessingStrategyType;
import by.zoomos_v2.service.file.export.strategy.StrategyManager;
import by.zoomos_v2.service.file.metadata.FileMetadataService;
import by.zoomos_v2.service.mapping.ExportFieldConfigService;
import by.zoomos_v2.service.statistics.OperationStatsService;
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
import java.util.*;
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
    private final OperationStatsService operationStatsService;
    private final StrategyManager strategyManager;
    private final FileMetadataService fileMetadataService;
    private final ClientService clientService;
    private final ObjectMapper objectMapper;

    /**
     * Отображает страницу экспорта данных
     */
    @GetMapping("/client/{clientName}/export")
    @LogExecution("Просмотр страницы экспорта")
    public String showExportPage(@PathVariable String clientName, Model model) {
        log.debug("Отображение страницы экспорта для клиента {}", clientName);
        try {
            model.addAttribute("client", clientService.getClientByName(clientName));
            model.addAttribute("files", fileMetadataService.getFilesByClientId(clientService.getClientByName(clientName).getId()));
            model.addAttribute("configs", exportFieldConfigService.getMappingsForClient(clientService.getClientByName(clientName).getId())
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
    @GetMapping("/client/{clientName}/export-mappings")
    @LogExecution("Просмотр списка маппингов экспорта")
    public String showMappings(@PathVariable String clientName, Model model) {
        log.debug("Запрошен список маппингов экспорта для магазина с Name: {}", clientName);
        try {
            Client client = clientService.getClientByName(clientName);
            List<ExportConfig> mappings = exportFieldConfigService.getMappingsForClient(clientService.getClientByName(clientName).getId())
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
    @GetMapping("/client/{clientName}/export-mappings/new")
    @LogExecution("Создание нового маппинга экспорта")
    public String showNewMappingForm(@PathVariable String clientName, Model model) {
        log.debug("Создание нового маппинга экспорта для клиента: {}", clientName);
        try {
            prepareEditForm(clientService.getClientByName(clientName).getId(), null, model);
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
    @GetMapping("/client/{clientName}/export-mappings/{mappingId}/edit")
    @LogExecution("Редактирование маппинга экспорта")
    public String editMapping(@PathVariable String clientName,
                              @PathVariable Long mappingId,
                              Model model) {
        log.debug("Редактирование маппинга {} для клиента {}", mappingId, clientName);
        try {
            prepareEditForm(clientService.getClientByName(clientName).getId(), mappingId, model);
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
    @PostMapping("/client/{clientName}/export-mappings/create")
    @LogExecution("Сохранение нового маппинга экспорта")
    public String createMapping(@PathVariable String clientName,
                                @RequestParam String positionsJson,
                                @RequestParam String configName,
                                @RequestParam String configDescription,
                                @RequestParam ProcessingStrategyType strategyType,
                                RedirectAttributes redirectAttributes) {
        log.debug("Создание нового маппинга экспорта для клиента {}", clientName);
        try {
            log.debug("Параметры: positionsJson={}, configName={}, description={}, strategyType={}",
                    positionsJson, configName, configDescription, strategyType);

            if (positionsJson == null || positionsJson.isEmpty()) {
                throw new IllegalArgumentException("Не указаны поля для маппинга");
            }

            List<EntityField> fields = objectMapper.readValue(positionsJson,
                    new TypeReference<>() {
                    });

            exportFieldConfigService.createConfig(clientService.getClientByName(clientName).getId(), configName, fields, configDescription, strategyType);
            redirectAttributes.addFlashAttribute("success", "Маппинг успешно создан");
        } catch (Exception e) {
            log.error("Ошибка при создании маппинга: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка при создании маппинга: " + e.getMessage());
        }
        return "redirect:/client/{clientName}/export-mappings";
    }

    /**
     * Обновляет существующий маппинг
     */
    @PostMapping("/client/{clientName}/export-mappings/{mappingId}/update")
    @LogExecution("Обновление маппинга экспорта")
    public String updateMapping(@PathVariable String clientName,
                                @PathVariable Long mappingId,
                                @RequestParam String positionsJson,
                                @RequestParam String configName,
                                @RequestParam String configDescription,
                                @RequestParam ProcessingStrategyType strategyType,
                                RedirectAttributes redirectAttributes) {
        log.debug("Обновление маппинга {} для клиента {}", mappingId, clientName);
        try {
            List<EntityField> fields = objectMapper.readValue(positionsJson,
                    new TypeReference<>() {
                    });

            exportFieldConfigService.updateFieldsConfig(
                    clientService.getClientByName(clientName).getId(),
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
        return "redirect:/client/{clientName}/export-mappings";
    }

    /**
     * Удаляет маппинг
     */
    @PostMapping("/client/{clientName}/export-mappings/{mappingId}/delete")
    @LogExecution("Удаление маппинга экспорта")
    public String deleteMapping(@PathVariable String clientName,
                                @PathVariable Long mappingId,
                                RedirectAttributes redirectAttributes) {
        log.debug("Удаление маппинга {} для клиента {}", mappingId, clientName);
        try {
            ExportConfig config = exportFieldConfigService.getConfigById(mappingId);
            validateMappingOwnership(config, clientService.getClientByName(clientName).getId());

            exportFieldConfigService.deleteMapping(mappingId);
            redirectAttributes.addFlashAttribute("success", "Маппинг успешно удален");
        } catch (Exception e) {
            log.error("Ошибка при удалении маппинга: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка при удалении маппинга: " + e.getMessage());
        }
        return "redirect:/client/{clientName}/export-mappings";
    }

    /**
     * Начало асинхронного экспорта
     */
    @PostMapping("/client/{clientName}/export/start")
    @ResponseBody
    public Map<String, Object> startExport(@PathVariable String clientName,
                                           @RequestBody StartExportRequest request) {
        log.debug("Запуск экспорта. FileId: {}, ConfigId: {}, FileType: {}",
                request.getFileId(), request.getConfigId(), request.getFileType());

        try {
            // Получаем конфигурацию
            ExportConfig config = exportFieldConfigService.getConfigById(request.getConfigId());

            // Устанавливаем параметры стратегии
            if (request.getStrategyParams() != null) {
                config.setParams(request.getStrategyParams());
            }

            // Запускаем экспорт
//            ExportOperation operation = fileExportService.startExport(
//                    request.getFileId(),
//                    config,
//                    request.getFileType()
//            );

            return Map.of(
//                    "operationId", operation.getId(),
                    "status", "started"
            );

        } catch (Exception e) {
            log.error("Ошибка при запуске экспорта: {}", e.getMessage(), e);
            return Map.of(
                    "error", e.getMessage(),
                    "status", "error"
            );
        }
    }

    /**
     * Получение статуса операции экспорта
     */
    @GetMapping("/api/operations/{operationId}/status")
    @ResponseBody
    public Map<String, Object> getOperationStatus(@PathVariable Long operationId) {
        try {
            ExportOperation operation = operationStatsService.findOperation(operationId)
                    .filter(op -> op instanceof ExportOperation)
                    .map(op -> (ExportOperation) op)
                    .orElseThrow(() -> new IllegalArgumentException("Операция не найдена или имеет неверный тип"));


            return Map.of(
                    "status", operation.getStatus().name(),
                    "progress", operation.getCurrentProgress(),
                    "message", operation.getMetadata().getOrDefault("statusMessage", ""),
                    "processed", operation.getProcessedRecords(),
                    "total", operation.getTotalRecords()
            );

        } catch (Exception e) {
            log.error("Ошибка при получении статуса операции: {}", e.getMessage(), e);
            return Map.of(
                    "status", "ERROR",
                    "message", "Ошибка: " + e.getMessage()
            );
        }
    }

    /**
     * Скачивание экспортированного файла
     */
    @GetMapping("/client/{clientName}/export/download/{fileId}")
    @LogExecution("Скачивание экспортированного файла")
    public ResponseEntity<Resource> downloadExportedFile(
            @PathVariable String clientName,
            @PathVariable Long fileId,
            @RequestParam Long configId,
            @RequestParam String fileType,
            @RequestParam Map<String, String> strategyParams) {

        log.debug("Запрос на экспорт файла. FileId: {}, ConfigId: {}, FileType: {}",
                fileId, configId, fileType);
        try {
            ExportConfig exportConfig = exportFieldConfigService.getConfigById(configId);
            exportConfig.setParams(strategyParams);

            ExportResult exportResult = fileExportService.exportFileData(fileId, exportConfig, fileType);

            if (!exportResult.isSuccess()) {
                log.error("Ошибка при экспорте файла: {}", exportResult.getErrorMessage());
                return ResponseEntity.badRequest().build();
            }

            if (!fileType.equalsIgnoreCase("csv")) {
                return ResponseEntity.badRequest().body(null);
            }

            // 🔹 Кодируем имя файла для корректного отображения в браузере
            String filename = exportResult.getFileName();
            String encodedFilename = new String(filename.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);

            // 🔹 Устанавливаем HTTP-заголовки
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/csv; charset=windows-1251")); // Можно убрать, если не нужно
            headers.setContentDispositionFormData("attachment", encodedFilename);

            // 🔹 Просто передаём файл (он уже закодирован в CP1251)
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
    @GetMapping("/client/{clientName}/export-mappings/strategy-params/{strategyType}")
    @ResponseBody
    public Map<String, Object> getStrategyParams(@PathVariable String clientName,
                                                 @PathVariable ProcessingStrategyType strategyType) {
        return processingStrategyService.getStrategyParameters(clientService.getClientByName(clientName).getId(), strategyType);
    }

    /**
     * Получение параметров стратегии для конфигурации
     */
    @GetMapping("/client/{clientName}/export-strategy-params/{configId}")
    @ResponseBody
    public Map<String, Object> getStrategyParameters(@PathVariable String clientName,
                                                     @PathVariable Long configId) {
        log.debug("Запрос параметров стратегии для конфигурации {}", configId);

        try {
            ExportConfig config = exportFieldConfigService.getConfigById(configId);

            // Добавляем проверку
            ProcessingStrategyType strategyType = config.getStrategyType();
            if (strategyType == null) {
                log.warn("Тип стратегии не задан для конфигурации {}", configId);
                return Map.of("error", "Тип стратегии не задан");
            }

            Set<String> requiredParams = strategyManager.getRequiredParameters(strategyType);
            log.debug("Получены параметры стратегии: {}", requiredParams);

            return Map.of(
                    "requiredParameters", requiredParams,
                    "currentValues", config.getParams() != null ? config.getParams() : Collections.emptyMap()
            );
        } catch (Exception e) {
            log.error("Ошибка при получении параметров стратегии: {}", e.getMessage(), e);
            return Map.of("error", e.getMessage());
        }
    }


    /**
     * API для обновления параметров стратегии
     */
    @PostMapping("/client/{clientName}/export-mappings/strategy-params/{strategyType}")
    @ResponseBody
    public void updateStrategyParams(@PathVariable String clientName,
                                     @PathVariable ProcessingStrategyType strategyType,
                                     @RequestBody Map<String, Object> parameters) {
        processingStrategyService.addStrategyToClient(clientService.getClientByName(clientName).getId(), strategyType, parameters);
    }

    /**
     * Получение списка поддерживаемых форматов
     */
    @GetMapping("/client/{clientName}/export/formats")
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

        // Сортируем все поля по позиции
        List<ExportField> enabledFields = config.getFields().stream()
                .filter(ExportField::isEnabled)
                .sorted(Comparator.comparingInt(ExportField::getPosition))
                .collect(Collectors.toList());

        List<ExportField> disabledFields = config.getFields().stream()
                .filter(field -> !field.isEnabled())
                .sorted(Comparator.comparing(ExportField::getSourceField))
                .collect(Collectors.toList());

        List<ProcessingStrategyType> availableStrategies =
                processingStrategyService.getAvailableStrategies(clientId);

        model.addAttribute("mapping", config);
        model.addAttribute("strategies", availableStrategies);
        model.addAttribute("enabledFields", enabledFields);
        model.addAttribute("disabledFields", disabledFields);

        log.debug("Подготовлены поля для формы: {} активных, {} неактивных",
                enabledFields.size(), disabledFields.size());
    }

    private void validateMappingOwnership(ExportConfig mapping, Long clientId) {
        if (!mapping.getClient().getId().equals(clientId)) {
            throw new IllegalArgumentException("Маппинг не принадлежит указанному клиенту");
        }
    }
}
