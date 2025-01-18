package by.zoomos_v2.controller;

import by.zoomos_v2.aspect.LogExecution;
import by.zoomos_v2.mapping.ClientMappingConfig;
import by.zoomos_v2.model.Client;
import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.model.ExportField;
import by.zoomos_v2.service.ClientService;
import by.zoomos_v2.service.ExportFieldConfigService;
import by.zoomos_v2.util.EntityField;
import by.zoomos_v2.util.EntityFieldGroup;
import by.zoomos_v2.util.EntityRegistryService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.text.FieldPosition;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Контроллер для управления настройками полей экспорта
 */
@Slf4j
@Controller
@RequestMapping("/client/{clientId}/exportmapping")
@RequiredArgsConstructor
public class ExportConfigController {

    private final ExportFieldConfigService exportFieldConfigService;
    private final EntityRegistryService entityRegistryService;
    private final ObjectMapper objectMapper;
    private final ClientService clientService;

    /**
     * Отображает список настроек upload маппинга для магазина
     */
    @GetMapping
    @LogExecution("Форма со списком export Mappings")
    public String showMappings(@PathVariable Long clientId, Model model) {
        log.debug("Запрошен список маппингов для магазина с ID: {}", clientId);
        try {
            Client client = clientService.getClientById(clientId);
            model.addAttribute("client", client);
            model.addAttribute("mappings", exportFieldConfigService.getMappingsForClient(clientId));
            return "exportMapping/mappings";
        } catch (Exception e) {
            log.error("Ошибка при получении маппингов: {}", e.getMessage(), e);
            model.addAttribute("error", "Ошибка при загрузке настроек маппинга");
            return "error";
        }
    }


    /**
     * Отображает страницу конфигурации экспорта
     */
    @GetMapping("/new")
    public String newConfig(@PathVariable Long clientId, Model model, RedirectAttributes redirectAttributes) {
        log.debug("Отображение страницы конфигурации экспорта для клиента: {}", clientId);

        try {
            // Создаем дефолтную конфигурацию
            ExportConfig config = exportFieldConfigService.createDefaultConfig(clientId);

            // Получаем все доступные поля из сущностей
            List<EntityFieldGroup> availableFields = entityRegistryService.getFieldsForMapping();

            // Собираем список ключей полей, которые уже есть в конфигурации
            Set<String> configFieldKeys = config.getFields().stream()
                    .map(ExportField::getSourceField)
                    .collect(Collectors.toSet());
            model.addAttribute("config", config);
            model.addAttribute("availableFields", availableFields);
            model.addAttribute("configFieldKeys", configFieldKeys);
            model.addAttribute("clientId", clientId);
            model.addAttribute("mappingId", null);
            redirectAttributes.addFlashAttribute("success", "Маппинг успешно создан");
            return "exportMapping/edit-mapping";
        } catch (Exception e) {
            log.error("Ошибка при получении конфигурации для клиента {}: {}", clientId, e.getMessage());
            model.addAttribute("error", "Ошибка при загрузке конфигурации: " + e.getMessage());
            return "error";
        }
    }

    /**
     * Отображает страницу конфигурации экспорта
     */
    @GetMapping("/{mappingId}")
    public String showConfig(@PathVariable Long clientId,
                             @PathVariable Long mappingId,
                             Model model) {
        log.debug("Отображение страницы конфигурации экспорта для клиента: {}", clientId);

        try {
            // Получаем текущую конфигурацию
            ExportConfig config = exportFieldConfigService.getConfigById(mappingId);
            // Получаем все доступные поля из сущностей
            List<EntityFieldGroup> availableFields = entityRegistryService.getFieldsForMapping();

            // Собираем список ключей полей, которые уже есть в конфигурации
            Set<String> configFieldKeys = config.getFields().stream()
                    .map(ExportField::getSourceField)
                    .collect(Collectors.toSet());
            model.addAttribute("config", config);
            model.addAttribute("availableFields", availableFields);
            model.addAttribute("configFieldKeys", configFieldKeys);
            model.addAttribute("clientId", clientId);

            return "exportMapping/edit-mapping";
        } catch (Exception e) {
            log.error("Ошибка при получении конфигурации для клиента {}: {}", clientId, e.getMessage());
            model.addAttribute("error", "Ошибка при загрузке конфигурации: " + e.getMessage());
            return "error";
        }
    }

    /**
     * Обрабатывает форму создания настроек полей
     */
    @PostMapping("/create/{mappingId}")
    public String createConfig(
            @PathVariable Long clientId,
            @PathVariable Long mappingId,
            @RequestParam(required = false) List<String> enabledFields,
            @RequestParam String positionsJson,
            @RequestParam String configName,
            RedirectAttributes redirectAttributes) {

        try {
            List<EntityField> fields = objectMapper.readValue(positionsJson,
                    new TypeReference<>() {
                    });

            exportFieldConfigService.updateFieldsConfig(
                    clientId,
                    enabledFields,
                    null,
                    fields,
                    configName,
                    mappingId
            );
            redirectAttributes.addFlashAttribute("success", "Конфигурация успешно создана");

        } catch (Exception e) {
            log.error("Ошибка при создании конфигурации: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Ошибка при создании конфигурации");
        }

        return "redirect:/client/{clientId}/exportmapping";
    }

    /**
     * Обрабатывает форму обновления настроек полей
     */
    @PostMapping("/edit/{mappingId}")
    public String updateConfig(
            @PathVariable Long clientId,
            @PathVariable Long mappingId,
            @RequestParam(required = false) List<String> enabledFields,
            @RequestParam String positionsJson,
            @RequestParam String configName,
            RedirectAttributes redirectAttributes) {

        try {
            List<EntityField> fields = objectMapper.readValue(positionsJson,
                    new TypeReference<>() {
                    });

            exportFieldConfigService.updateFieldsConfig(
                    clientId,
                    enabledFields,
                    null,
                    fields,
                    configName,
                    mappingId
            );
            redirectAttributes.addFlashAttribute("success", "Конфигурация успешно обновлена");

        } catch (Exception e) {
            log.error("Ошибка при обновлении конфигурации: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Ошибка при обновлении конфигурации");
        }

        return "redirect:/client/{clientId}/exportmapping";
    }




    /**
     * Сбрасывает конфигурацию к настройкам по умолчанию
     */
    @PostMapping("/{mappingId}/reset")
    public String resetConfig(
            @PathVariable Long clientId,
            @PathVariable Long mappingId,
            RedirectAttributes redirectAttributes) {

        log.debug("Сброс конфигурации для клиента: {}", clientId);

        try {
            exportFieldConfigService.createDefaultConfig(clientId);
            redirectAttributes.addFlashAttribute("success", "Конфигурация сброшена к настройкам по умолчанию");
        } catch (Exception e) {
            log.error("Ошибка при сбросе конфигурации: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Ошибка при сбросе конфигурации");
        }

        return "redirect:/client/export/mapping/" + clientId;
    }

    /**
     * Обрабатывает удаление маппинга
     */
    @PostMapping("/delete/{mappingId}")
    @LogExecution("Удаление upload маппинга")
    public String deleteMapping(@PathVariable Long clientId,
                                @PathVariable Long mappingId,
                                RedirectAttributes redirectAttributes) {
        log.debug("Удаление маппинга {} для магазина {}", mappingId, clientId);
        try {
            // Получаем текущую конфигурацию
            ExportConfig config = exportFieldConfigService.getConfigById(mappingId);
            if (!config.getClient().getId().equals(clientId)) {
                throw new IllegalArgumentException("Маппинг не принадлежит указанному магазину");
            }

            exportFieldConfigService.deleteMapping(mappingId);
            redirectAttributes.addFlashAttribute("success",
                    "Настройки маппинга успешно удалены");
        } catch (Exception e) {
            log.error("Ошибка при удалении маппинга: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка при удалении настроек маппинга: " + e.getMessage());
        }
        return "redirect:/client/{clientId}/exportmapping";
    }
}
