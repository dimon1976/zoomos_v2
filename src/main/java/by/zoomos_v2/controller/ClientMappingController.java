package by.zoomos_v2.controller;

import by.zoomos_v2.mapping.ClientMappingConfig;
import by.zoomos_v2.model.Client;
import by.zoomos_v2.service.ClientService;
import by.zoomos_v2.service.MappingConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/shop/{clientName}/mapping")
public class ClientMappingController {

    @Autowired
    private MappingConfigService mappingConfigService;

    @Autowired
    private ClientService clientService;

    // Получение всех маппингов для клиента
    @GetMapping
    public String getMappingConfig(@PathVariable String clientName, Model model) {
        Client client = clientService.getClientByName(clientName);  // Ищем клиента по имени

        if (client == null) {
            model.addAttribute("error", "Client not found");
            return "error";  // Шаблон ошибки, если клиент не найден
        }

        // Загружаем все маппинги для клиента
        List<ClientMappingConfig> mappingConfigs = mappingConfigService.getAllMappingConfigsForClient(client.getId());

        model.addAttribute("client", client);
        model.addAttribute("mappingConfigs", mappingConfigs);
        return "client-mapping";  // Шаблон для редактирования конфигураций
    }

    // Сохранение нового маппинга для клиента
    @PostMapping("/save")
    public String saveMappingConfig(@PathVariable String clientName,
                                    @RequestParam String configName,  // Новый параметр для имени конфигурации
                                    @RequestParam String type,
                                    @RequestParam String mappingJson,
                                    Model model) {
        Client client = clientService.getClientByName(clientName);  // Ищем клиента по имени

        if (client == null) {
            model.addAttribute("error", "Client not found");
            return "error";  // Шаблон ошибки
        }

        // Сохраняем новый маппинг с указанным названием
        mappingConfigService.saveMappingConfig(client, configName, type, mappingJson);
        model.addAttribute("success", "Mapping configuration saved successfully.");
        return "redirect:/shop/{clientName}/mapping";  // Перенаправление на страницу маппинга клиента
    }

    // Редактирование маппинга для конкретного типа
    @GetMapping("/edit/{type}")
    public String editMappingConfig(@PathVariable String clientName, @PathVariable String type, Model model) {
        Client client = clientService.getClientByName(clientName);  // Ищем клиента по имени

        if (client == null) {
            model.addAttribute("error", "Client not found");
            return "error";  // Шаблон ошибки
        }

        String mappingConfig = mappingConfigService.getMappingConfigByType(client.getId(), type);

        model.addAttribute("client", client);
        model.addAttribute("type", type);
        model.addAttribute("mappingConfig", mappingConfig);
        return "edit-mapping";  // Шаблон для редактирования конкретного маппинга
    }

    // Обновление маппинга для конкретного типа
    @PostMapping("/update/{type}")
    public String updateMappingConfig(@PathVariable String clientName, @PathVariable String type,
                                      @RequestParam String configName, // Параметр для имени конфигурации
                                      @RequestParam String mappingJson, Model model) {
        Client client = clientService.getClientByName(clientName);  // Ищем клиента по имени

        if (client == null) {
            model.addAttribute("error", "Client not found");
            return "error";  // Шаблон ошибки
        }

        mappingConfigService.updateMappingConfig(client.getId(), type, configName, mappingJson);
        model.addAttribute("success", "Mapping configuration updated successfully.");
        return "redirect:/shop/{clientName}/mapping";  // Перенаправление на страницу маппинга клиента
    }

    // Удаление маппинга
    @GetMapping("/delete/{configId}")
    public String deleteMappingConfig(@PathVariable String clientName, @PathVariable Long configId, Model model) {
        Client client = clientService.getClientByName(clientName);  // Ищем клиента по имени

        if (client == null) {
            model.addAttribute("error", "Client not found");
            return "error";  // Шаблон ошибки
        }

        mappingConfigService.deleteMappingConfig(configId);
        model.addAttribute("success", "Mapping configuration deleted successfully.");
        return "redirect:/shop/{clientName}/mapping";  // Перенаправление на страницу с маппингами
    }
}
