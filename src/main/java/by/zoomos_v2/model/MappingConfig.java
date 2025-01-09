package by.zoomos_v2.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;


@Data
@Entity
public class MappingConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "entity_type", nullable = false)
    private String entityType; // Тип сущности, например, "BaseFileEntity" или "AvFileEntity"

    @NotBlank(message = "Имя столбца в файле обязательно для заполнения")
    @Column(name = "column_name", nullable = false)
    private String columnName; // Имя столбца в файле

    @Column(name = "field_name", nullable = false)
    private String fieldName; // Имя поля в сущности

    @ManyToOne
    @JoinColumn(name = "client_id", nullable = false)
    private Client client; // Добавляем ссылку на клиента
}
