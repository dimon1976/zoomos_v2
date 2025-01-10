package by.zoomos_v2.controller;

import by.zoomos_v2.model.Client;
import by.zoomos_v2.service.ClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/clients")
public class ClientsController {

    @Autowired
    private ClientService clientService;

    @GetMapping
    public String listClients(Model model) {
        List<Client> clients = clientService.getAllClients();
        model.addAttribute("clients", clients);
        return "/client/clients";
    }

    @PostMapping("/add")
    public String addClient(@RequestParam String name,
                            @RequestParam String entityType, // Добавляем параметр для типа сущности
                            Model model) {
        try {
            // Передаем выбранный тип сущности в сервис для обработки
            Client client = clientService.addClient(name, entityType);
            return "redirect:/clients";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "/client/clients";
        }
    }

    @GetMapping("/search")
    public String searchClient(@RequestParam String name, Model model) {
        try {
            Client client = clientService.getClientByName(name);
            model.addAttribute("client", client);
            return "/client/clients";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "/client/clients";
        }
    }
}
