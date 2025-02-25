package by.zoomos_v2.controller;

import by.zoomos_v2.aspect.LogExecution;
import by.zoomos_v2.mapping.ClientMappingConfig;
import by.zoomos_v2.model.Client;
import by.zoomos_v2.service.client.ClientService;
import by.zoomos_v2.service.mapping.MappingConfigService;
import by.zoomos_v2.util.EntityFieldGroup;
import by.zoomos_v2.util.EntityRegistryService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Контроллер для управления настройками маппинга данных магазинов.
 * Обеспечивает функционал настройки соответствия полей при импорте данных.
 */
@Slf4j
@Controller
@RequestMapping("/client/{clientName}/mappings")
@RequiredArgsConstructor
public class ClientMappingController {


    private final ClientService clientService;
    private final MappingConfigService mappingConfigService;
    private final EntityRegistryService entityRegistryService;
// TODO Починить перетаскивание полей в форме маппинга
    /**
     * Отображает список настроек маппинга для клиента
     */
    @GetMapping
    @LogExecution("Просмотр списка маппингов")
    public String showMappings(@PathVariable String clientName, Model model) {
        log.debug("Запрошен список маппингов для магазина с clientName: {}", clientName);
        try {
            Client client = clientService.getClientById(clientService.getClientByName(clientName).getId());
            model.addAttribute("client", client);
            model.addAttribute("mappings", mappingConfigService.getMappingsForClient(clientService.getClientByName(clientName).getId()));
            return "client/mappings/list";
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
    @LogExecution("Создание нового маппинга")
    public String showNewMappingForm(@PathVariable String clientName,
                                     Model model,
                                     @RequestParam(value = "active", defaultValue = "true") boolean active) {
        log.debug("Отображение формы создания маппинга для магазина с clientName: {}", clientName);
        try {
            Client client = clientService.getClientByName(clientName);

            ClientMappingConfig mapping = new ClientMappingConfig();
            mapping.setClientId(clientService.getClientByName(clientName).getId());
            mapping.setActive(active);

            model.addAttribute("client", client);
            model.addAttribute("mapping", mapping);
            model.addAttribute("entityFields", entityRegistryService.getFieldsForMapping());

            return "client/mappings/edit";
        } catch (Exception e) {
            log.error("Ошибка при создании формы маппинга: {}", e.getMessage(), e);
            model.addAttribute("error", "Ошибка при создании нового маппинга");
            return "error";
        }
    }

    /**
     * Создает новый маппинг
     */
    @PostMapping("/create")
    @LogExecution("Сохранение нового маппинга")
    public String createMapping(@PathVariable String clientName,
                                @RequestParam(value = "active", defaultValue = "false") boolean active,
                                @ModelAttribute("mapping") ClientMappingConfig mapping,
                                RedirectAttributes redirectAttributes) {
        log.debug("Создание нового маппинга для магазина с clientName: {}", clientName);
        try {
            mapping.setClientId(clientService.getClientByName(clientName).getId());
            mapping.setActive(active);
            validateMappingData(mapping);

            ClientMappingConfig savedMapping = mappingConfigService.createMapping(mapping);
            log.info("Маппинг успешно создан с ID: {}", savedMapping.getId());
            redirectAttributes.addFlashAttribute("success", "Маппинг успешно создан");
        } catch (Exception e) {
            log.error("Ошибка при создании маппинга: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка при создании маппинга: " + e.getMessage());
        }
        return "redirect:/client/{clientName}/mappings";
    }

    /**
     * Отображает форму редактирования маппинга
     */
    @GetMapping("/{mappingId}/edit")
    @LogExecution("Редактирование маппинга")
    public String showEditForm(@PathVariable String clientName,
                               @PathVariable Long mappingId,
                               Model model) {
        log.debug("Запрошено редактирование маппинга {} для магазина {}", mappingId, clientName);
        try {
            Client client = clientService.getClientByName(clientName);
            ClientMappingConfig mapping = mappingConfigService.getMappingById(mappingId);
            validateMappingOwnership(mapping, clientService.getClientByName(clientName).getId());

            List<EntityFieldGroup> fields = entityRegistryService.getFieldsForMapping();
            log.debug("Получены поля для маппинга: {}", fields);

            model.addAttribute("client", client);
            model.addAttribute("mapping", mapping);
            model.addAttribute("entityFields", fields);

            return "client/mappings/edit";
        } catch (Exception e) {
            log.error("Ошибка при загрузке формы редактирования: {}", e.getMessage(), e);
            model.addAttribute("error", "Ошибка при загрузке настроек маппинга");
            return "error";
        }
    }

    /**
     * Обновляет существующий маппинг
     */
    @PostMapping("/{mappingId}/update")
    @LogExecution("Обновление маппинга")
    public String updateMapping(@PathVariable String clientName,
                                @PathVariable Long mappingId,
                                @RequestParam(value = "active", defaultValue = "false") boolean active,
                                @ModelAttribute("mapping") ClientMappingConfig mapping,
                                RedirectAttributes redirectAttributes) {
        log.debug("Обновление маппинга {} для магазина {}", mappingId, clientName);
        try {
            mapping.setId(mappingId);
            mapping.setClientId(clientService.getClientByName(clientName).getId());
            mapping.setActive(active);

            validateMappingOwnership(mappingConfigService.getMappingById(mappingId), clientService.getClientByName(clientName).getId());
            validateMappingData(mapping);

            mappingConfigService.updateMapping(mapping);
            redirectAttributes.addFlashAttribute("success", "Маппинг успешно обновлен");
        } catch (Exception e) {
            log.error("Ошибка при обновлении маппинга: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка при обновлении маппинга: " + e.getMessage());
        }
        return "redirect:/client/{clientName}/mappings";
    }

    /**
     * Удаляет маппинг
     */
    @PostMapping("/{mappingId}/delete")
    @LogExecution("Удаление маппинга")
    public String deleteMapping(@PathVariable String clientName,
                                @PathVariable Long mappingId,
                                RedirectAttributes redirectAttributes) {
        log.debug("Удаление маппинга {} для магазина {}", mappingId, clientName);
        try {
            validateMappingOwnership(mappingConfigService.getMappingById(mappingId), clientService.getClientByName(clientName).getId());
            mappingConfigService.deleteMapping(mappingId);
            redirectAttributes.addFlashAttribute("success", "Маппинг успешно удален");
        } catch (Exception e) {
            log.error("Ошибка при удалении маппинга: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка при удалении маппинга: " + e.getMessage());
        }
        // Изменяем редирект на страницу списка маппингов
        return "redirect:/client/{clientName}/mappings";
    }

    private void validateMappingData(ClientMappingConfig mapping) {
        if (mapping.getName() == null || mapping.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Название маппинга обязательно для заполнения");
        }
        if (mapping.getFileType() == null) {
            throw new IllegalArgumentException("Тип файла обязателен для заполнения");
        }
        if (mapping.getDataSource() == null) {
            throw new IllegalArgumentException("Тип источника данных обязателен для заполнения");
        }
        if (mapping.getColumnsConfig() == null || mapping.getColumnsConfig().trim().isEmpty()) {
            throw new IllegalArgumentException("Необходимо настроить маппинг колонок");
        }
        try {
            new ObjectMapper().readTree(mapping.getColumnsConfig());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Неверный формат настройки колонок");
        }
    }

    private void validateMappingOwnership(ClientMappingConfig mapping, Long clientId) {
        if (!mapping.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("Маппинг не принадлежит указанному магазину");
        }
    }
}