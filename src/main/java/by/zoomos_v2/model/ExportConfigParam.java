package by.zoomos_v2.model;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "export_config_params")
public class ExportConfigParam {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "export_config_id", nullable = false)
    private ExportConfig exportConfig;

    @Column(name = "param_key", nullable = false)
    private String key;

    @Column(name = "param_value")
    private String value;
}
