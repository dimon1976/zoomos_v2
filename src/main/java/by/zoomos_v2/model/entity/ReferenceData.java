package by.zoomos_v2.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "reference_data")
@Getter
@Setter
public class ReferenceData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "retail_code")
    private String retailCode;

    @Column(name = "retail_name")
    private String retailName;

    @Column(name = "physical_address")
    private String physicalAddress;

    @Column(name = "region_code")
    private String regionCode;

    @Column(name = "region_name")
    private String regionName;

    @Column(name = "task_number")
    private String taskNumber;

    @Column(name = "client_id")
    private Long clientId;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @Column(name = "is_active")
    private boolean isActive;
}
