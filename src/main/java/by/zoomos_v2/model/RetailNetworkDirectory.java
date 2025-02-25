package by.zoomos_v2.model;

import by.zoomos_v2.annotations.FieldDescription;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * Сущность справочника розничных сетей.
 * Используется для хранения справочной информации о розничных сетях и их адресах.
 */
@Setter
@Getter
@Entity
@Table(name = "retail_network_directory",
        indexes = {
                @Index(name = "idx_retail_code", columnList = "retail_code", unique = true),
                @Index(name = "idx_region_code", columnList = "region_code")
        })
public class RetailNetworkDirectory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @FieldDescription(value = "пропустить", skipMapping = true)
    private Long id;

    @Column(name = "retail_code", nullable = false)
    @FieldDescription("Код Розничной Сети")
    private String retailCode;

    @Column(name = "retail_name")
    @FieldDescription("Розничная Сеть")
    private String retailName;

    @Column(name = "physical_address", length = 500)
    @FieldDescription("Физический Адрес")
    private String physicalAddress;

    @Column(name = "region_code")
    @FieldDescription("Код Региона")
    private String regionCode;

    @Column(name = "region_name")
    @FieldDescription("Название Региона")
    private String regionName;

    @Column(name = "created_at", updatable = false)
    @FieldDescription(value = "пропустить", skipMapping = true)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @FieldDescription(value = "пропустить", skipMapping = true)
    private LocalDateTime updatedAt;

    @Version
    @FieldDescription(value = "пропустить", skipMapping = true)
    private Long version;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}