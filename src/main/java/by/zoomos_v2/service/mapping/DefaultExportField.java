package by.zoomos_v2.service.mapping;

import by.zoomos_v2.util.EntityField;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Перечисление дефолтных полей для экспорта
 */
public enum DefaultExportField {
    ID("product.productId", "ID", 0),
    NAME("product.productName", "Наименование", 1),
    PRICE("product.productPrice", "Цена", 2),
    OLD_PRICE("product.productOldPrice", "Старая цена", 3),
    DISCOUNT("product.productDiscount", "Скидка", 4),
    BRAND("product.productBrand", "Бренд", 5),
    CATEGORY("product.productCategory", "Категория", 6),
    URL("product.productUrl", "Ссылка", 7),
    COMPETITOR("competitordata.competitorName", "Конкурент", 8),
    REGION("regiondata.region", "Регион", 9);

    private final String mappingKey;
    private final String displayName;
    private final int defaultPosition;

    DefaultExportField(String mappingKey, String displayName, int defaultPosition) {
        this.mappingKey = mappingKey;
        this.displayName = displayName;
        this.defaultPosition = defaultPosition;
    }

    public String getMappingKey() {
        return mappingKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getDefaultPosition() {
        return defaultPosition;
    }

    public static List<EntityField> getDefaultFields() {
        return Arrays.stream(values())
                .map(def -> {
                    EntityField field = new EntityField();
                    field.setMappingKey(def.getMappingKey());
                    field.setDescription(def.getDisplayName());
                    field.setPosition(def.getDefaultPosition());
                    return field;
                })
                .collect(Collectors.toList());
    }

}
