package by.zoomos_v2.controller;

import by.zoomos_v2.model.Client;
import by.zoomos_v2.model.MappingConfig;
import by.zoomos_v2.service.ClientService;
import by.zoomos_v2.service.MappingConfigService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/shop")
public class MappingConfigController {

    @Autowired
    private MappingConfigService mappingConfigService;
    @Autowired
    private ClientService clientService;

    @GetMapping("/{clientName}/mapping/add-config")
    public String showAddConfigForm(@PathVariable String clientName, Model model) {
        try {
            Client client = clientService.getClientByName(clientName);
            model.addAttribute("mappingConfig", new MappingConfig());
            model.addAttribute("client", client);
            model.addAttribute("currentClientName", clientName);
            return "add-config";
        } catch (IllegalArgumentException e) {
            // Если клиент не найден, возвращаем ошибку
            model.addAttribute("error", "Клиент не найден");
            return "error"; // Шаблон для отображения ошибок
        }
    }

    @PostMapping("/{clientName}/mapping/add-config")
    public String addConfig(@PathVariable String clientName, @Valid @ModelAttribute("mappingConfig") MappingConfig mappingConfig, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            // Если есть ошибки валидации, возвращаем пользователя обратно на форму с ошибками
            model.addAttribute("client", clientService.getClientByName(clientName));
            model.addAttribute("currentClientName", clientName);
            return "add-config";
        }

        try {
            Client client = clientService.getClientByName(clientName);
            mappingConfig.setClient(client);
            mappingConfigService.saveMappingConfig(mappingConfig);
            return "redirect:/shop/" + clientName + "/mapping/add-config?success";
        } catch (IllegalArgumentException e) {
            // Если клиент не найден, возвращаем ошибку
            model.addAttribute("error", "Клиент не найден");
            return "error"; // Шаблон для отображения ошибок
        }
    }
}
