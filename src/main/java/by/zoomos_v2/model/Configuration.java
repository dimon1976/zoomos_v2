package by.zoomos_v2.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
public class Configuration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    private String name;
    private String clientName; // Имя клиента
}
