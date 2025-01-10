package by.zoomos_v2.model;

import by.zoomos_v2.annotations.FieldDescription;
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

    @FieldDescription("Регион (город, область)")
    private String region;
    @FieldDescription("Адрес")
    private String address;
    @FieldDescription("не заполнять")
    private String productId;    // Ссылка на ProductEntity (по id товара)

    // Другие поля, специфичные для региональных данных
}
