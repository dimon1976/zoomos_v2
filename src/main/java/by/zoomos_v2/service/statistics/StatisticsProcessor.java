package by.zoomos_v2.service.statistics;

import by.zoomos_v2.model.enums.OperationStatus;
import by.zoomos_v2.model.operation.BaseOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * Сервис для обработки и агрегации статистики
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsProcessor {
    private final OperationStatsService operationStatsService;


    /**
     * Метод расчета всех метрик операции
     */
    private void calculateOperationMetrics(BaseOperation operation) {
        if (operation.getStartTime() != null && operation.getEndTime() != null) {
            long processingTimeSeconds = ChronoUnit.SECONDS.between(
                    operation.getStartTime(),
                    operation.getEndTime()
            );
            operation.setProcessingTimeSeconds(processingTimeSeconds);

            // Расчет скорости обработки
            if (operation.getProcessedRecords() != null && processingTimeSeconds > 0) {
                double recordsPerSecond = operation.getProcessedRecords().doubleValue() / processingTimeSeconds;
                operation.setProcessingSpeed(recordsPerSecond);
            }
        }
    }

    /**
     * Централизованный сбор и обновление метрик операции
     */
    public void updateMetrics(BaseOperation operation) {
        // Базовые метрики
        calculateOperationMetrics(operation);

        // Метрики производительности
        Map<String, Object> performanceMetrics = new HashMap<>();
        performanceMetrics.put("processingTimeSeconds", operation.getProcessingTimeSeconds());
        performanceMetrics.put("recordsPerSecond", operation.getProcessingSpeed());
        performanceMetrics.put("peakMemoryUsage", getPeakMemoryUsage());
        performanceMetrics.put("timestamp", LocalDateTime.now());

        // Метрики прогресса
        Map<String, Object> progressMetrics = new HashMap<>();
        progressMetrics.put("totalRecords", operation.getTotalRecords());
        progressMetrics.put("processedRecords", operation.getProcessedRecords());
        progressMetrics.put("failedRecords", operation.getFailedRecords());
        progressMetrics.put("completionRate", calculateCompletionRate(operation));

        operation.getMetadata().put("performanceMetrics", performanceMetrics);
        operation.getMetadata().put("progressMetrics", progressMetrics);
    }

    private double calculateCompletionRate(BaseOperation operation) {
        if (operation.getTotalRecords() == null || operation.getTotalRecords() == 0) {
            return 0.0;
        }
        return (operation.getProcessedRecords() * 100.0) / operation.getTotalRecords();
    }

    /**
     * Централизованный обработчик статуса операции
     */
    public void handleOperationStatus(BaseOperation operation) {
        updateMetrics(operation);

        OperationStatus status = determineOperationStatus(operation);
        String errorSummary = operation.getErrors().isEmpty() ? null
                : String.join("; ", operation.getErrors());

        operationStatsService.updateOperationStatus(
                operation,
                status,
                errorSummary,
                "PROCESSING_SUMMARY"
        );
    }


    /**
     * Обрабатывает ошибку операции
     *
     * @param operation операция
     * @param error     сообщение об ошибке
     * @param errorType тип ошибки
     */
    public void handleOperationError(BaseOperation operation, String error, String errorType) {
        log.debug("Обработка ошибки операции {}: {} ({})", operation.getId(), error, errorType);
        operation.addError(error, errorType);
        operation.setStatus(OperationStatus.FAILED);
        operation.setEndTime(LocalDateTime.now());

        updateMetrics(operation);
        operationStatsService.updateOperationStatus(operation, operation.getStatus(), error, errorType);
    }

    /**
     * Обработчик прогресса операции
     */
    public void handleProgress(BaseOperation operation, int progress, String message) {
        updateMetrics(operation);

        Map<String, Object> progressData = new HashMap<>();
        progressData.put("currentProgress", progress);
        progressData.put("message", message);
        progressData.put("timestamp", LocalDateTime.now());

        operation.getMetadata().put("currentProgress", progressData);
        operationStatsService.updateOperation(operation);
    }


    /**
     * Обновляет статистику операции
     *
     * @param operation операция
     */
    public void updateOperationStats(BaseOperation operation) {
        log.debug("Обновление статистики операции {}: статус={}, обработано={}, всего={}",
                operation.getId(), operation.getStatus(),
                operation.getProcessedRecords(), operation.getTotalRecords());

        handleOperationStatus(operation);
    }

    /**
     * Определяет финальный статус операции
     *
     * @param operation операция
     * @return финальный статус операции
     */
    private OperationStatus determineOperationStatus(BaseOperation operation) {
        if (operation.getStatus() != OperationStatus.CANCELLED) {
            if (operation.getErrors().isEmpty()) {
                return OperationStatus.COMPLETED;
            } else if (operation.getProcessedRecords() > 0) {
                return OperationStatus.PARTIAL_SUCCESS;
            }
            return OperationStatus.FAILED;
        }
        return OperationStatus.CANCELLED;
    }


    /**
     * Получает пиковое использование памяти
     */
    public String getPeakMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long allocatedMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = allocatedMemory - freeMemory;
        return formatBytes(usedMemory);
    }

    public String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
