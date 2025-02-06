package by.zoomos_v2.service.statistics;

import by.zoomos_v2.model.operation.BaseOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Обработчик прогресса для всех операций
 */
@Service
@RequiredArgsConstructor
public class OperationProgressTracker {
    private static final long UPDATE_INTERVAL_MS = 1000;
    private final StatisticsProcessor statisticsProcessor;
    private final Map<Long, LocalDateTime> lastUpdates = new ConcurrentHashMap<>();
    private final Map<Long, Integer> lastProgress = new ConcurrentHashMap<>();


    public void trackProgress(BaseOperation operation, int progress, String message) {
        if (shouldUpdate(operation.getId()) || isSignificantChange(operation.getId(), progress)) {
            statisticsProcessor.handleProgress(operation, progress, message);
            lastUpdates.put(operation.getId(), LocalDateTime.now());
            lastProgress.put(operation.getId(), progress);
        }
    }

    private boolean isSignificantChange(Long operationId, int newProgress) {
        return Math.abs(lastProgress.getOrDefault(operationId, 0) - newProgress) >= 5;
    }

    private boolean shouldUpdate(Long operationId) {
        LocalDateTime lastUpdate = lastUpdates.get(operationId);
        return lastUpdate == null ||
                ChronoUnit.MILLIS.between(lastUpdate, LocalDateTime.now()) >= UPDATE_INTERVAL_MS;
    }
}

