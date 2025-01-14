package by.zoomos_v2.service.processor;

/**
 * Интерфейс для обратного вызова при обновлении прогресса обработки
 */
@FunctionalInterface
public interface ProcessingProgressCallback {
    void updateProgress(int progress, String message);
}