package by.zoomos_v2.controller;

import by.zoomos_v2.service.ClientService;
import by.zoomos_v2.model.Client;
import by.zoomos_v2.aspect.LogExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Контроллер для управления магазинами (клиентами).
 * Обеспечивает веб-интерфейс для работы с данными магазинов.
 */
@Slf4j
@Controller
@RequestMapping("/client")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

        /**
     * Отображает форму настроек клиента
     * @param id идентификатор клиента
     * @param model модель для передачи данных в представление
     * @return имя представления
     */
    @GetMapping("/{id}/settings")
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
     * @param redirectAttributes атрибуты для передачи сообщений
     * @return редирект на список клиентов
     */
    @PostMapping("/settings/save")
    @LogExecution("Сохранение настроек магазина")
    public String saveClientSettings(@ModelAttribute("client") Client client,
                                     @RequestParam(value = "active", defaultValue = "false") boolean active,
                                     RedirectAttributes redirectAttributes) {
        log.debug("Сохранение настроек магазина: {}", client);
        try {
            if (client.getId() == null) {
                client.setActive(active);
                clientService.createClient(client);
                redirectAttributes.addFlashAttribute("success", "Магазин успешно создан");
            } else {
                client.setActive(active);
                clientService.updateClient(client);
                redirectAttributes.addFlashAttribute("success", "Настройки магазина обновлены");
            }
        } catch (Exception e) {
            log.error("Ошибка при сохранении магазина: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка при сохранении магазина: " + e.getMessage());
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
        log.debug("Отображение формы создания нового магазина");
        Client client = new Client();
        client.setActive(true); // устанавливаем значение по умолчанию
        model.addAttribute("client", client);
        return "client/client-settings";
    }

    /**
     * Обрабатывает удаление магазина
     */
    @PostMapping("/delete/{id}")
    @LogExecution("Удаление магазина")
    public String deleteClient(@PathVariable Long id,
                               RedirectAttributes redirectAttributes) {
        log.debug("Запрос на удаление магазина с ID: {}", id);
        try {
            clientService.deleteClient(id);
            redirectAttributes.addFlashAttribute("success", "Магазин успешно удален");
        } catch (Exception e) {
            log.error("Ошибка при удалении магазина: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка при удалении магазина: " + e.getMessage());
        }
        return "redirect:/clients";
    }
}
