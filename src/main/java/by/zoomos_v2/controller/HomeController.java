package by.zoomos_v2.controller;

import by.zoomos_v2.DTO.dashboard.SystemResourcesDTO;
import by.zoomos_v2.aspect.LogExecution;
import by.zoomos_v2.exception.HomePageException;
import by.zoomos_v2.model.Client;
import by.zoomos_v2.model.enums.OperationStatus;
import by.zoomos_v2.model.enums.OperationType;
import by.zoomos_v2.model.operation.BaseOperation;
import by.zoomos_v2.model.operation.ImportOperation;
import by.zoomos_v2.service.client.ClientService;
import by.zoomos_v2.service.statistics.OperationStatsService;
import by.zoomos_v2.service.statistics.StatisticsProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Контроллер для главной страницы приложения с оптимизацией производительности
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class HomeController {
    private final ClientService clientService;
    private final OperationStatsService operationStatsService;
    private final StatisticsProcessor statisticsProcessor;

    // Для кэширования системных ресурсов
    private LocalDateTime lastResourceUpdate = LocalDateTime.now().minusMinutes(10);
    private SystemResourcesDTO cachedSystemResources = null;

    // Для кэширования данных статистики
    private LocalDateTime lastStatsUpdate = LocalDateTime.now().minusMinutes(10);
    private Map<String, Object> cachedStats = null;

    // Период обновления кэша в секундах
    @Value("${dashboard.cache.ttl:30}")
    private int cacheTtlSeconds;

    /**
     * Отображает главную страницу с обзорной информацией и статистикой
     * Оптимизировано для снижения нагрузки на CPU
     *
     * @param model Модель для передачи данных в представление
     * @return Название представления для отображения
     */
    @GetMapping("/")
    @LogExecution("Просмотр главной страницы")
    public String index(Model model) {
        try {
            log.debug("Подготовка данных для главной страницы");

            // Проверяем, нужно ли обновить кэш
            if (cachedStats == null ||
                    lastStatsUpdate.plusSeconds(cacheTtlSeconds).isBefore(LocalDateTime.now())) {
                // Обновляем кэш
                updateStats();
            }

            // Добавляем кэшированные данные в модель
            model.addAllAttributes(cachedStats);

            // Получаем системные ресурсы (обновляются отдельно, т.к. их можно обновлять чаще)
            model.addAttribute("systemResources", getSystemResources());

            log.debug("Данные для главной страницы успешно подготовлены");
            return "index";
        } catch (Exception e) {
            log.error("Ошибка при загрузке главной страницы: {}", e.getMessage(), e);
            throw new HomePageException("Ошибка загрузки главной страницы", e);
        }
    }

    /**
     * Обновляет кэшированные данные статистики
     * Вызывается при истечении времени кэша или по расписанию
     */
    private synchronized void updateStats() {
        log.debug("Обновление кэшированных данных статистики");

        // Создаем новый кэш
        Map<String, Object> newStats = new HashMap<>();

        try {
            // Загружаем данные о клиентах (относительно легкая операция)
            List<Client> allClients = clientService.getAllClients();
            newStats.put("clientsCount", allClients.size());
            newStats.put("activeClientsCount", allClients.stream()
                    .filter(Client::isActive)
                    .count());

            // Загружаем операции с лимитом для уменьшения нагрузки
            List<? extends BaseOperation> recentOperations = getRecentOperations(50);

            // Базовая статистика
            LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
            newStats.put("todayUploadsCount", countOperationsByTypeAndDate(recentOperations, OperationType.IMPORT, todayStart));
            newStats.put("todayExportsCount", countOperationsByTypeAndDate(recentOperations, OperationType.EXPORT, todayStart));
            newStats.put("activeOperationsCount", countActiveOperations(recentOperations));

            // Получаем данные для отображения в таблице (только самые последние)
            List<Map<String, Object>> operationsForDisplay = getRecentOperationsWithClientInfo(
                    recentOperations.stream()
                            .sorted(Comparator.comparing(BaseOperation::getStartTime).reversed())
                            .limit(10)
                            .collect(Collectors.toList()),
                    allClients);
            newStats.put("recentOperations", operationsForDisplay);

            // Формируем данные для диаграмм
            newStats.put("operationTypeCounts", getOperationTypeCounts(recentOperations));
            newStats.put("operationsTimeline", getOperationsTimeline(recentOperations));

            // Обновляем кэш
            cachedStats = newStats;
            lastStatsUpdate = LocalDateTime.now();

            log.debug("Кэшированные данные успешно обновлены");
        } catch (Exception e) {
            log.error("Ошибка при обновлении кэша статистики: {}", e.getMessage(), e);
            // Если обновление не удалось, оставляем старые данные
            if (cachedStats == null) {
                cachedStats = new HashMap<>();
            }
        }
    }

    /**
     * Возвращает актуальные данные о системных ресурсах
     * Обновляет их не чаще раза в 5 секунд для снижения нагрузки
     */
    private SystemResourcesDTO getSystemResources() {
        // Обновляем данные о ресурсах только раз в 5 секунд
        if (cachedSystemResources == null ||
                lastResourceUpdate.plusSeconds(5).isBefore(LocalDateTime.now())) {

            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            int memoryUsagePercentage = (int) ((usedMemory * 100L) / maxMemory);

            cachedSystemResources = SystemResourcesDTO.builder()
                    .peakMemoryUsage(statisticsProcessor.formatBytes(usedMemory))
                    .currentMemoryUsage(statisticsProcessor.formatBytes(usedMemory))
                    .memoryUsagePercentage(memoryUsagePercentage)
                    .diskUsagePercentage(30) // Заглушка, в реальном приложении нужно вычислять
                    .lastUpdated(LocalDateTime.now())
                    .build();

            lastResourceUpdate = LocalDateTime.now();
        }

        return cachedSystemResources;
    }

    /**
     * Запланированное обновление кэша каждые 30 секунд
     */
    @Scheduled(fixedRateString = "${dashboard.cache.update:30000}")
    public void scheduledCacheUpdate() {
        try {
            updateStats();
            log.debug("Выполнено плановое обновление кэша");
        } catch (Exception e) {
            log.error("Ошибка при плановом обновлении кэша: {}", e.getMessage(), e);
        }
    }

    /**
     * Получает ограниченное количество последних операций
     * Оптимизировано для снижения нагрузки
     */
    private List<? extends BaseOperation> getRecentOperations(int limit) {
        // Получаем только нужное количество операций, чтобы не загружать всю базу
        return operationStatsService.getClientOperations(null, null).stream()
                .sorted(Comparator.comparing(BaseOperation::getStartTime).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    // Остальные методы остаются без изменений...

    /**
     * Формирует список последних операций с дополнительной информацией о клиентах
     */
    private List<Map<String, Object>> getRecentOperationsWithClientInfo(
            List<? extends BaseOperation> operations,
            List<Client> allClients) {

        // Создаем Map для быстрого поиска клиентов по ID
        Map<Long, String> clientNames = allClients.stream()
                .collect(Collectors.toMap(Client::getId, Client::getName, (a, b) -> a));

        // Получаем 10 последних операций и обогащаем их информацией о клиентах
        return operations.stream()
                .map(op -> {
                    Map<String, Object> operationMap = new HashMap<>();
                    operationMap.put("id", op.getId());
                    operationMap.put("clientId", op.getClientId());
                    operationMap.put("clientName", clientNames.getOrDefault(op.getClientId(), "Неизвестный"));
                    operationMap.put("type", op.getType());
                    operationMap.put("status", op.getStatus());
                    operationMap.put("startTime", op.getStartTime());
                    operationMap.put("sourceIdentifier", op.getSourceIdentifier());
                    operationMap.put("processedRecords", op.getProcessedRecords() != null ? op.getProcessedRecords() : 0);
                    operationMap.put("totalRecords", op.getTotalRecords() != null ? op.getTotalRecords() : 0);

                    // Дополнительная информация для импорта
                    if (op instanceof ImportOperation) {
                        ImportOperation importOp = (ImportOperation) op;
                        operationMap.put("fileName", importOp.getFileName());
                        operationMap.put("fileSize", importOp.getFileSize());
                    }

                    return operationMap;
                })
                .collect(Collectors.toList());
    }

    /**
     * Подсчитывает количество операций определенного типа, начатых после указанной даты
     */
    private long countOperationsByTypeAndDate(List<? extends BaseOperation> operations,
                                              OperationType type,
                                              LocalDateTime after) {
        return operations.stream()
                .filter(op -> op.getType() == type &&
                        op.getStartTime() != null &&
                        op.getStartTime().isAfter(after))
                .count();
    }

    /**
     * Подсчитывает количество активных операций
     */
    private long countActiveOperations(List<? extends BaseOperation> operations) {
        return operations.stream()
                .filter(op -> op.getStatus() == OperationStatus.IN_PROGRESS ||
                        op.getStatus() == OperationStatus.PENDING)
                .count();
    }

    /**
     * Формирует статистику по типам операций для диаграммы
     */
    private Map<String, Object> getOperationTypeCounts(List<? extends BaseOperation> operations) {
        Map<String, Object> result = new HashMap<>();

        // Создаем статистику по типам
        Map<String, Long> typeCounts = operations.stream()
                .collect(Collectors.groupingBy(
                        op -> op.getType().name(),
                        Collectors.counting()
                ));

        // Преобразуем в формат для JavaScript
        result.put("labels", new ArrayList<>(typeCounts.keySet()));
        result.put("data", new ArrayList<>(typeCounts.values()));

        // Добавляем описания для более читаемых меток
        Map<String, String> typeDescriptions = new HashMap<>();
        for (OperationType type : OperationType.values()) {
            typeDescriptions.put(type.name(), type.getDescription());
        }
        result.put("descriptions", typeDescriptions);

        log.debug("Сформирована статистика по типам операций: {}", result);
        return result;
    }

    /**
     * Формирует временную статистику операций за последние 7 дней для диаграммы
     */
    private Map<String, Object> getOperationsTimeline(List<? extends BaseOperation> operations) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM");
        Map<String, Object> result = new HashMap<>();
        List<String> labels = new ArrayList<>();
        List<Long> data = new ArrayList<>();

        // Инициализируем данные за последние 7 дней
        Map<String, Long> dailyCounts = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            LocalDateTime date = LocalDateTime.now().minusDays(i).withHour(0).withMinute(0).withSecond(0);
            String formattedDate = date.format(formatter);
            dailyCounts.put(formattedDate, 0L);
        }

        // Заполняем статистику по дням
        operations.stream()
                .filter(op -> op.getStartTime() != null &&
                        op.getStartTime().isAfter(LocalDateTime.now().minusDays(7)))
                .forEach(op -> {
                    String day = op.getStartTime().format(formatter);
                    dailyCounts.computeIfPresent(day, (k, v) -> v + 1);
                });

        // Преобразуем в списки для JavaScript
        labels.addAll(dailyCounts.keySet());
        data.addAll(dailyCounts.values());

        result.put("labels", labels);
        result.put("data", data);

        log.debug("Сформирована временная статистика операций: {}", result);
        return result;
    }
}