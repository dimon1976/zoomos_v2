package by.zoomos_v2.util;

import lombok.Data;

@Data
public class EntityField {
    private String fieldName;      // Исходное название поля
    private String description;      // Отображаемое название
    private String mappingKey;
    private int position;           // Позиция в экспорте
    private boolean enabled;
}
