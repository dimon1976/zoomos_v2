package by.zoomos_v2.service.file.export.strategy;

/**
 * Типы параметров для стратегий обработки данных
 */
public enum ParameterType {
    STRING("Строка"),
    NUMBER("Число"),
    SELECT("Выбор из списка"),
    DATE("Дата"),
    BOOLEAN("Логическое значение");

    private final String displayName;

    ParameterType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}