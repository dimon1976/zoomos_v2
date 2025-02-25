package by.zoomos_v2.service.statistics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
@Service
@Slf4j
public class OperationStateManager {
    private final ConcurrentHashMap<Long, AtomicBoolean> cancelledOperations = new ConcurrentHashMap<>();

    public void markAsCancelled(Long operationId) {
        log.debug("Устанавливаем флаг отмены для операции {}", operationId);
        cancelledOperations.computeIfAbsent(operationId, k -> new AtomicBoolean())
                .set(true);
    }

    public boolean isCancelled(Long operationId) {
        AtomicBoolean flag = cancelledOperations.get(operationId);
        boolean cancelled = flag != null && flag.get();
        if (cancelled) {
            log.debug("Обнаружен флаг отмены для операции {}", operationId);
        }
        return cancelled;
    }

    public void cleanup(Long operationId) {
        log.debug("Очистка состояния для операции {}", operationId);
        cancelledOperations.remove(operationId);
    }
}
