package by.zoomos_v2.mapping;

import by.zoomos_v2.model.Client;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
public class ClientMappingConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "client_id")
    private Client client;

    private String name;

    @Column(name = "mapping_data", columnDefinition = "TEXT")
    private String mappingData;  // Храним конфигурацию в виде JSON-строки

    @Column(name = "type")
    private String type;  // Тип маппинга (например, "Product", "SiteData", "Region")

}
