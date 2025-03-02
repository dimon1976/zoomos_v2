package by.zoomos_v2.controller.api;

import by.zoomos_v2.model.Client;
import by.zoomos_v2.model.enums.OperationType;
import by.zoomos_v2.model.operation.BaseOperation;
import by.zoomos_v2.service.client.ClientService;
import by.zoomos_v2.service.statistics.OperationStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * REST-контроллер для обработки AJAX запросов операций с оптимизацией производительности
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class OperationsRestController {

    private final OperationStatsService operationStatsService;
    private final ClientService clientService;

    // Кэш для хранения недавних результатов запросов (тип запроса -> время последнего обновления -> данные)
    private final Map<String, Map.Entry<LocalDateTime, List<Map<String, Object>>>> resultsCache = new ConcurrentHashMap<>();

    // Период действия кэша в секундах
    private static final long CACHE_TTL_SECONDS = 10;

    /**
     * Возвращает список операций с учетом фильтрации по типу
     * Реализует кэширование для улучшения производительности
     *
     * @param type  Тип операции для фильтрации (IMPORT, EXPORT, ALL)
     * @param limit Максимальное количество операций (по умолчанию 10)
     * @return Список операций в формате JSON
     */
    @GetMapping("/operations")
    public List<Map<String, Object>> getOperations(
            @RequestParam(required = false, defaultValue = "ALL") String type,
            @RequestParam(required = false, defaultValue = "10") int limit) {

        String cacheKey = type + "_" + limit;

        // Проверяем кэш
        Map.Entry<LocalDateTime, List<Map<String, Object>>> cachedResult = resultsCache.get(cacheKey);
        if (cachedResult != null) {
            LocalDateTime cacheTime = cachedResult.getKey();
            if (cacheTime.plusSeconds(CACHE_TTL_SECONDS).isAfter(LocalDateTime.now())) {
                log.debug("Возвращаем данные из кэша для типа: {}, лимит: {}", type, limit);
                return cachedResult.getValue();
            }
        }

        log.debug("Получение операций для AJAX. Тип: {}, лимит: {}", type, limit);

        try {
            // Определяем тип операции для фильтрации
            OperationType operationType = null;
            if (!"ALL".equals(type)) {
                operationType = OperationType.valueOf(type);
            }

            // Кэшируем клиентов для оптимизации множественных запросов
            List<Client> clients = clientService.getAllClients();
            Map<Long, String> clientNames = clients.stream()
                    .collect(Collectors.toMap(Client::getId, Client::getName, (old, newVal) -> old));

            // Получаем все операции оптимизированным методом для снижения нагрузки
            List<? extends BaseOperation> operations = operationStatsService.getClientOperations(null, operationType);

            // Формируем результат с учетом лимита и оптимизируем обработку
            List<Map<String, Object>> result = operations.stream()
                    .sorted(Comparator.comparing(BaseOperation::getStartTime).reversed())
                    .limit(limit)
                    .map(op -> mapOperationToDto(op, clientNames))
                    .collect(Collectors.toList());

            // Сохраняем результат в кэш
            resultsCache.put(cacheKey, new AbstractMap.SimpleEntry<>(LocalDateTime.now(), result));

            return result;
        } catch (Exception e) {
            log.error("Ошибка при получении операций: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Преобразует объект операции в формат для REST API
     * Оптимизирован для снижения нагрузки на CPU
     */
    private Map<String, Object> mapOperationToDto(BaseOperation op, Map<Long, String> clientNames) {
        Map<String, Object> result = new HashMap<>(10); // Устанавливаем начальную емкость для оптимизации
        result.put("id", op.getId());
        result.put("clientId", op.getClientId());
        result.put("clientName", clientNames.getOrDefault(op.getClientId(), "Неизвестный"));

        // Корректное преобразование enum-значений
        result.put("type", new HashMap<String, Object>(2) {{
            put("name", op.getType().name());
            put("description", op.getType().getDescription());
        }});

        result.put("status", new HashMap<String, Object>(2) {{
            put("name", op.getStatus().name());
            put("description", op.getStatus().getDescription());
        }});

        // Форматирование дат в ISO формате для корректной обработки в JS
        result.put("startTime", op.getStartTime() != null ?
                op.getStartTime().toString() : null);
        result.put("endTime", op.getEndTime() != null ?
                op.getEndTime().toString() : null);

        result.put("sourceIdentifier", op.getSourceIdentifier());
        result.put("processedRecords", op.getProcessedRecords() != null ?
                op.getProcessedRecords() : 0);
        result.put("totalRecords", op.getTotalRecords() != null ?
                op.getTotalRecords() : 0);
        return result;
    }

    /**
     * Очищает кэш результатов.
     * Может быть вызван по заданному расписанию или по событию обновления данных
     */
    public void clearCache() {
        log.debug("Очистка кэша операций");
        resultsCache.clear();
    }
}