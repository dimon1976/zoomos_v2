package by.zoomos_v2.model.operation;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Type;

import java.util.Map;

/**
 * Сущность для хранения статистики операций экспорта данных
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "export_operations")
public class ExportOperation extends BaseOperation {

    @Column(name = "export_format")
    private String exportFormat;

    @Column(name = "target_path")
    private String targetPath;

    @Column(name = "files_generated")
    private Integer filesGenerated;

    @Column(name = "processing_strategy")
    private String processingStrategy;

    @Type(JsonBinaryType.class)
    @Column(name = "export_config", columnDefinition = "jsonb")
    private Map<String, Object> exportConfig;
}
