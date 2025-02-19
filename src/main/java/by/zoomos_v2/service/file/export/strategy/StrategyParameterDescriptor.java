package by.zoomos_v2.service.file.export.strategy;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * Описание параметра стратегии обработки данных.
 * Используется для автоматической генерации UI и валидации.
 */
@Data
@Builder
public class StrategyParameterDescriptor {
    /**
     * Уникальный ключ параметра
     */
    private String key;

    /**
     * Название для отображения в интерфейсе
     */
    private String displayName;

    /**
     * Описание параметра
     */
    private String description;

    /**
     * Тип параметра
     */
    private ParameterType type;

    /**
     * Признак обязательности параметра
     */
    private boolean required;

    /**
     * Значение по умолчанию
     */
    private String defaultValue;

    /**
     * Список допустимых значений (для типа SELECT)
     */
    private List<String> allowedValues;

    /**
     * Валидирует значение параметра
     *
     * @param value значение для проверки
     * @throws IllegalArgumentException если значение невалидно
     */
    public void validate(String value) {
        if (required && (value == null || value.trim().isEmpty())) {
            throw new IllegalArgumentException(
                    String.format("Параметр %s обязателен для заполнения", displayName)
            );
        }

        if (value != null && type == ParameterType.SELECT &&
                !allowedValues.contains(value)) {
            throw new IllegalArgumentException(
                    String.format("Недопустимое значение для параметра %s", displayName)
            );
        }

        // Дополнительные проверки для других типов можно добавить здесь
    }
}
