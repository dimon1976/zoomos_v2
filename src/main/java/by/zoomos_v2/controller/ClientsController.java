package by.zoomos_v2.controller;

import by.zoomos_v2.model.Client;
import by.zoomos_v2.service.ClientService;
import by.zoomos_v2.aspect.LogExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Контроллер для управления клиентами через веб-интерфейс.
 * Обеспечивает функционал просмотра, создания, редактирования и удаления клиентов.
 */
@Slf4j
@Controller
@RequestMapping("/clients")
@RequiredArgsConstructor
public class ClientsController {

    private final ClientService clientService;

    /**
     * Отображает список всех клиентов
     * @param model модель для передачи данных в представление
     * @return имя представления
     */
    @GetMapping
    public String showClients(Model model) {
        log.debug("Запрошен список клиентов");
        model.addAttribute("clients", clientService.getAllClients());
        return "client/clients";
    }

    /**
     * Отображает форму настроек клиента
     * @param id идентификатор клиента
     * @param model модель для передачи данных в представление
     * @return имя представления
     */
    @GetMapping("/settings/{id}")
    @LogExecution("Просмотр настроек клиента")
    public String showClientSettings(@PathVariable Long id, Model model) {
        log.debug("Запрошены настройки клиента с ID: {}", id);
        Client client = clientService.getClientById(id);
        model.addAttribute("client", client);
        return "client/client-settings";
    }

    /**
     * Обрабатывает сохранение настроек клиента
     * @param client объект клиента с обновленными данными
     * @param redirectAttributes атрибуты для передачи сообщений
     * @return редирект на список клиентов
     */
    @PostMapping("/settings/save")
    @LogExecution("Сохранение настроек клиента")
    public String saveClientSettings(@ModelAttribute Client client,
                                     RedirectAttributes redirectAttributes) {
        try {
            log.debug("Сохранение настроек клиента с ID: {}", client.getId());
            clientService.updateClient(client);
            redirectAttributes.addFlashAttribute("success",
                    "Настройки клиента успешно сохранены");
        } catch (Exception e) {
            log.error("Ошибка при сохранении настроек клиента: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка при сохранении настроек: " + e.getMessage());
        }
        return "redirect:/clients";
    }

    /**
     * Отображает форму создания нового клиента
     * @param model модель для передачи данных в представление
     * @return имя представления
     */
    @GetMapping("/new")
    public String showNewClientForm(Model model) {
        log.debug("Запрошена форма создания нового клиента");
        model.addAttribute("client", new Client());
        return "client/client-settings";
    }

    /**
     * Обрабатывает удаление клиента
     * @param id идентификатор клиента
     * @param redirectAttributes атрибуты для передачи сообщений
     * @return редирект на список клиентов
     */
    @PostMapping("/delete/{id}")
    @LogExecution("Удаление клиента")
    public String deleteClient(@PathVariable Long id,
                               RedirectAttributes redirectAttributes) {
        try {
            log.debug("Запрос на удаление клиента с ID: {}", id);
            clientService.deleteClient(id);
            redirectAttributes.addFlashAttribute("success", "Клиент успешно удален");
        } catch (Exception e) {
            log.error("Ошибка при удалении клиента: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка при удалении клиента: " + e.getMessage());
        }
        return "redirect:/clients";
    }
}
