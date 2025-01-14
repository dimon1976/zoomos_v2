package by.zoomos_v2.controller;

import by.zoomos_v2.mapping.ClientMappingConfig;
import by.zoomos_v2.model.Client;
import by.zoomos_v2.service.ClientService;
import by.zoomos_v2.service.MappingConfigService;
import by.zoomos_v2.aspect.LogExecution;
import by.zoomos_v2.util.EntityRegistryService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Контроллер для управления настройками маппинга данных магазинов.
 * Обеспечивает функционал настройки соответствия полей при импорте данных.
 */
@Slf4j
@Controller
@RequestMapping("/shops/{shopId}/mapping")
@RequiredArgsConstructor
public class ClientMappingController {

    private final ClientService clientService;
    private final MappingConfigService mappingConfigService;
    private final EntityRegistryService entityRegistryService;

    /**
     * Отображает список настроек маппинга для магазина
     */
    @GetMapping
    public String showMappings(@PathVariable Long shopId, Model model) {
        log.debug("Запрошен список маппингов для магазина с ID: {}", shopId);
        try {
            Client client = clientService.getClientById(shopId);
            model.addAttribute("shop", client);
            model.addAttribute("mappings", mappingConfigService.getMappingsForClient(shopId));
            return "uploadMapping/mappings";
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
    @LogExecution("Форма нового маппинга")
    public String showNewMappingForm(@PathVariable Long shopId, Model model) {
        log.debug("Отображение формы создания маппинга для магазина с ID: {}", shopId);
        try {
            Client client = clientService.getClientById(shopId);
            model.addAttribute("shop", client);

            ClientMappingConfig mapping = new ClientMappingConfig();
            mapping.setClientId(shopId);
            mapping.setActive(true);
            model.addAttribute("mapping", mapping);

            // Добавляем поля для маппинга
            model.addAttribute("entityFields", entityRegistryService.getFieldsForMapping());

            return "uploadMapping/edit-mapping";
        } catch (Exception e) {
            log.error("Ошибка при создании формы маппинга: {}", e.getMessage(), e);
            model.addAttribute("error", "Ошибка при создании нового маппинга");
            return "error";
        }
    }

    /**
     * Обрабатывает создание нового маппинга
     */
    @PostMapping("/create")
    @LogExecution("Создание маппинга")
    public String createMapping(@PathVariable Long shopId,
                                @ModelAttribute("mapping") ClientMappingConfig mapping,
                                RedirectAttributes redirectAttributes) {
        log.debug("Создание нового маппинга для магазина с ID: {}", shopId);
        try {
            mapping.setClientId(shopId);
            mappingConfigService.createMapping(mapping);
            // Проверяем, что все необходимые поля заполнены
            validateMappingData(mapping);
            // Сохраняем маппинг
            ClientMappingConfig savedMapping = mappingConfigService.createMapping(mapping);

            log.info("Маппинг успешно создан с ID: {}", savedMapping.getId());
            redirectAttributes.addFlashAttribute("success", "Маппинг успешно создан");
        } catch (Exception e) {
            log.error("Ошибка при создании маппинга: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка при создании маппинга: " + e.getMessage());
        }
        return "redirect:/shops/{shopId}/mapping";
    }

    private void validateMappingData(ClientMappingConfig mapping) {
        if (mapping.getName() == null || mapping.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Название маппинга обязательно для заполнения");
        }
        if (mapping.getFileType() == null) {
            throw new IllegalArgumentException("Тип файла обязателен для заполнения");
        }
        if (mapping.getColumnsConfig() == null || mapping.getColumnsConfig().trim().isEmpty()) {
            throw new IllegalArgumentException("Необходимо настроить маппинг колонок");
        }
        try {
            // Проверяем, что columnsConfig содержит валидный JSON
            new ObjectMapper().readTree(mapping.getColumnsConfig());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Неверный формат настройки колонок");
        }
    }

    /**
     * Отображает форму редактирования маппинга
     */
    @GetMapping("/edit/{mappingId}")
    @LogExecution("Форма редактирования маппинга")
    public String showEditForm(@PathVariable Long shopId,
                               @PathVariable Long mappingId,
                               Model model) {
        log.debug("Запрошено редактирование маппинга {} для магазина {}", mappingId, shopId);
        try {
            Client client = clientService.getClientById(shopId);
            ClientMappingConfig mapping = mappingConfigService.getMappingById(mappingId);

            if (!mapping.getClientId().equals(shopId)) {
                throw new IllegalArgumentException("Маппинг не принадлежит указанному магазину");
            }

            model.addAttribute("shop", client);
            model.addAttribute("mapping", mapping);
            return "uploadMapping/edit-mapping";
        } catch (Exception e) {
            log.error("Ошибка при загрузке формы редактирования: {}", e.getMessage(), e);
            model.addAttribute("error", "Ошибка при загрузке настроек маппинга");
            return "error";
        }
    }

    /**
     * Обрабатывает обновление существующего маппинга
     */
    @PostMapping("/update/{mappingId}")
    @LogExecution("Обновление маппинга")
    public String updateMapping(@PathVariable Long shopId,
                                @PathVariable Long mappingId,
                                @ModelAttribute("mapping") ClientMappingConfig mapping,
                                RedirectAttributes redirectAttributes) {
        log.debug("Обновление маппинга {} для магазина {}", mappingId, shopId);
        try {
            mapping.setId(mappingId);
            mapping.setClientId(shopId);
            mappingConfigService.updateMapping(mapping);
            redirectAttributes.addFlashAttribute("success", "Маппинг успешно обновлен");
        } catch (Exception e) {
            log.error("Ошибка при обновлении маппинга: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка при обновлении маппинга: " + e.getMessage());
        }
        return "redirect:/shops/{shopId}/mapping";
    }

    /**
     * Обрабатывает удаление маппинга
     */
    @PostMapping("/delete/{mappingId}")
    @LogExecution("Удаление маппинга")
    public String deleteMapping(@PathVariable Long shopId,
                                @PathVariable Long mappingId,
                                RedirectAttributes redirectAttributes) {
        log.debug("Удаление маппинга {} для магазина {}", mappingId, shopId);
        try {
            ClientMappingConfig mapping = mappingConfigService.getMappingById(mappingId);
            if (!mapping.getClientId().equals(shopId)) {
                throw new IllegalArgumentException("Маппинг не принадлежит указанному магазину");
            }

            mappingConfigService.deleteMapping(mappingId);
            redirectAttributes.addFlashAttribute("success",
                    "Настройки маппинга успешно удалены");
        } catch (Exception e) {
            log.error("Ошибка при удалении маппинга: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка при удалении настроек маппинга: " + e.getMessage());
        }
        return "redirect:/shops/{shopId}/mapping";
    }
}