package by.zoomos_v2.model.operation;

import by.zoomos_v2.model.enums.OperationType;
import by.zoomos_v2.model.enums.OperationStatus;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Базовый класс для всех операций в системе
 */
@Entity
@Data
@Inheritance(strategy = InheritanceType.JOINED)
@Table(name = "operations")
public abstract class BaseOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OperationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OperationStatus status;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "source_identifier")
    private String sourceIdentifier;

    @Type(JsonBinaryType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Type(JsonBinaryType.class)
    @Column(name = "errors", columnDefinition = "jsonb")
    private List<String> errors = new ArrayList<>();

    @Column(name = "total_records")
    private Integer totalRecords;

    @Column(name = "processed_records")
    private Integer processedRecords;

    @Column(name = "failed_records")
    private Integer failedRecords;

    @Column(name = "processing_time_seconds")
    private Long processingTimeSeconds;

    public void addError(String error) {
        if (errors == null) {
            errors = new ArrayList<>();
        }
        errors.add(error);
    }

    public void updateProcessingStatistics(Integer totalRecords, Integer processedRecords, Integer failedRecords) {
        this.totalRecords = totalRecords;
        this.processedRecords = processedRecords;
        this.failedRecords = failedRecords;
    }

    @PrePersist
    protected void onCreate() {
        startTime = LocalDateTime.now();
        if (status == null) {
            status = OperationStatus.PENDING;
        }
    }
}