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

    private Long clientId;
    private String taskNumber;
    private LocalDateTime uploadedAt;
    private boolean isActive;

    // Поля справочных данных
    @Column(columnDefinition = "jsonb")
    private String referenceDataJson;
}
