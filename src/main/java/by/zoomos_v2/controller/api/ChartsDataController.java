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
 * Без зависимостей от репозиториев
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
            // Здесь в будущем должен быть код получения данных из вашего источника
            // Сейчас используем заглушку с демо-данными

            // Определяем типы операций (замените на ваши реальные типы)
            List<String> types = Arrays.asList("IMPORT", "EXPORT", "PRODUCT_UPDATE", "PRICE_UPDATE");
            Map<String, String> descriptions = new HashMap<>();
            descriptions.put("IMPORT", "Импорт");
            descriptions.put("EXPORT", "Экспорт");
            descriptions.put("PRODUCT_UPDATE", "Обновление товаров");
            descriptions.put("PRICE_UPDATE", "Обновление цен");

            // Генерируем случайные данные для демонстрации
            List<Long> counts = Arrays.asList(15L, 8L, 3L, 5L);

            response.put("labels", types);
            response.put("data", counts);
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
            // Здесь в будущем должен быть код получения данных из вашего источника
            // Сейчас используем заглушку с демо-данными

            // Форматируем даты для отображения
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM");

            // Создаем метки для последних 7 дней
            List<String> labels = new ArrayList<>();
            List<Long> data = new ArrayList<>();

            LocalDate today = LocalDate.now();
            Random random = new Random();

            // Генерируем данные для последних 7 дней
            for (int i = 6; i >= 0; i--) {
                LocalDate date = today.minusDays(i);
                labels.add(date.format(formatter));

                // Случайное количество операций для демонстрации
                data.add((long) random.nextInt(10));
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