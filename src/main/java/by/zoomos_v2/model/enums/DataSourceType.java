package by.zoomos_v2.model.enums;

public enum DataSourceType {
    TASK("Задание"),
    REPORT("Отчет"),
    COMMON("Общий");  // Для других стратегий

    private final String displayName;

    DataSourceType(String displayName) {
        this.displayName = displayName;
    }
}
