package by.zoomos_v2.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class FileMetadata {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;
    private String fileName;
    private String columnSchema;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;

}
