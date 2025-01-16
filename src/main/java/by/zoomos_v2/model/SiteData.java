package by.zoomos_v2.model;

import by.zoomos_v2.annotations.FieldDescription;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "site_data")
public class SiteData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @FieldDescription(value = "пропустить", skipMapping = true)
    private Long id;

    @FieldDescription(value = "пропустить", skipMapping = true)
    private Long clientId;

    @FieldDescription("Название сайта")
    private String competitorName;

    @FieldDescription("Цена конкурента")
    private String competitorPrice;

    @FieldDescription("Статус наличия товара")
    private String competitorStockStatus;

    @FieldDescription("Акционная цена")
    private String competitorPromotionalPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    @FieldDescription(value = "пропустить", skipMapping = true)
    private Product product;

    @Version
    @FieldDescription(value = "пропустить", skipMapping = true)
    private Long version;

}
