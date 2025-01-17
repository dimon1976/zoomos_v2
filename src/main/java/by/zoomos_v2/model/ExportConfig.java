package by.zoomos_v2.model;

import jakarta.persistence.*;
import lombok.Data;
import java.util.List;

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

    @Column(name = "is_default")
    private boolean isDefault;

    @OneToMany(mappedBy = "exportConfig", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<ExportField> fields;
}
