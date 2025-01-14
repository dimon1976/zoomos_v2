package by.zoomos_v2.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HomeController {
    private static final Logger logger = LoggerFactory.getLogger(HomeController.class);

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/tools")
    public String tools() {
        return "tools"; // Пока просто возвращаем заглушку
    }

    @GetMapping("/search")
    public String search(@RequestParam String query) {
        // Здесь должна быть логика поиска, пока просто вернем главную
        return "index";
    }
}
