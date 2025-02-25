package by.zoomos_v2.model.operation;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Type;

import java.util.Map;

/**
 * Сущность для хранения операций экспорта данных.
 * Расширяет базовую операцию специфичными для экспорта полями.
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "export_operations")
public class ExportOperation extends BaseOperation {
    /**
     * Формат экспорта (CSV, XLSX и т.д.)
     */
    @Column(name = "export_format")
    private String exportFormat;
    /**
     * Путь сохранения экспортированного файла
     */
    @Column(name = "target_path")
    private String targetPath;
    /**
     * Количество сгенерированных файлов
     */
    @Column(name = "files_generated")
    private Integer filesGenerated;
    /**
     * Название стратегии обработки данных
     */
    @Column(name = "processing_strategy")
    private String processingStrategy;
    /**
     * Конфигурация экспорта в JSON формате
     */
    @Type(JsonBinaryType.class)
    @Column(name = "export_config", columnDefinition = "jsonb")
    private Map<String, Object> exportConfig;
}
