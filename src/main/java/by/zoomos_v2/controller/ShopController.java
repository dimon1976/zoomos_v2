package by.zoomos_v2.controller;

import by.zoomos_v2.mapping.ClientMappingConfig;
import by.zoomos_v2.model.Client;
import by.zoomos_v2.service.ClientService;
import by.zoomos_v2.service.ConfigurationService;
import by.zoomos_v2.service.MappingConfigService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/shop")
public class ShopController {

    private final ClientService clientService;
    private final MappingConfigService mappingConfigService;


    public ShopController(ClientService clientService, MappingConfigService mappingConfigService) {
        this.clientService = clientService;
        this.mappingConfigService = mappingConfigService;
    }


    // Страница с настройками клиента
    @GetMapping("/{clientName}/settings")
    public String clientSettings(@PathVariable String clientName, Model model) {
        try {
            Client client = clientService.getClientByName(clientName);
            model.addAttribute("client", client);
            return "/client/client-settings"; // Шаблон настроек клиента
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", "Клиент не найден");
            return "error"; // Шаблон ошибки
        }
    }

    // Страница загрузки конфигураций для клиента
    @GetMapping("/{clientName}/upload")
    public String getConfigurations(@PathVariable String clientName, Model model) {
        Client client = clientService.getClientByName(clientName);

        if (client == null) {
            model.addAttribute("error", "Клиент не найден");
            return "error"; // Шаблон ошибки
        }

        // Получаем все маппинги для клиента
        List<ClientMappingConfig> mappingConfigs = mappingConfigService.getAllMappingConfigsForClient(client.getId());

        // Добавляем в модель
        model.addAttribute("client", client);
        model.addAttribute("configurations", mappingConfigs); // Передаем список маппингов
        return "/client/upload-settings"; // Шаблон с настройками загрузки
    }
}
