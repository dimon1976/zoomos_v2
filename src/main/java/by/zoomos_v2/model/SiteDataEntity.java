package by.zoomos_v2.model;

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
    private Long id;

    private String siteName;      // Название сайта
    private String price;         // Цена товара
    private String competitorPrice; // Цена конкурента
    private String stockStatus;   // Статус наличия товара (в наличии, нет в наличии)
    private String promotionalPrice; // Акционная цена
    private String productId;     // Ссылка на ProductEntity (по id товара)

    // Другие поля, специфичные для данных с сайта
}
