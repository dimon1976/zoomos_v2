package by.zoomos_v2.model.enums;

/**
 * Перечисление статусов операций
 */
public enum OperationStatus {
    PENDING("В ожидании"),
    IN_PROGRESS("В процессе"),
    COMPLETED("Завершено"),
    FAILED("Ошибка"),
    CANCELLED("Отменено"),
    PARTIAL_SUCCESS("Частично успешно");

    private final String description;

    OperationStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}