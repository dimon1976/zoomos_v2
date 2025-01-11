package by.zoomos_v2.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class AvFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    private String competitorStatus;
    private String parseDate;
    private String webcacheUrl;
    private String competitorUrl;
    private String taskNo;
    private String startTask;
    private String endTask;
    private String productCategoryCode;// Код товарной категории
    private String productComment;// Комментарий по товару
    private String priceZoneCode;// Код ценовой зоны
    private String retailerCode;// Код розничной сети
    private String retailChain;// Розничная сеть
    private String region;// Регион
    private String physicalAddress;// Физический адрес
    private String barcode;// Штрихкод
    private String quantityOfPieces;// Количество штук
    private String competitorsPrice;// Цена конкурента
    private String promotionalPrice;// Цена акционная\по карте
    private String analog;// Аналог
    private String note;// Примечание
}
