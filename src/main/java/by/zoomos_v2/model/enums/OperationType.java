package by.zoomos_v2.model.enums;

/**
 * Перечисление типов операций в системе
 */
public enum OperationType {
    IMPORT("Импорт данных"),
    EXPORT("Экспорт данных"),
    UTILITY("Утилита"),
    OTHER("Прочее");

    private final String description;

    OperationType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
