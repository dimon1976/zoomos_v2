package by.zoomos_v2.service.file.input.callback;

/**
 * Интерфейс для обратного вызова при обновлении прогресса обработки
 */
@FunctionalInterface
public interface ProcessingProgressCallback {
    /**
     * Обновляет информацию о прогрессе обработки
     * @param progress процент выполнения (0-100)
     * @param message информационное сообщение
     */
    void updateProgress(int progress, String message);
}