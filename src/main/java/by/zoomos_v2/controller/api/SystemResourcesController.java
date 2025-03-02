package by.zoomos_v2.controller.api;

import by.zoomos_v2.service.DiskSpaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST контроллер для получения информации о системных ресурсах
 */
@RestController
@RequestMapping("/api/system")
public class SystemResourcesController {

    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    @Autowired
    private DiskSpaceService diskSpaceService;

    @GetMapping("/resources")
    public ResponseEntity<Map<String, Object>> getSystemResources(
            @RequestParam(value = "forceUpdate", defaultValue = "false") boolean forceUpdate) {

        Map<String, Object> resources = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();

        resources.put("lastUpdated", now);
        resources.put("lastUpdatedFormatted", dateFormatter.format(now));

        // Получаем информацию о CPU - для Windows используем альтернативный метод
        try {
            com.sun.management.OperatingSystemMXBean sunOsBean =
                    (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

            // Получаем количество доступных процессоров/ядер
            int processorCount = Runtime.getRuntime().availableProcessors();
            resources.put("availableProcessors", processorCount);

            // Используем getCpuLoad() для общей загрузки системы
            double cpuLoad = sunOsBean.getCpuLoad();
            if (cpuLoad >= 0) {
                // Правильный перевод в проценты (0.0 - 1.0 -> 0% - 100%)
                double systemLoadValue = cpuLoad * 100.0;

                // Округляем до 2 знаков после запятой
                double cpuPercentage = Math.round(systemLoadValue * 100) / 100.0;
                resources.put("cpuUsagePercentage", cpuPercentage);

                // Добавляем системную загрузку (как в диспетчере задач)
                resources.put("systemCpuLoad", cpuPercentage);
            } else {
                // Пробуем другой метод для получения загрузки CPU
                try {
                    double processCpuLoad = sunOsBean.getProcessCpuLoad();
                    if (processCpuLoad >= 0) {
                        double processPercentage = Math.round(processCpuLoad * 10000) / 100.0;
                        resources.put("cpuUsagePercentage", processPercentage);
                        resources.put("processCpuLoad", processPercentage);
                    } else {
                        resources.put("cpuUsagePercentage", 0.0);
                        resources.put("cpuInfo", "Недоступно на этой платформе");
                    }
                } catch (Exception e) {
                    resources.put("cpuUsagePercentage", 0.0);
                    resources.put("cpuInfo", "Ошибка при получении загрузки процесса: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            resources.put("cpuUsagePercentage", 0.0);
            resources.put("cpuError", "Ошибка при получении информации о CPU: " + e.getMessage());
        }

        // Получаем информацию о памяти
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long usedMemory = memoryBean.getHeapMemoryUsage().getUsed() +
                memoryBean.getNonHeapMemoryUsage().getUsed();
        long maxMemory = Runtime.getRuntime().maxMemory();
        long totalMemory = Runtime.getRuntime().totalMemory();

        double memoryPercentage = Math.round((double) usedMemory / maxMemory * 10000) / 100.0;
        resources.put("memoryUsagePercentage", memoryPercentage);

        // Добавляем подробную информацию о памяти
        resources.put("currentMemoryUsage", formatSize(usedMemory));
        resources.put("totalMemory", formatSize(maxMemory));
        resources.put("allocatedMemory", formatSize(totalMemory));
        resources.put("freeMemory", formatSize(totalMemory - usedMemory));
        resources.put("maxHeapMemory", formatSize(memoryBean.getHeapMemoryUsage().getMax()));
        resources.put("usedHeapMemory", formatSize(memoryBean.getHeapMemoryUsage().getUsed()));

        // Получаем информацию о диске из сервиса DiskSpaceService
        ConcurrentHashMap<String, Object> diskInfo;
        if (forceUpdate) {
            diskInfo = diskSpaceService.forceUpdateDiskInfo();
        } else {
            diskInfo = diskSpaceService.getDiskInfo();
        }

        // Добавляем информацию о диске в ответ
        resources.putAll(diskInfo);

        return ResponseEntity.ok(resources);
    }

    /**
     * Форматирует размер в байтах в читаемый формат (KB, MB, GB)
     */
    private String formatSize(long size) {
        if (size <= 0) return "0 B";

        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));

        // Округляем до 2 знаков после запятой
        double formattedSize = Math.round((size / Math.pow(1024, digitGroups)) * 100) / 100.0;

        return formattedSize + " " + units[digitGroups];
    }
}