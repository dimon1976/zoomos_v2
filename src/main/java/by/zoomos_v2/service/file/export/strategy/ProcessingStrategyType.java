package by.zoomos_v2.service.file.export.strategy;

public enum ProcessingStrategyType {
    DEFAULT("Стандартная обработка", "Выгрузка данных без дополнительной обработки"),
    CLEAN_URLS("Очистка URL", "Очистка URL-адресов конкурентов перед выгрузкой для Simple.ru"),
    FILTER_PRICES("Фильтрация цен", "Фильтрация и форматирование ценовых данных"),
    MERGE_PRODUCTS("Объединение продуктов", "Объединение данных по одинаковым продуктам"),
    TASK_BASED_FILTER("Фильтрация по заданию", "Фильтрация данных на основе задания и обогащение справочными данными для азбуки");

    private final String displayName;
    private final String description;

    ProcessingStrategyType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
