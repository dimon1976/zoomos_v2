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
@RequestMapping("/shops")
@RequiredArgsConstructor
public class ShopController {

    private final ClientService clientService;

    /**
     * Отображает список всех магазинов
     */
    @GetMapping
    public String showShops(Model model) {
        log.debug("Запрошен список магазинов");
        model.addAttribute("shops", clientService.getAllClients());
        return "client/clients"; // используем существующий шаблон
    }

    /**
     * Отображает страницу настроек магазина
     */
    @GetMapping("/settings/{id}")
    @LogExecution("Просмотр настроек магазина")
    public String showShopSettings(@PathVariable Long id, Model model) {
        log.debug("Запрошены настройки магазина с ID: {}", id);
        try {
            Client client = clientService.getClientById(id);
            model.addAttribute("shop", client);
            return "client/client-settings";
        } catch (Exception e) {
            log.error("Ошибка при получении настроек магазина: {}", e.getMessage(), e);
            model.addAttribute("error", "Не удалось загрузить настройки магазина");
            return "error";
        }
    }

    /**
     * Обрабатывает сохранение настроек магазина
     */
    @PostMapping("/settings/save")
    @LogExecution("Сохранение настроек магазина")
    public String saveShopSettings(@ModelAttribute("shop") Client client,
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
        return "redirect:/shops";
    }

    /**
     * Отображает форму создания нового магазина
     */
    @GetMapping("/new")
    public String showNewShopForm(Model model) {
        log.debug("Отображение формы создания нового магазина");
        Client client = new Client();
        client.setActive(true); // устанавливаем значение по умолчанию
        model.addAttribute("shop", client);
        return "client/client-settings";
    }

    /**
     * Обрабатывает удаление магазина
     */
    @PostMapping("/delete/{id}")
    @LogExecution("Удаление магазина")
    public String deleteShop(@PathVariable Long id,
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
        return "redirect:/shops";
    }
}
