package by.zoomos_v2.controller;

import by.zoomos_v2.aspect.LogExecution;
import by.zoomos_v2.exception.HomePageException;
import by.zoomos_v2.service.client.ClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Collections;

@Slf4j
@Controller
@RequiredArgsConstructor
public class HomeController {
    private final ClientService clientService;

    /**
     * Отображение главной страницы
     * @param model объект Model для передачи данных в представление
     * @return название представления
     * @throws HomePageException при ошибке загрузки данных
     */
    @GetMapping("/")
    @LogExecution("Просмотр главной страницы")
    public String index(Model model) {
        log.debug("Загрузка главной страницы");
        try {
            // Добавляем информацию об активном меню
            model.addAttribute("activeMenu", "home");

            // Базовая статистика
            model.addAttribute("activeClientsCount", clientService.getActiveClientsCount());
            model.addAttribute("todayUploadsCount", 0); // TODO: Добавить сервис статистики
            model.addAttribute("todayExportsCount", 0); // TODO: Добавить сервис статистики
            model.addAttribute("activeOperationsCount", 0); // TODO: Добавить сервис статистики

            // Последние операции
            model.addAttribute("recentOperations", Collections.emptyList()); // TODO: Добавить сервис операций

            return "index";
        } catch (Exception e) {
            log.error("Ошибка при загрузке главной страницы: {}", e.getMessage(), e);
            throw new HomePageException("Ошибка загрузки главной страницы", e);
        }
    }
}
