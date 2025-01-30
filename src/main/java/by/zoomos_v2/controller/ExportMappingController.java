package by.zoomos_v2.controller;

import by.zoomos_v2.aspect.LogExecution;
import by.zoomos_v2.model.Client;
import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.model.ExportField;
import by.zoomos_v2.service.client.ClientService;
import by.zoomos_v2.service.file.export.service.ProcessingStrategyService;
import by.zoomos_v2.service.file.export.strategy.ProcessingStrategyType;
import by.zoomos_v2.service.mapping.ExportFieldConfigService;
import by.zoomos_v2.util.EntityField;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/client/{clientId}/export-mappings")
@RequiredArgsConstructor
public class ExportMappingController {
    private final ProcessingStrategyService processingStrategyService;
    private final ExportFieldConfigService exportFieldConfigService;
    private final ObjectMapper objectMapper;
    private final ClientService clientService;

    /**
     * Отображает список настроек маппинга для экспорта
     */
    @GetMapping
    @LogExecution("Просмотр списка маппингов экспорта")
    public String showMappings(@PathVariable Long clientId, Model model) {
        log.debug("Запрошен список маппингов экспорта для магазина с ID: {}", clientId);
        try {
            Client client = clientService.getClientById(clientId);
            List<ExportConfig> mappings = exportFieldConfigService.getMappingsForClient(clientId)
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
    @GetMapping("/new")
    @LogExecution("Создание нового маппинга экспорта")
    public String showNewMappingForm(@PathVariable Long clientId, Model model) {
        log.debug("Создание нового маппинга экспорта для клиента: {}", clientId);
        try {
            ExportConfig config = exportFieldConfigService.createTemporaryConfig(clientId);

            Map<String, List<ExportField>> groupedEnabledFields = config.getFields().stream()
                    .filter(ExportField::isEnabled)
                    .collect(Collectors.groupingBy(field -> field.getSourceField().split("\\.")[0]));

            Map<String, List<ExportField>> groupedDisabledFields = config.getFields().stream()
                    .filter(field -> !field.isEnabled())
                    .collect(Collectors.groupingBy(field -> field.getSourceField().split("\\.")[0]));

            List<ProcessingStrategyType> availableStrategies =
                    processingStrategyService.getAvailableStrategies(clientId);

            if (availableStrategies.isEmpty()) {
                log.warn("Список доступных стратегий пуст");
                availableStrategies = Arrays.asList(ProcessingStrategyType.values());
            }

            model.addAttribute("client", clientService.getClientById(clientId));
            model.addAttribute("strategies", availableStrategies);
            model.addAttribute("groupedEnabledFields", groupedEnabledFields);
            model.addAttribute("groupedDisabledFields", groupedDisabledFields);
            model.addAttribute("config", config);

            return "client/export-mappings/edit";
        } catch (Exception e) {
            log.error("Ошибка при создании маппинга: {}", e.getMessage(), e);
            model.addAttribute("error", "Ошибка при создании маппинга");
            return "error";
        }
    }

    /**
     * Создает новый маппинг
     */
    @PostMapping("/create")
    @LogExecution("Сохранение нового маппинга экспорта")
    public String createMapping(@PathVariable Long clientId,
                                @RequestParam String positionsJson,
                                @RequestParam String configName,
                                @RequestParam String configDescription,
                                @RequestParam ProcessingStrategyType strategyType,
                                RedirectAttributes redirectAttributes) {
        log.debug("Создание нового маппинга экспорта для клиента {}", clientId);
        try {
            log.debug("Параметры: positionsJson={}, configName={}, description={}, strategyType={}",
                    positionsJson, configName, configDescription, strategyType);

            if (positionsJson == null || positionsJson.isEmpty()) {
                throw new IllegalArgumentException("Не указаны поля для маппинга");
            }

            List<EntityField> fields = objectMapper.readValue(positionsJson,
                    new TypeReference<List<EntityField>>() {});

            exportFieldConfigService.createConfig(clientId, configName, fields, configDescription, strategyType);
            redirectAttributes.addFlashAttribute("success", "Маппинг успешно создан");
        } catch (Exception e) {
            log.error("Ошибка при создании маппинга: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка при создании маппинга: " + e.getMessage());
        }
        return "redirect:/client/{clientId}/export-mappings";
    }

    /**
     * Отображает форму редактирования маппинга
     */
    @GetMapping("/{mappingId}")
    @LogExecution("Редактирование маппинга экспорта")
    public String editMapping(@PathVariable Long clientId,
                              @PathVariable Long mappingId,
                              Model model) {
        log.debug("Редактирование маппинга {} для клиента {}", mappingId, clientId);
        try {
            ExportConfig config = exportFieldConfigService.getConfigById(mappingId);
            validateMappingOwnership(config, clientId);

            Map<String, List<ExportField>> groupedEnabledFields = config.getFields().stream()
                    .filter(ExportField::isEnabled)
                    .collect(Collectors.groupingBy(field -> field.getSourceField().split("\\.")[0]));

            Map<String, List<ExportField>> groupedDisabledFields = config.getFields().stream()
                    .filter(field -> !field.isEnabled())
                    .collect(Collectors.groupingBy(field -> field.getSourceField().split("\\.")[0]));

            List<ProcessingStrategyType> availableStrategies =
                    processingStrategyService.getAvailableStrategies(clientId);

            model.addAttribute("client", clientService.getClientById(clientId));
            model.addAttribute("strategies", availableStrategies);
            model.addAttribute("groupedEnabledFields", groupedEnabledFields);
            model.addAttribute("groupedDisabledFields", groupedDisabledFields);
            model.addAttribute("config", config);

            return "client/export-mappings/edit";
        } catch (Exception e) {
            log.error("Ошибка при загрузке маппинга: {}", e.getMessage(), e);
            model.addAttribute("error", "Ошибка при загрузке маппинга");
            return "error";
        }
    }

    /**
     * Обновляет существующий маппинг
     */
    @PostMapping("/{mappingId}/update")
    @LogExecution("Обновление маппинга экспорта")
    public String updateMapping(@PathVariable Long clientId,
                                @PathVariable Long mappingId,
                                @RequestParam String positionsJson,
                                @RequestParam String configName,
                                @RequestParam String configDescription,
                                @RequestParam ProcessingStrategyType strategyType,
                                RedirectAttributes redirectAttributes) {
        log.debug("Обновление маппинга {} для клиента {}", mappingId, clientId);
        try {
            List<EntityField> fields = objectMapper.readValue(positionsJson,
                    new TypeReference<List<EntityField>>() {});

            exportFieldConfigService.updateFieldsConfig(
                    clientId,
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
        return "redirect:/client/{clientId}/export-mappings";
    }

    /**
     * Удаляет маппинг
     */
    @PostMapping("/{mappingId}/delete")
    @LogExecution("Удаление маппинга экспорта")
    public String deleteMapping(@PathVariable Long clientId,
                                @PathVariable Long mappingId,
                                RedirectAttributes redirectAttributes) {
        log.debug("Удаление маппинга {} для клиента {}", mappingId, clientId);
        try {
            ExportConfig config = exportFieldConfigService.getConfigById(mappingId);
            validateMappingOwnership(config, clientId);

            exportFieldConfigService.deleteMapping(mappingId);
            redirectAttributes.addFlashAttribute("success", "Маппинг успешно удален");
        } catch (Exception e) {
            log.error("Ошибка при удалении маппинга: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка при удалении маппинга: " + e.getMessage());
        }
        return "redirect:/client/{clientId}/export-mappings";
    }

    /**
     * Параметры стратегии обработки
     */
    @GetMapping("/strategy-params/{strategyType}")
    @ResponseBody
    public Map<String, Object> getStrategyParams(@PathVariable Long clientId,
                                                 @PathVariable ProcessingStrategyType strategyType) {
        return processingStrategyService.getStrategyParameters(clientId, strategyType);
    }

    /**
     * Обновление параметров стратегии
     */
    @PostMapping("/strategy-params/{strategyType}")
    @ResponseBody
    public void updateStrategyParams(@PathVariable Long clientId,
                                     @PathVariable ProcessingStrategyType strategyType,
                                     @RequestBody Map<String, Object> parameters) {
        processingStrategyService.addStrategyToClient(clientId, strategyType, parameters);
    }

    private void validateMappingOwnership(ExportConfig mapping, Long clientId) {
        if (!mapping.getClient().getId().equals(clientId)) {
            throw new IllegalArgumentException("Маппинг не принадлежит указанному клиенту");
        }
    }
}
