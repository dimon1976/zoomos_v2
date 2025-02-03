package by.zoomos_v2.model.operation;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Сущность для хранения статистики операций импорта данных
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "import_operations")
public class ImportOperation extends BaseOperation {

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_format")
    private String fileFormat;

    @Column(name = "mapping_config_id")
    private Long mappingConfigId;

    @Column(name = "processing_speed")
    private Double processingSpeed;

    @Column(name = "encoding")
    private String encoding;

    @Column(name = "delimiter")
    private String delimiter;

    /**
     * MIME-тип файла
     */
    @Column(name = "content_type")
    private String contentType;
}
