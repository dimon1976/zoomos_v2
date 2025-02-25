package by.zoomos_v2.model;

import by.zoomos_v2.service.file.export.strategy.ProcessingStrategyType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "strategy_type")
    private ProcessingStrategyType strategyType = ProcessingStrategyType.DEFAULT;

    @OneToMany(mappedBy = "exportConfig", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    @ToString.Exclude
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

    /**
     * Устанавливает значение параметра
     *
     * @param key ключ параметра
     * @param value значение параметра
     */
    public void setParam(String key, String value) {
        if (params == null) {
            params = new ArrayList<>();
        }

        // Ищем существующий параметр
        ExportConfigParam param = params.stream()
                .filter(p -> p.getKey().equals(key))
                .findFirst()
                .orElse(null);

        if (param == null) {
            // Создаем новый параметр
            param = new ExportConfigParam();
            param.setKey(key);
            param.setExportConfig(this);
            params.add(param);
        }

        param.setValue(value);
    }

    /**
     * Получает все параметры в виде Map
     */
    public Map<String, String> getParams() {
        if (params == null) {
            return new HashMap<>();
        }
        return params.stream()
                .collect(Collectors.toMap(
                        ExportConfigParam::getKey,
                        ExportConfigParam::getValue,
                        (existing, replacement) -> existing // в случае дубликатов оставляем первое значение
                ));
    }

    /**
     * Очищает все параметры
     */
    public void clearParams() {
        if (params != null) {
            params.clear();
        }
    }

    /**
     * Устанавливает параметры из Map
     */
    public void setParams(Map<String, String> parameters) {
        clearParams();
        if (parameters != null) {
            parameters.forEach(this::setParam);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
