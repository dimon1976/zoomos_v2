package by.zoomos_v2.model;

import by.zoomos_v2.annotations.FieldDescription;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "region_data")
public class RegionData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @FieldDescription(value = "пропустить", skipMapping = true)
    private Long id;

    @FieldDescription("Регион (город, область)")
    @Column(nullable = false)
    private String region;

    @FieldDescription("Адрес")
    private String regionAddress;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @FieldDescription(value = "пропустить", skipMapping = true)
    private Product product;

    @Version
    @FieldDescription(value = "пропустить", skipMapping = true)
    private Long version;

    // Другие поля, специфичные для региональных данных
}