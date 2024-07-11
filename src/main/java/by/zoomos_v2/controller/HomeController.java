package by.zoomos_v2.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
@Controller
public class HomeController {
    @GetMapping("/")
    public String viewHomePage(Model model) {
        return "index"; // Возвращает главную страницу
    }
}
