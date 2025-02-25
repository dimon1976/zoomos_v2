package by.zoomos_v2.model.operation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Сущность для хранения операций импорта данных.
 * Расширяет базовую операцию специфичными для импорта полями.
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "import_operations")
public class ImportOperation extends BaseOperation {
    /**
     * Оригинальное имя импортируемого файла
     */
    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_id")
    private Long fileId;

    /**
     * Размер файла в байтах
     */
    @Column(name = "file_size")
    private Long fileSize;
    /**
     * Формат файла (CSV, XLSX и т.д.)
     */
    @Column(name = "file_format")
    private String fileFormat;
    /**
     * Идентификатор конфигурации маппинга
     */
    @Column(name = "mapping_config_id")
    private Long mappingConfigId;
    /**
     * Скорость обработки записей в секунду
     */
    @Column(name = "processing_speed")
    private Double processingSpeed;
    /**
     * Кодировка файла
     */
    @Column(name = "encoding")
    private String encoding;
    /**
     * Разделитель для текстовых файлов
     */
    @Column(name = "delimiter")
    private String delimiter;

    /**
     * MIME-тип файла
     */
    @Column(name = "content_type")
    private String contentType;
}
