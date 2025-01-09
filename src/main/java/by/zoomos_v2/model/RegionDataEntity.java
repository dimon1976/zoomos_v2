package by.zoomos_v2.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

@Data
@Entity
public class RegionDataEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String region;       // Регион (город, область)
    private String address;      // Адрес
    private String productId;    // Ссылка на ProductEntity (по id товара)

    // Другие поля, специфичные для региональных данных
}
