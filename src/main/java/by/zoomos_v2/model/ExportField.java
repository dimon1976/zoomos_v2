package by.zoomos_v2.model;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "export_fields")
public class ExportField {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "export_config_id", nullable = false)
    private ExportConfig exportConfig;

    @Column(name = "source_field", nullable = false)
    private String sourceField;

    @Column(name = "display_name")
    private String displayName;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private boolean enabled;
}
