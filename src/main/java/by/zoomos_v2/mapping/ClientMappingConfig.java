package by.zoomos_v2.mapping;

import by.zoomos_v2.model.Client;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
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

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Column(name = "mapping_data", columnDefinition = "TEXT")
    private String mappingData;  // Храним конфигурацию в виде JSON-строки

    public void setId(Long id) {
        this.id = id;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setMappingData(String mappingData) {
        this.mappingData = mappingData;
    }
}
