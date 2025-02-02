package by.zoomos_v2.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
@Table(name = "export_configs")
public class ExportConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(nullable = false)
    private String name;

    /**
     * Описание конфигурации
     */
    @Column(length = 500)
    private String description;

    @Column(name = "is_default")
    private boolean isDefault;

    @OneToMany(mappedBy = "exportConfig", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    @ToString.Exclude  // Добавляем эту аннотацию
    private List<ExportField> fields;

    /**
     * Дата создания конфигурации
     */
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * Дата последнего обновления конфигурации
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

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
