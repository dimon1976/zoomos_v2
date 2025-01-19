package by.zoomos_v2.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
     * Дополнительные параметры конфигурации экспорта
     */
    @OneToMany(mappedBy = "exportConfig", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private List<ExportConfigParam> params;

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

    /**
     * Получает значение параметра по ключу
     */
    public String getParam(String key) {
        if (params == null) {
            return null;
        }
        return params.stream()
                .filter(p -> p.getKey().equals(key))
                .map(ExportConfigParam::getValue)
                .findFirst()
                .orElse(null);
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
