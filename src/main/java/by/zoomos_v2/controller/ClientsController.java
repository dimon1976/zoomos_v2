package by.zoomos_v2.controller;

import by.zoomos_v2.service.ClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

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
}
