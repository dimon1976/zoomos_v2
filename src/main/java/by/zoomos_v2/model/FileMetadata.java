package by.zoomos_v2.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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

    @Column(name = "shop_id", nullable = false)
    private Long clientId;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "stored_filename", nullable = false)
    private String storedFilename;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false)
    private FileType fileType;

    @Column(name = "content_type")
    private String contentType;

    @Column(nullable = false)
    private Long size;

    @Column
    private String encoding;

    @Column
    private String delimiter;

    @Column(name = "mapping_config_id")
    private Long mappingConfigId;

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
