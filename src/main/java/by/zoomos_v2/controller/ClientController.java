package by.zoomos_v2.controller;

import by.zoomos_v2.exception.TabDataException;
import by.zoomos_v2.service.client.ClientService;
import by.zoomos_v2.model.Client;
import by.zoomos_v2.aspect.LogExecution;
import by.zoomos_v2.service.file.input.service.FileUploadService;
import by.zoomos_v2.service.file.metadata.FileMetadataService;
import by.zoomos_v2.service.mapping.ExportConfigService;
import by.zoomos_v2.service.mapping.MappingConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

/**
 * Контроллер для управления клиентами.
 * Обеспечивает функционал просмотра, создания, редактирования и удаления клиентов,
 * а также работу с настройками и dashboard клиента.
 */
@Slf4j
@Controller
@RequestMapping
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;
    private final FileUploadService fileUploadService;
    private final MappingConfigService mappingConfigService;
    private final ExportConfigService exportConfigService;
    private final FileMetadataService fileMetadataService;

    /**
     * Отображает список всех клиентов
     * @param model модель для передачи данных в представление
     * @return имя представления
     */
    @GetMapping("/clients")
    @LogExecution("Просмотр списка магазинов")
    public String showClients(Model model) {
        log.debug("Запрошен список клиентов");
        try {
            model.addAttribute("clients", clientService.getAllClients());
            return "client/clients";
        } catch (Exception e) {
            log.error("Ошибка при получении списка магазинов: {}", e.getMessage(), e);
            model.addAttribute("error", "Не удалось загрузить список магазинов");
            return "error";
        }
    }

    /**
     * Отображает dashboard клиента
     * @param id идентификатор клиента
     * @param model модель для передачи данных в представление
     * @return имя представления dashboard
     */
    @GetMapping("/client/{id}/dashboard")
    @LogExecution("Просмотр панели управления магазина")
    public String showDashboard(@PathVariable Long id, Model model) {
        log.debug("Запрошен dashboard магазина с ID: {}", id);
        try {
            Client client = clientService.getClientById(id);
            model.addAttribute("client", client);

            // Данные для вкладки загрузки
            model.addAttribute("files", fileMetadataService.getFilesByClientId(id));
            model.addAttribute("mappings", mappingConfigService.getMappingsForClient(id));

            // Данные для вкладки экспорта
            model.addAttribute("configs", exportConfigService.getConfigsByClientId(id));

            return "client/dashboard";
        } catch (Exception e) {
            log.error("Ошибка при загрузке dashboard магазина: {}", e.getMessage(), e);
            model.addAttribute("error", "Не удалось загрузить dashboard магазина");
            return "error";
        }
    }

    /**
     * Отображает форму настроек клиента
     * @param id идентификатор клиента
     * @param model модель для передачи данных в представление
     * @return имя представления
     */
    @GetMapping("/client/{id}/settings")
    @LogExecution("Просмотр настроек магазина")
    public String showClientSettings(@PathVariable Long id, Model model) {
        log.debug("Запрошены настройки магазина с ID: {}", id);
        try {
            Client client = clientService.getClientById(id);
            model.addAttribute("client", client);
            return "client/client-settings";
        } catch (Exception e) {
            log.error("Ошибка при получении настроек магазина: {}", e.getMessage(), e);
            model.addAttribute("error", "Не удалось загрузить настройки магазина");
            return "error";
        }
    }

    /**
     * Обрабатывает сохранение настроек клиента
     * @param client объект клиента с обновленными данными
     * @param active статус активности клиента
     * @param redirectAttributes атрибуты для передачи сообщений
     * @return редирект на соответствующую страницу
     */
    @PostMapping("/client/{id}/settings/save")
    @LogExecution("Сохранение настроек магазина")
    public String saveClientSettings(@PathVariable Long id,
                                     @ModelAttribute("client") Client client,
                                     @RequestParam(value = "active", defaultValue = "false") boolean active,
                                     RedirectAttributes redirectAttributes) {
        log.debug("Сохранение настроек магазина с ID {}: {}", id, client);
        try {
            client.setId(id); // Убеждаемся, что ID соответствует URL
            client.setActive(active);

            if (id == null) {
                clientService.createClient(client);
                redirectAttributes.addFlashAttribute("success", "Магазин успешно создан");
                return "redirect:/clients";
            } else {
                clientService.updateClient(client);
                redirectAttributes.addFlashAttribute("success", "Настройки магазина обновлены");
                return "redirect:/client/" + id + "/dashboard";
            }
        } catch (Exception e) {
            log.error("Ошибка при сохранении магазина: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка при сохранении магазина: " + e.getMessage());
            return "redirect:/client/" + id + "/settings";
        }
    }

    /**
     * Отображает форму создания нового клиента
     * @param model модель для передачи данных в представление
     * @return имя представления
     */
    @GetMapping("/client/new")
    @LogExecution("Создание нового магазина")
    public String showNewClientForm(Model model) {
        log.debug("Отображение формы создания нового магазина");
        Client client = new Client();
        client.setActive(true);
        model.addAttribute("client", client);
        return "client/client-settings";
    }

    /**
     * Обрабатывает создание нового клиента
     * @param client объект клиента с данными
     * @param redirectAttributes атрибуты для передачи сообщений
     * @return редирект на список клиентов
     */
    @PostMapping("/client/new")
    @LogExecution("Создание нового магазина")
    public String createClient(@ModelAttribute Client client,
                               @RequestParam(value = "active", defaultValue = "false") boolean active,
                               RedirectAttributes redirectAttributes) {
        log.debug("Создание нового магазина: {}", client);
        try {
            client.setActive(active);
            clientService.createClient(client);
            redirectAttributes.addFlashAttribute("success", "Магазин успешно создан");
            return "redirect:/clients";
        } catch (Exception e) {
            log.error("Ошибка при создании магазина: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Ошибка при создании магазина: " + e.getMessage());
            return "redirect:/client/new";
        }
    }

    /**
     * Обрабатывает удаление клиента
     * @param id идентификатор клиента
     * @param redirectAttributes атрибуты для передачи сообщений
     * @return редирект на список клиентов
     */
    @PostMapping("/client/{id}/delete")
    @LogExecution("Удаление магазина")
    public String deleteClient(@PathVariable Long id,
                               RedirectAttributes redirectAttributes) {
        log.debug("Запрос на удаление магазина с ID: {}", id);
        try {
            clientService.deleteClient(id);
            redirectAttributes.addFlashAttribute("success", "Магазин успешно удален");
        } catch (Exception e) {
            //TODO Починить удаление магазина (рекурсивно)
            log.error("Ошибка при удалении магазина: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка при удалении магазина: " + e.getMessage());
        }
        return "redirect:/clients";
    }

    /**
     * Загрузка данных для вкладки загрузки файлов
     */
    @GetMapping("/client/{id}/upload-data")
    @ResponseBody
    public Map<String, Object> getUploadTabData(@PathVariable Long id) {
        log.debug("Загрузка данных для вкладки загрузки файлов клиента {}", id);
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("files", fileUploadService.getRecentFiles(id));
            data.put("mappings", mappingConfigService.getMappingsForClient(id));
            return data;
        } catch (Exception e) {
            log.error("Ошибка при загрузке данных для вкладки загрузки: {}", e.getMessage(), e);
            throw new TabDataException("Ошибка при загрузке данных вкладки", e);
        }
    }
}
