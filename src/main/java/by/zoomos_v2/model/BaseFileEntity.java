package by.zoomos_v2.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE) // Используем одну таблицу для всех сущностей
@DiscriminatorColumn(name = "entity_type", discriminatorType = DiscriminatorType.STRING)
// Поле для различия типов сущностей
public class BaseFileEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    private String productId;
    private String model;
    private String vendor;
    private String category;

    @Column(name = "client_name")  // Добавляем поле для имени клиента
    private String clientName;     // Имя клиента, которое будет использоваться для фильтрации в репозитории
}
