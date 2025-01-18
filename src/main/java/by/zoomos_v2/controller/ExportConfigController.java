package by.zoomos_v2.controller;

import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.service.ExportFieldConfigService;
import by.zoomos_v2.util.EntityField;
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

/**
 * Контроллер для управления настройками полей экспорта
 */
@Slf4j
@Controller
@RequestMapping("/client/export/mapping")
@RequiredArgsConstructor
public class ExportConfigController {

    private final ExportFieldConfigService exportFieldConfigService;
    private final ObjectMapper objectMapper;

    /**
     * Отображает страницу конфигурации экспорта
     */
    @GetMapping("/{clientId}")
    public String showConfig(@PathVariable Long clientId, Model model) {
        log.debug("Отображение страницы конфигурации экспорта для клиента: {}", clientId);

        try {
            ExportConfig config = exportFieldConfigService.getOrCreateConfig(clientId);
            model.addAttribute("config", config);
            model.addAttribute("clientId", clientId);

            return "export/config";
        } catch (Exception e) {
            log.error("Ошибка при получении конфигурации для клиента {}: {}", clientId, e.getMessage());
            model.addAttribute("error", "Ошибка при загрузке конфигурации: " + e.getMessage());
            return "error";
        }
    }

    /**
     * Обрабатывает форму обновления настроек полей
     */
    @PostMapping("/{clientId}/update")
    public String updateConfig(
            @PathVariable Long clientId,
            @RequestParam(required = false) List<String> enabledFields,
            @RequestParam String positionsJson,
            @RequestParam String configName,
            RedirectAttributes redirectAttributes) {

        try {
            List<EntityField> fields = objectMapper.readValue(positionsJson,
                    new TypeReference<List<EntityField>>() {});

            exportFieldConfigService.updateFieldsConfig(
                    clientId,
                    enabledFields,
                    null,
                    fields,
                    configName
            );
            redirectAttributes.addFlashAttribute("success", "Конфигурация успешно обновлена");

        } catch (Exception e) {
            log.error("Ошибка при обновлении конфигурации: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Ошибка при обновлении конфигурации");
        }

        return "redirect:/client/export/mapping/" + clientId;
    }


    /**
     * Сбрасывает конфигурацию к настройкам по умолчанию
     */
    @PostMapping("/{clientId}/reset")
    public String resetConfig(
            @PathVariable Long clientId,
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
}
