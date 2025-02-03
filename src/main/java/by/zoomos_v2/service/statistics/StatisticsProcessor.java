package by.zoomos_v2.service.statistics;

import by.zoomos_v2.model.enums.OperationStatus;
import by.zoomos_v2.service.file.ProcessingStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
     * Обновляет статистику операции на основе ProcessingStats
     */
    public void updateOperationStats(Long operationId, ProcessingStats stats) {
        log.debug("Обновление статистики операции {}", operationId);

        try {
            operationStatsService.updateProcessingStats(
                    operationId,
                    stats.getTotalCount(),
                    stats.getSuccessCount(),
                    stats.getErrorCount()
            );

            // Обновляем статус операции
            OperationStatus status = determineStatus(stats);
            operationStatsService.updateOperationStatus(
                    operationId,
                    status,
                    stats.getErrors() != null && !stats.getErrors().isEmpty() ?
                            String.join("; ", stats.getErrors()) : null
            );

            // Добавляем дополнительную статистику
            if (stats.getAdditionalStats() != null) {
                addAdditionalStats(operationId, stats.getAdditionalStats());
            }

        } catch (Exception e) {
            log.error("Ошибка при обновлении статистики операции {}: {}",
                    operationId, e.getMessage(), e);
            operationStatsService.updateOperationStatus(
                    operationId,
                    OperationStatus.FAILED,
                    "Ошибка обновления статистики: " + e.getMessage()
            );
        }
    }

    /**
     * Определяет статус операции на основе статистики
     */
    private OperationStatus determineStatus(ProcessingStats stats) {
        if (stats.getErrorCount() == 0) {
            return OperationStatus.COMPLETED;
        } else if (stats.getSuccessCount() > 0) {
            return OperationStatus.PARTIAL_SUCCESS;
        } else {
            return OperationStatus.FAILED;
        }
    }

    /**
     * Добавляет дополнительную статистику к операции
     */
    private void addAdditionalStats(Long operationId, Map<String, Object> additionalStats) {
        // Здесь можно добавить логику сохранения дополнительной статистики
        // Например, через отдельную таблицу или JSON-поле
    }
}
