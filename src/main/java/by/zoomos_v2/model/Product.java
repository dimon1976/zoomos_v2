package by.zoomos_v2.model;

import by.zoomos_v2.annotations.FieldDescription;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
@Entity
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @FieldDescription("пропустить")
    private Long id;

    @FieldDescription("ID товара")
    private String productId;
    @FieldDescription("Наименование товара")
    private String productName;
    @FieldDescription("Бренд товара")
    private String productBrand;
    @FieldDescription("Категория товара")
    private String productCategory;
    @FieldDescription("Описание товара")
    private String productDescription;

    // Связь с RegionData
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RegionData> regionDataList;

    // Связь с SiteData
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SiteData> siteDataList;
    // Другие поля, специфичные для товара
}

