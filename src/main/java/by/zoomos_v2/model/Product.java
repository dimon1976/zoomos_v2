package by.zoomos_v2.model;

import by.zoomos_v2.annotations.FieldDescription;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @FieldDescription(value = "пропустить", skipMapping = true)
    private Long id;

    @FieldDescription(value = "пропустить", skipMapping = true)
    private Long clientId;

    @FieldDescription("ID товара")
    private String productId;

    @FieldDescription("Наименование товара")
    @Column(length = 400)
    private String productName;

    @FieldDescription("Бренд товара")
    private String productBrand;

    @FieldDescription("Штрихкод")
    private String productBar;

    @FieldDescription("Описание")
    private String productDescription;

    @FieldDescription("Ссылка")
    @Column(length = 1100)
    private String productUrl;

    @FieldDescription("Категория товара 1")
    private String productCategory1;
    @FieldDescription("Категория товара 2")
    private String productCategory2;
    @FieldDescription("Категория товара 3")
    private String productCategory3;

    @FieldDescription("Цена")
    private Double productPrice;


    @FieldDescription("Аналог")
    private String productAnalog;

    @FieldDescription("Дополнительное поле 1")
    private String competitorAdditional1;

    @FieldDescription("Дополнительное поле 2")
    private String competitorAdditional2;

    @FieldDescription("Дополнительное поле 3")
    private String competitorAdditional3;

    @FieldDescription("Дополнительное поле 4")
    private String competitorAdditional4;

    @FieldDescription("Дополнительное поле 5")
    private String competitorAdditional5;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @FieldDescription(value = "Региональные данные", skipMapping = true)
    private List<RegionData> regionDataList = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @FieldDescription(value = "Данные с сайтов", skipMapping = true)
    private List<SiteData> siteDataList = new ArrayList<>();

    // Методы для управления связями
    public void addRegionData(RegionData regionData) {
        regionDataList.add(regionData);
        regionData.setProduct(this);
    }

    public void removeRegionData(RegionData regionData) {
        regionDataList.remove(regionData);
        regionData.setProduct(null);
    }

    public void addSiteData(SiteData siteData) {
        siteDataList.add(siteData);
        siteData.setProduct(this);
    }

    public void removeSiteData(SiteData siteData) {
        siteDataList.remove(siteData);
        siteData.setProduct(null);
    }
}

