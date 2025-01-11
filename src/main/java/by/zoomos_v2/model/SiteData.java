package by.zoomos_v2.model;

import by.zoomos_v2.annotations.FieldDescription;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
public class SiteData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @FieldDescription("пропустить")
    private Long id;

    @FieldDescription("Название сайта")
    private String competitorName;
    @FieldDescription("Цена конкурента")
    private String competitorPrice;
    @FieldDescription("Статус наличия товара")
    private String competitorStockStatus;
    @FieldDescription("Акционная цена")
    private String competitorPromotionalPrice;

    // Ссылка на продукт
    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    @FieldDescription("пропустить")
    private Product product;

    // Другие поля, специфичные для данных с сайта
}
