package by.zoomos_v2.controller;

import by.zoomos_v2.aspect.LogExecution;
import by.zoomos_v2.exception.HomePageException;
import by.zoomos_v2.service.client.ClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.service.OperationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
@Slf4j
@Controller
@RequiredArgsConstructor
public class HomeController {
    private final ClientService clientService;

    /**
     * Отображение главной страницы
     */
    @GetMapping("/")
    @LogExecution("Просмотр главной страницы")
    public String index(Model model) {
        log.debug("Загрузка главной страницы");
        try {
            model.addAttribute("clientsCount", clientService.getAllClients().size());
            return "index";
        } catch (Exception e) {
            log.error("Ошибка при загрузке главной страницы: {}", e.getMessage(), e);
            throw new HomePageException("Ошибка загрузки главной страницы", e);
        }
    }
}
