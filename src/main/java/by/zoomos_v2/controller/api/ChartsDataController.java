package by.zoomos_v2.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * REST контроллер для получения данных для графиков
 */
@RestController
@RequestMapping("/api/charts")
public class ChartsDataController {

    /**
     * Возвращает данные о распределении операций по типам
     */
    @GetMapping("/operation-types")
    public ResponseEntity<Map<String, Object>> getOperationTypeCounts() {
        Map<String, Object> response = new HashMap<>();

        try {
            // ПРИМЕЧАНИЕ: В реальном приложении здесь должен быть запрос к базе данных
            // Сейчас используем тестовые данные для демонстрации

            List<String> labels = Arrays.asList("IMPORT", "EXPORT", "PRODUCT_UPDATE", "PRICE_UPDATE");
            List<Integer> data = Arrays.asList(15, 8, 3, 5);

            Map<String, String> descriptions = new HashMap<>();
            descriptions.put("IMPORT", "Импорт");
            descriptions.put("EXPORT", "Экспорт");
            descriptions.put("PRODUCT_UPDATE", "Обновление товаров");
            descriptions.put("PRICE_UPDATE", "Обновление цен");

            response.put("labels", labels);
            response.put("data", data);
            response.put("descriptions", descriptions);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", "Ошибка при получении данных: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Возвращает данные о количестве операций за последние 7 дней
     */
    @GetMapping("/operations-timeline")
    public ResponseEntity<Map<String, Object>> getOperationsTimeline() {
        Map<String, Object> response = new HashMap<>();

        try {
            // ПРИМЕЧАНИЕ: В реальном приложении здесь должен быть запрос к базе данных
            // Сейчас используем тестовые данные для демонстрации

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM");
            List<String> labels = new ArrayList<>();
            List<Integer> data = new ArrayList<>();

            LocalDate today = LocalDate.now();
            Random random = new Random();

            for (int i = 6; i >= 0; i--) {
                LocalDate date = today.minusDays(i);
                labels.add(date.format(formatter));
                data.add(random.nextInt(10)); // Случайное число от 0 до 9
            }

            response.put("labels", labels);
            response.put("data", data);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", "Ошибка при получении данных: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}