package by.zoomos_v2.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
public class ProductEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;
    private String productId;  // ID товара
    private String name;       // Наименование товара
    private String brand;      // Бренд товара
    private String category;   // Категория товара
    private String description; // Описание товара
    // Другие поля, специфичные для товара
}

