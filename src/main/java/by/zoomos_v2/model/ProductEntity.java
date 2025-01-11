package by.zoomos_v2.model;

import by.zoomos_v2.annotations.FieldDescription;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
public class ProductEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @FieldDescription("пропустить")
    private Long id;

    @FieldDescription("ID товара")
    private String productId;
    @FieldDescription("Наименование товара")
    private String name;
    @FieldDescription("Бренд товара")
    private String brand;
    @FieldDescription("Категория товара")
    private String category;
    @FieldDescription("Описание товара")
    private String description;
    // Другие поля, специфичные для товара
}

