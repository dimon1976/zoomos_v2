package by.zoomos_v2.service.statistics;

import by.zoomos_v2.model.enums.OperationStatus;
import by.zoomos_v2.model.operation.BaseOperation;
import by.zoomos_v2.model.operation.ImportOperation;
import by.zoomos_v2.service.file.BatchProcessingData;
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
     * Обновляет статус операции
     * @param operationId ID операции
     * @param status новый статус
     * @param message сообщение о статусе
     * @param errorType тип ошибки (если есть)
     */
    public void updateOperationStatus(Long operationId, OperationStatus status, String message, String errorType) {
        log.debug("Обновление статуса операции {}: {} - {}", operationId, status, message);
        operationStatsService.updateOperationStatus(operationId, status, message, errorType);
    }

    /**
     * Обрабатывает ошибку операции
     * @param operation операция
     * @param error сообщение об ошибке
     * @param errorType тип ошибки
     */
    public void handleOperationError(BaseOperation operation, String error, String errorType) {
        log.debug("Обработка ошибки операции {}: {} ({})", operation.getId(), error, errorType);
        operation.addError(error, errorType);
        updateOperationStatus(operation.getId(), OperationStatus.FAILED, error, errorType);
    }

    /**
     * Обновляет статистику операции
     * @param operation операция
     */
    public void updateOperationStats(BaseOperation operation) {
        log.debug("Обновление статистики операции {}", operation.getId());

        try {
            // Обновляем основную статистику
            operationStatsService.updateProcessingStats(
                    operation.getId(),
                    operation.getTotalRecords(),
                    operation.getProcessedRecords(),
                    operation.getFailedRecords()
            );

            // Определяем финальный статус
            OperationStatus finalStatus = determineOperationStatus(operation);

            // Обновляем статус с учетом ошибок
            String errorSummary = operation.getErrors().isEmpty() ? null
                    : String.join("; ", operation.getErrors());

            updateOperationStatus(
                    operation.getId(),
                    finalStatus,
                    errorSummary,
                    "PROCESSING_SUMMARY"
            );

        } catch (Exception e) {
            log.error("Ошибка при обновлении статистики операции {}: {}",
                    operation.getId(), e.getMessage(), e);
            handleOperationError(operation,
                    "Ошибка обновления статистики: " + e.getMessage(),
                    "STATISTICS_ERROR");
        }
    }

    /**
     * Определяет финальный статус операции
     * @param operation операция
     * @return финальный статус операции
     */
    private OperationStatus determineOperationStatus(BaseOperation operation) {
        if (operation.getErrors().isEmpty()) {
            return OperationStatus.COMPLETED;
        } else if (operation.getProcessedRecords() > 0) {
            return OperationStatus.PARTIAL_SUCCESS;
        }
        return OperationStatus.FAILED;
    }

    /**
     * Добавляет метрики производительности в операцию
     * @param operation операция
     * @param startTime время начала
     */
    public void addPerformanceMetrics(BaseOperation operation, LocalDateTime startTime) {
        if (startTime != null && operation.getEndTime() != null) {
            long processingTimeSeconds = ChronoUnit.SECONDS.between(startTime, operation.getEndTime());

            Map<String, Object> performanceMetrics = new HashMap<>();
            performanceMetrics.put("processingTimeSeconds", processingTimeSeconds);

            if (operation.getProcessedRecords() != null && processingTimeSeconds > 0) {
                double recordsPerSecond = (double) operation.getProcessedRecords() / processingTimeSeconds;
                performanceMetrics.put("recordsPerSecond", recordsPerSecond);
            }

            performanceMetrics.put("peakMemoryUsage", getPeakMemoryUsage());
            performanceMetrics.put("timestamp", LocalDateTime.now());

            operation.getMetadata().put("performanceMetrics", performanceMetrics);
        }
    }

    /**
     * Получает пиковое использование памяти
     */
    private String getPeakMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long allocatedMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = allocatedMemory - freeMemory;
        return formatBytes(usedMemory);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp-1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
