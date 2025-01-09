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
        return "clients";
    }

    @PostMapping("/add")
    public String addClient(@RequestParam String name, Model model) {
        try {
            Client client = clientService.addClient(name);
            return "redirect:/clients";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "clients";
        }
    }

    @GetMapping("/search")
    public String searchClient(@RequestParam String name, Model model) {
        try {
            Client client = clientService.getClientByName(name);
            model.addAttribute("client", client);
            return "clients";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "clients";
        }
    }
}
