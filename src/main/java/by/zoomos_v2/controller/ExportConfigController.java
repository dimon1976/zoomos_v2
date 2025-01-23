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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Контроллер для управления настройками полей экспорта
 */
@Slf4j
@Controller
@RequestMapping("/client/{clientId}/exportmapping")
@RequiredArgsConstructor
public class ExportConfigController {
    @Autowired
    private ProcessingStrategyService processingStrategyService;
    private final ExportFieldConfigService exportFieldConfigService;
    private final ObjectMapper objectMapper;
    private final ClientService clientService;

    /**
     * Отображает список настроек маппинга для магазина
     */
    @GetMapping
    @LogExecution("Форма со списком export Mappings")
    public String showMappings(@PathVariable Long clientId, Model model) {
        log.debug("Запрошен список маппингов для магазина с ID: {}", clientId);
        try {
            Client client = clientService.getClientById(clientId);
            List<ExportConfig> mappings = exportFieldConfigService.getMappingsForClient(clientId)
                    .orElse(Collections.emptyList()); // Возвращаем пустой список, если ничего не найдено

            model.addAttribute("client", client);
            model.addAttribute("mappings", mappings);
            return "exportMapping/mappings";
        } catch (Exception e) {
            log.error("Ошибка при получении маппингов: {}", e.getMessage(), e);
            model.addAttribute("error", "Ошибка при загрузке настроек маппинга");
            return "error";
        }
    }

    /**
     * Отображает форму создания новой конфигурации
     */
    @GetMapping("/new")
    public String newConfig(@PathVariable Long clientId, Model model) {
        log.debug("Создание новой конфигурации для клиента: {}", clientId);
        try {
            // Создаем временную конфигурацию со всеми полями
            ExportConfig config = exportFieldConfigService.createTemporaryConfig(clientId);

            // Группируем поля по источнику и фильтруем по признаку enabled
            Map<String, List<ExportField>> groupedEnabledFields = config.getFields().stream()
                    .filter(ExportField::isEnabled)
                    .collect(Collectors.groupingBy(field -> field.getSourceField().split("\\.")[0]));

            Map<String, List<ExportField>> groupedDisabledFields = config.getFields().stream()
                    .filter(field -> !field.isEnabled())
                    .collect(Collectors.groupingBy(field -> field.getSourceField().split("\\.")[0]));
            // Добавляем список доступных стратегий
            List<ProcessingStrategyType> availableStrategies =
                    processingStrategyService.getAvailableStrategies(clientId);
            // Передаем данные в модель
            model.addAttribute("strategies", availableStrategies);
            model.addAttribute("groupedEnabledFields", groupedEnabledFields);
            model.addAttribute("groupedDisabledFields", groupedDisabledFields);
            model.addAttribute("config", config);
            model.addAttribute("clientId", clientId);
            model.addAttribute("mappingId", null);

            return "exportMapping/edit-mapping";
        } catch (Exception e) {
            log.error("Ошибка при создании конфигурации: {}", e.getMessage(), e);
            model.addAttribute("error", "Ошибка при создании конфигурации");
            return "error";
        }
    }

    /**
     * Сохраняет новую конфигурацию
     */
    @PostMapping("/create")
    public String createConfig(
            @PathVariable Long clientId,
            @RequestParam String positionsJson,
            @RequestParam String configName,
            @RequestParam String configDescription,
            @RequestParam ProcessingStrategyType strategyType,
            RedirectAttributes redirectAttributes) {

        log.debug("Создание новой конфигурации для клиента {}", clientId);
        try {
            List<EntityField> fields = objectMapper.readValue(positionsJson, new TypeReference<>() {});
            exportFieldConfigService.createConfig(clientId, configName, fields, configDescription, strategyType);
            redirectAttributes.addFlashAttribute("success", "Конфигурация успешно создана");
            return "redirect:/client/{clientId}/exportmapping";
        } catch (Exception e) {
            log.error("Ошибка при создании конфигурации: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Ошибка при создании конфигурации");
            return "redirect:/client/{clientId}/exportmapping";
        }
    }

    /**
     * Отображает форму редактирования конфигурации
     */
    @GetMapping("/{mappingId}")
    public String editConfig(@PathVariable Long clientId,
                             @PathVariable Long mappingId,
                             Model model) {
        log.debug("Редактирование конфигурации {} для клиента {}", mappingId, clientId);
        try {
            ExportConfig config = exportFieldConfigService.getConfigById(mappingId);
            // Группируем поля по источнику и фильтруем по признаку enabled
            Map<String, List<ExportField>> groupedEnabledFields = config.getFields().stream()
                    .filter(ExportField::isEnabled)
                    .collect(Collectors.groupingBy(field -> field.getSourceField().split("\\.")[0]));

            Map<String, List<ExportField>> groupedDisabledFields = config.getFields().stream()
                    .filter(field -> !field.isEnabled())
                    .collect(Collectors.groupingBy(field -> field.getSourceField().split("\\.")[0]));
            // Добавляем список доступных стратегий
            List<ProcessingStrategyType> availableStrategies =
                    processingStrategyService.getAvailableStrategies(clientId);
            // Передаем данные в модель
            model.addAttribute("strategies", availableStrategies);
            model.addAttribute("groupedEnabledFields", groupedEnabledFields);
            model.addAttribute("groupedDisabledFields", groupedDisabledFields);
            model.addAttribute("config", config);
            model.addAttribute("clientId", clientId);
            model.addAttribute("mappingId", mappingId);
            return "exportMapping/edit-mapping";
        } catch (Exception e) {
            log.error("Ошибка при получении конфигурации: {}", e.getMessage(), e);
            model.addAttribute("error", "Ошибка при загрузке конфигурации");
            return "error";
        }
    }

    /**
     * Обновляет существующую конфигурацию
     */
    @PostMapping("/{mappingId}/update")
    public String updateConfig(
            @PathVariable Long clientId,
            @PathVariable Long mappingId,
            @RequestParam String positionsJson,
            @RequestParam String configName,
            @RequestParam String configDescription,
            @RequestParam ProcessingStrategyType strategyType,
            RedirectAttributes redirectAttributes) {

        log.debug("Обновление конфигурации {} для клиента {}", mappingId, clientId);
        try {
            List<EntityField> fields = objectMapper.readValue(positionsJson, new TypeReference<>() {
            });
            exportFieldConfigService.updateFieldsConfig(
                    clientId,
                    fields,
                    configName,
                    mappingId,
                    configDescription,
                    strategyType
            );
            redirectAttributes.addFlashAttribute("success", "Конфигурация успешно обновлена");
        } catch (Exception e) {
            log.error("Ошибка при обновлении конфигурации: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Ошибка при обновлении конфигурации");
        }
        return "redirect:/client/{clientId}/exportmapping";
    }

    /**
     * Удаляет конфигурацию
     */
    @PostMapping("/{mappingId}/delete")
    public String deleteConfig(@PathVariable Long clientId,
                               @PathVariable Long mappingId,
                               RedirectAttributes redirectAttributes) {
        log.debug("Удаление конфигурации {} для клиента {}", mappingId, clientId);
        try {
            ExportConfig config = exportFieldConfigService.getConfigById(mappingId);
            if (!config.getClient().getId().equals(clientId)) {
                throw new IllegalArgumentException("Конфигурация не принадлежит указанному клиенту");
            }
            exportFieldConfigService.deleteMapping(mappingId);
            redirectAttributes.addFlashAttribute("success", "Конфигурация успешно удалена");
        } catch (Exception e) {
            log.error("Ошибка при удалении конфигурации: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Ошибка при удалении конфигурации");
        }
        return "redirect:/client/{clientId}/exportmapping";
    }

    /**
     * Сбрасывает конфигурацию к настройкам по умолчанию
     */
    @PostMapping("/{mappingId}/reset")
    public String resetConfig(@PathVariable Long clientId,
                              @PathVariable Long mappingId,
                              RedirectAttributes redirectAttributes) {
        log.debug("Сброс конфигурации {} к настройкам по умолчанию для клиента {}", mappingId, clientId);
        try {
            // Проверяем существование и принадлежность конфигурации
            ExportConfig config = exportFieldConfigService.getConfigById(mappingId);
            if (!config.getClient().getId().equals(clientId)) {
                throw new IllegalArgumentException("Конфигурация не принадлежит указанному клиенту");
            }

            // Создаем новую дефолтную конфигурацию
            ExportConfig defaultConfig = exportFieldConfigService.createDefaultConfig(clientId);
            redirectAttributes.addFlashAttribute("success", "Конфигурация успешно сброшена к настройкам по умолчанию");
        } catch (Exception e) {
            log.error("Ошибка при сбросе конфигурации: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Ошибка при сбросе конфигурации");
        }
        return "redirect:/client/{clientId}/exportmapping/{mappingId}";
    }
}
