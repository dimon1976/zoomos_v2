package by.zoomos_v2.controller;

import by.zoomos_v2.model.Client;
import by.zoomos_v2.service.ClientService;
import by.zoomos_v2.service.ConfigurationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/shop")
public class ShopController {

    private final ClientService clientService;
    private final ConfigurationService configurationService;


    public ShopController(ClientService clientService, ConfigurationService configurationService) {
        this.clientService = clientService;
        this.configurationService = configurationService;
    }


    @GetMapping("/{clientName}/settings")
    public String clientSettings(@PathVariable String clientName, Model model) {
        try {
            Client client = clientService.getClientByName(clientName);
            model.addAttribute("client", client);
            return "client-settings"; // Предполагаем, что это имя нашего нового шаблона
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", "Клиент не найден");
            return "error"; // Шаблон для отображения ошибок
        }
    }

    @GetMapping("/{clientName}/upload")
    public String getConfigurations(Model model, @PathVariable String clientName) {
        Client client = clientService.getClientByName(clientName);
        model.addAttribute("client", client);
        model.addAttribute("configurations", configurationService.getConfigurationsForClient(clientName));
        return "upload-settings";
    }

    @GetMapping("/{clientName}/delete-config/{id}")
    public String deleteConfigurations(Model model, @PathVariable String clientName, @PathVariable Long id) {
        configurationService.deleteConfiguration(id);
        Client client = clientService.getClientByName(clientName);
        model.addAttribute("client", client);
        model.addAttribute("configurations", configurationService.getConfigurationsForClient(clientName));
        return "redirect:/shop/" + clientName + "/upload-settings";
    }
}
