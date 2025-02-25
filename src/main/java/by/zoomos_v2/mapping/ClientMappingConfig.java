package by.zoomos_v2.mapping;

import by.zoomos_v2.model.enums.DataSourceType;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;

/**
 * Сущность для хранения настроек маппинга данных магазина.
 * Определяет как данные из загруженных файлов будут обрабатываться.
 */
@Entity
@Data
@NoArgsConstructor
@Table(name = "client_mapping_config")
public class ClientMappingConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_source")
    @Enumerated(EnumType.STRING)
    private DataSourceType dataSource;

    /**
     * Идентификатор магазина (клиента), к которому относится маппинг
     */
    @Column(name = "client_id", nullable = false)
    private Long clientId;

    /**
     * Имя конфигурации маппинга
     */
    @Column(nullable = false)
    private String name;

    /**
     * Описание конфигурации
     */
    @Column(length = 500)
    private String description;

    /**
     * Статус активности конфигурации
     */
    @Column
    private boolean active = true;

    /**
     * Тип файла, для которого применяется маппинг (например, CSV, Excel и т.д.)
     */
    @Column(name = "file_type")
    private String fileType;

    /**
     * Конфигурация колонок в формате JSON
     * Хранит соответствие между колонками файла и полями в системе
     */
    @Type(JsonBinaryType.class)
    @Column(name = "columns_config", columnDefinition = "jsonb")
    private String columnsConfig;

    /**
     * Настройки разделителей и других параметров для парсинга файлов
     */
    @Type(JsonBinaryType.class)
    @Column(name = "parser_config", columnDefinition = "jsonb")
    private String parserConfig;

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

    /**
     * Создает новую конфигурацию маппинга для указанного клиента
     *
     * @param clientId идентификатор клиента
     * @param name     название конфигурации
     */
    public ClientMappingConfig(Long clientId, String name) {
        this.clientId = clientId;
        this.name = name;
        this.active = true;
    }
}
