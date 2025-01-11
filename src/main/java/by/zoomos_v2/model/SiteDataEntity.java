package by.zoomos_v2.model;

import by.zoomos_v2.annotations.FieldDescription;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

@Data
@Entity
public class SiteDataEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @FieldDescription("пропустить")
    private Long id;

    @FieldDescription("Название сайта")
    private String siteName;
    @FieldDescription("Цена товара")
    private String price;
    @FieldDescription("Цена конкурента")
    private String competitorPrice;
    @FieldDescription("Статус наличия товара")
    private String stockStatus;
    @FieldDescription("Акционная цена")
    private String promotionalPrice;
    @FieldDescription("пропустить")
    private String productId;     // Ссылка на ProductEntity (по id товара)

    // Другие поля, специфичные для данных с сайта
}
