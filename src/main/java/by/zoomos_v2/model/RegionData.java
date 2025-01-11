package by.zoomos_v2.model;

import by.zoomos_v2.annotations.FieldDescription;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
public class RegionData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @FieldDescription("пропустить")
    private Long id;

    @FieldDescription("Регион (город, область)")
    private String region;
    @FieldDescription("Адрес")
    private String regionAddress;

    // Ссылка на продукт
    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    @FieldDescription("пропустить")
    private Product product;

    // Другие поля, специфичные для региональных данных
}
