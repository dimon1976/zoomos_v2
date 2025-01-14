package by.zoomos_v2.constant;

/**
 * Константы для статусов обработки файлов.
 */
public final class FileStatus {

    private FileStatus() {
        // Приватный конструктор для утилитарного класса
    }

    /**
     * Файл загружен, ожидает обработки
     */
    public static final String PENDING = "PENDING";

    /**
     * Файл в процессе обработки
     */
    public static final String PROCESSING = "PROCESSING";

    /**
     * Обработка файла успешно завершена
     */
    public static final String COMPLETED = "COMPLETED";

    /**
     * Произошла ошибка при обработке файла
     */
    public static final String ERROR = "ERROR";

    /**
     * Обработка файла была отменена
     */
    public static final String CANCELLED = "CANCELLED";

    /**
     * Проверяет, находится ли файл в конечном состоянии
     */
    public static boolean isFinalStatus(String status) {
        return COMPLETED.equals(status) ||
                ERROR.equals(status) ||
                CANCELLED.equals(status);
    }

    /**
     * Проверяет, является ли статус валидным
     */
    public static boolean isValidStatus(String status) {
        return PENDING.equals(status) ||
                PROCESSING.equals(status) ||
                COMPLETED.equals(status) ||
                ERROR.equals(status) ||
                CANCELLED.equals(status);
    }
}