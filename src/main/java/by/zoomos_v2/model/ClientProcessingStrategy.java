package by.zoomos_v2.model;

import by.zoomos_v2.model.Client;
import by.zoomos_v2.service.file.export.strategy.ProcessingStrategyType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "client_processing_strategy")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientProcessingStrategy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "client_id")
    private Client client;

    @Enumerated(EnumType.STRING)
    @Column(name = "strategy_type")
    private ProcessingStrategyType strategyType;

    @Column(name = "is_active")
    private boolean isActive;

    @Column(name = "parameters", columnDefinition = "jsonb")
    private String parameters;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
