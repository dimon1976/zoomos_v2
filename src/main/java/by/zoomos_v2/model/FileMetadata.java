package by.zoomos_v2.model;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Сущность для хранения метаданных загруженных файлов.
 * Содержит информацию о загруженных файлах и их состоянии обработки.
 */
@Entity
@Data
@NoArgsConstructor
@Table(name = "file_metadata")
public class FileMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ID магазина, которому принадлежит файл
     */
    @Column(name = "shop_id", nullable = false)
    private Long clientId;

    /**
     * Оригинальное имя файла
     */
    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    /**
     * Имя файла в системе хранения
     */
    @Column(name = "stored_filename", nullable = false)
    private String storedFilename;

    /**
     * Тип файла (CSV, EXCEL и т.д.)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false)
    private FileType fileType;

    /**
     * MIME-тип файла
     */
    @Column(name = "content_type")
    private String contentType;

    /**
     * Размер файла в байтах
     */
    @Column(nullable = false)
    private Long size;

    @Column
    private String encoding;

    @Column
    private String delimiter;

    /**
     * ID конфигурации маппинга, если она была указана при загрузке
     */
    @Column(name = "mapping_config_id")
    private Long mappingConfigId;

    /**
     * Дата и время загрузки файла
     */
    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;


    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
    }

    public void updateTextParameters(TextFileParameters parameters) {
        this.encoding = parameters.getEncoding();
        this.delimiter = String.valueOf(parameters.getDelimiter());
    }

    /**
     * Проверяет, принадлежит ли файл указанному магазину
     */
    public boolean belongsToShop(Long shopId) {
        return this.clientId != null && this.clientId.equals(shopId);
    }
}
