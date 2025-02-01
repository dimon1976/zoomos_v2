package by.zoomos_v2.controller;

import by.zoomos_v2.aspect.LogExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.Optional;

@Slf4j
@Controller
@RequestMapping("/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    /**
     * Отображение страницы статистики операций
     *
     * @param startDate начальная дата периода
     * @param endDate   конечная дата периода
     * @param model     модель представления
     * @return название представления
     */
    @GetMapping("/operations")
    @LogExecution("Просмотр статистики операций")
    public String showOperationsStatistics(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            Model model) {

        log.debug("Загрузка статистики операций за период с {} по {}", startDate, endDate);

        // Если даты не указаны, используем текущий день
        LocalDate now = LocalDate.now();
        startDate = Optional.ofNullable(startDate).orElse(now);
        endDate = Optional.ofNullable(endDate).orElse(now);

        model.addAttribute("activeMenu", "statistics");
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        // TODO: Добавить реальные данные статистики

        return "statistics/operations";
    }

    /**
     * Отображение страницы статистики загрузок
     */
    @GetMapping("/uploads")
    @LogExecution("Просмотр статистики загрузок")
    public String showUploadsStatistics(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            Model model) {

        log.debug("Загрузка статистики загрузок за период с {} по {}", startDate, endDate);

        LocalDate now = LocalDate.now();
        startDate = Optional.ofNullable(startDate).orElse(now);
        endDate = Optional.ofNullable(endDate).orElse(now);

        model.addAttribute("activeMenu", "statistics");
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        // TODO: Добавить реальные данные статистики

        return "statistics/uploads";
    }

    /**
     * Отображение страницы статистики выгрузок
     */
    @GetMapping("/exports")
    @LogExecution("Просмотр статистики выгрузок")
    public String showExportsStatistics(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            Model model) {

        log.debug("Загрузка статистики выгрузок за период с {} по {}", startDate, endDate);

        LocalDate now = LocalDate.now();
        startDate = Optional.ofNullable(startDate).orElse(now);
        endDate = Optional.ofNullable(endDate).orElse(now);

        model.addAttribute("activeMenu", "statistics");
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        // TODO: Добавить реальные данные статистики

        return "statistics/exports";
    }
}