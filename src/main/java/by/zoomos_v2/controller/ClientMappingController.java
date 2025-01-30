package by.zoomos_v2.controller;

import by.zoomos_v2.mapping.ClientMappingConfig;
import by.zoomos_v2.model.Client;
import by.zoomos_v2.service.client.ClientService;
import by.zoomos_v2.service.mapping.MappingConfigService;
import by.zoomos_v2.aspect.LogExecution;
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
@RequestMapping("/client/{clientId}/mappings")
@RequiredArgsConstructor
public class ClientMappingController {

//    private final ClientService clientService;
//    private final MappingConfigService mappingConfigService;
//    private final EntityRegistryService entityRegistryService;
//
//
//
//    /**
//     * Отображает список настроек маппинга для магазина
//     */
//    @GetMapping
//    @LogExecution("Просмотр списка маппингов")
//    public String showMappings(@PathVariable Long clientId, Model model) {
//        log.debug("Запрошен список маппингов для магазина с ID: {}", clientId);
//        try {
//            Client client = clientService.getClientById(clientId);
//            model.addAttribute("client", client);
//            model.addAttribute("mappings", mappingConfigService.getMappingsForClient(clientId));
//            return "uploadMapping/mappings";
//        } catch (Exception e) {
//            log.error("Ошибка при получении маппингов: {}", e.getMessage(), e);
//            model.addAttribute("error", "Ошибка при загрузке настроек маппинга");
//            return "error";
//        }
//    }
//
//    /**
//     * Отображает форму создания нового маппинга
//     */
//    @GetMapping("/new")
//    @LogExecution("Форма нового upload маппинга")
//    public String showNewMappingForm(@PathVariable Long clientId, Model model, @RequestParam(value = "active", defaultValue = "true") boolean active) {
//        log.debug("Отображение формы создания маппинга для магазина с ID: {}", clientId);
//        try {
//            Client client = clientService.getClientById(clientId);
//            model.addAttribute("client", client);
//
//            ClientMappingConfig mapping = new ClientMappingConfig();
//            mapping.setClientId(clientId);
//            mapping.setActive(active);
//            model.addAttribute("mapping", mapping);
//
//            // Добавляем поля для маппинга
//            model.addAttribute("entityFields", entityRegistryService.getFieldsForMapping());
//
//            return "uploadMapping/edit-mapping";
//        } catch (Exception e) {
//            log.error("Ошибка при создании формы маппинга: {}", e.getMessage(), e);
//            model.addAttribute("error", "Ошибка при создании нового маппинга");
//            return "error";
//        }
//    }
//
//    /**
//     * Обрабатывает создание нового маппинга
//     */
//    @PostMapping("/create")
//    @LogExecution("Создание upload маппинга")
//    public String createMapping(@PathVariable Long clientId,
//                                @RequestParam(value = "active", defaultValue = "false") boolean active,
//                                @ModelAttribute("mapping") ClientMappingConfig mapping,
//                                RedirectAttributes redirectAttributes) {
//        log.debug("Создание нового маппинга для магазина с ID: {}", clientId);
//        try {
//            mapping.setClientId(clientId);
//            // Проверяем, что все необходимые поля заполнены
//            validateMappingData(mapping);
//            // Сохраняем маппинг
//            mapping.setActive(active);
//            ClientMappingConfig savedMapping = mappingConfigService.createMapping(mapping);
//
//            log.info("Маппинг успешно создан с ID: {}", savedMapping.getId());
//            redirectAttributes.addFlashAttribute("success", "Маппинг успешно создан");
//        } catch (Exception e) {
//            log.error("Ошибка при создании маппинга: {}", e.getMessage(), e);
//            redirectAttributes.addFlashAttribute("error",
//                    "Ошибка при создании маппинга: " + e.getMessage());
//        }
//        return "redirect:/client/{clientId}/uploadmapping";
//    }
//
//    /**
//     * Отображает форму редактирования маппинга
//     */
//    @GetMapping("/edit/{mappingId}")
//    @LogExecution("Форма редактирования upload маппинга")
//    public String showEditForm(@PathVariable Long clientId,
//                               @PathVariable Long mappingId,
//                               Model model) {
//        log.debug("Запрошено редактирование маппинга {} для магазина {}", mappingId, clientId);
//        try {
//            Client client = clientService.getClientById(clientId);
//            ClientMappingConfig mapping = mappingConfigService.getMappingById(mappingId);
//
//            if (!mapping.getClientId().equals(clientId)) {
//                throw new IllegalArgumentException("Маппинг не принадлежит указанному магазину");
//            }
//
//            List<EntityFieldGroup> fields = entityRegistryService.getFieldsForMapping();
//            log.debug("Получены поля для маппинга: {}", fields); // проверяем получаемые поля
//
//            model.addAttribute("client", client);
//            model.addAttribute("mapping", mapping);
//            // Добавляем поля для маппинга
//            model.addAttribute("entityFields", fields);
//            return "uploadMapping/edit-mapping";
//        } catch (Exception e) {
//            log.error("Ошибка при загрузке формы редактирования: {}", e.getMessage(), e);
//            model.addAttribute("error", "Ошибка при загрузке настроек маппинга");
//            return "error";
//        }
//    }
//
//    /**
//     * Обрабатывает обновление существующего маппинга
//     */
//    @PostMapping("/update/{mappingId}")
//    @LogExecution("Обновление upload маппинга")
//    public String updateMapping(@PathVariable Long clientId,
//                                @PathVariable Long mappingId,
//                                @RequestParam(value = "active", defaultValue = "false") boolean active,
//                                @ModelAttribute("mapping") ClientMappingConfig mapping,
//                                RedirectAttributes redirectAttributes) {
//        log.debug("Обновление маппинга {} для магазина {}", mappingId, clientId);
//        try {
//            mapping.setId(mappingId);
//            mapping.setClientId(clientId);
//            mapping.setActive(active);
//            ClientMappingConfig existingMapping = mappingConfigService.getMappingById(mappingId);
//            if (!existingMapping.getClientId().equals(clientId)) {
//                throw new IllegalArgumentException("Маппинг не принадлежит указанному магазину");
//            }
//            mappingConfigService.updateMapping(mapping);
//            redirectAttributes.addFlashAttribute("success", "Маппинг успешно обновлен");
//        } catch (Exception e) {
//            log.error("Ошибка при обновлении маппинга: {}", e.getMessage(), e);
//            redirectAttributes.addFlashAttribute("error",
//                    "Ошибка при обновлении маппинга: " + e.getMessage());
//        }
//        return "redirect:/client/{clientId}/uploadmapping";
//    }
//
//    /**
//     * Обрабатывает удаление маппинга
//     */
//    @PostMapping("/delete/{mappingId}")
//    @LogExecution("Удаление upload маппинга")
//    public String deleteMapping(@PathVariable Long clientId,
//                                @PathVariable Long mappingId,
//                                RedirectAttributes redirectAttributes) {
//        log.debug("Удаление маппинга {} для магазина {}", mappingId, clientId);
//        try {
//            ClientMappingConfig mapping = mappingConfigService.getMappingById(mappingId);
//            if (!mapping.getClientId().equals(clientId)) {
//                throw new IllegalArgumentException("Маппинг не принадлежит указанному магазину");
//            }
//
//            mappingConfigService.deleteMapping(mappingId);
//            redirectAttributes.addFlashAttribute("success",
//                    "Настройки маппинга успешно удалены");
//        } catch (Exception e) {
//            log.error("Ошибка при удалении маппинга: {}", e.getMessage(), e);
//            redirectAttributes.addFlashAttribute("error",
//                    "Ошибка при удалении настроек маппинга: " + e.getMessage());
//        }
//        return "redirect:/client/{clientId}/uploadmapping";
//    }
//
//    private void validateMappingData(ClientMappingConfig mapping) {
//        if (mapping.getName() == null || mapping.getName().trim().isEmpty()) {
//            throw new IllegalArgumentException("Название маппинга обязательно для заполнения");
//        }
//        if (mapping.getFileType() == null) {
//            throw new IllegalArgumentException("Тип файла обязателен для заполнения");
//        }
//        if (mapping.getColumnsConfig() == null || mapping.getColumnsConfig().trim().isEmpty()) {
//            throw new IllegalArgumentException("Необходимо настроить маппинг колонок");
//        }
//        try {
//            // Проверяем, что columnsConfig содержит валидный JSON
//            new ObjectMapper().readTree(mapping.getColumnsConfig());
//        } catch (JsonProcessingException e) {
//            throw new IllegalArgumentException("Неверный формат настройки колонок");
//        }
//    }

    private final ClientService clientService;
    private final MappingConfigService mappingConfigService;
    private final EntityRegistryService entityRegistryService;
// TODO Починить перетаскивание полей в форме маппинга
    /**
     * Отображает список настроек маппинга для клиента
     */
    @GetMapping
    @LogExecution("Просмотр списка маппингов")
    public String showMappings(@PathVariable Long clientId, Model model) {
        log.debug("Запрошен список маппингов для магазина с ID: {}", clientId);
        try {
            Client client = clientService.getClientById(clientId);
            model.addAttribute("client", client);
            model.addAttribute("mappings", mappingConfigService.getMappingsForClient(clientId));
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
    public String showNewMappingForm(@PathVariable Long clientId,
                                     Model model,
                                     @RequestParam(value = "active", defaultValue = "true") boolean active) {
        log.debug("Отображение формы создания маппинга для магазина с ID: {}", clientId);
        try {
            Client client = clientService.getClientById(clientId);

            ClientMappingConfig mapping = new ClientMappingConfig();
            mapping.setClientId(clientId);
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
    public String createMapping(@PathVariable Long clientId,
                                @RequestParam(value = "active", defaultValue = "false") boolean active,
                                @ModelAttribute("mapping") ClientMappingConfig mapping,
                                RedirectAttributes redirectAttributes) {
        log.debug("Создание нового маппинга для магазина с ID: {}", clientId);
        try {
            mapping.setClientId(clientId);
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
        return "redirect:/client/{clientId}/mappings";
    }

    /**
     * Отображает форму редактирования маппинга
     */
    @GetMapping("/{mappingId}/edit")
    @LogExecution("Редактирование маппинга")
    public String showEditForm(@PathVariable Long clientId,
                               @PathVariable Long mappingId,
                               Model model) {
        log.debug("Запрошено редактирование маппинга {} для магазина {}", mappingId, clientId);
        try {
            Client client = clientService.getClientById(clientId);
            ClientMappingConfig mapping = mappingConfigService.getMappingById(mappingId);
            validateMappingOwnership(mapping, clientId);

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
    public String updateMapping(@PathVariable Long clientId,
                                @PathVariable Long mappingId,
                                @RequestParam(value = "active", defaultValue = "false") boolean active,
                                @ModelAttribute("mapping") ClientMappingConfig mapping,
                                RedirectAttributes redirectAttributes) {
        log.debug("Обновление маппинга {} для магазина {}", mappingId, clientId);
        try {
            mapping.setId(mappingId);
            mapping.setClientId(clientId);
            mapping.setActive(active);

            validateMappingOwnership(mappingConfigService.getMappingById(mappingId), clientId);
            validateMappingData(mapping);

            mappingConfigService.updateMapping(mapping);
            redirectAttributes.addFlashAttribute("success", "Маппинг успешно обновлен");
        } catch (Exception e) {
            log.error("Ошибка при обновлении маппинга: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка при обновлении маппинга: " + e.getMessage());
        }
        return "redirect:/client/{clientId}/mappings";
    }

    /**
     * Удаляет маппинг
     */
    @PostMapping("/{mappingId}/delete")
    @LogExecution("Удаление маппинга")
    public String deleteMapping(@PathVariable Long clientId,
                                @PathVariable Long mappingId,
                                RedirectAttributes redirectAttributes) {
        log.debug("Удаление маппинга {} для магазина {}", mappingId, clientId);
        try {
            validateMappingOwnership(mappingConfigService.getMappingById(mappingId), clientId);
            mappingConfigService.deleteMapping(mappingId);
            redirectAttributes.addFlashAttribute("success", "Маппинг успешно удален");
        } catch (Exception e) {
            log.error("Ошибка при удалении маппинга: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка при удалении маппинга: " + e.getMessage());
        }
        // Изменяем редирект на страницу списка маппингов
        return "redirect:/client/{clientId}/mappings";
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