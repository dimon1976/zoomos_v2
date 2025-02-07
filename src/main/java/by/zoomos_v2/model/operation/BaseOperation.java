package by.zoomos_v2.model.operation;

import by.zoomos_v2.model.enums.OperationStatus;
import by.zoomos_v2.model.enums.OperationType;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
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
    /**
     * Уникальный идентификатор операции
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /**
     * Идентификатор клиента
     */
    @Column(name = "client_id", nullable = false)
    private Long clientId;
    /**
     * Тип операции (импорт, экспорт и т.д.)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OperationType type;
    /**
     * Статус выполнения операции
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OperationStatus status;
    /**
     * Время начала операции
     */
    @Column(name = "start_time")
    private LocalDateTime startTime;
    /**
     * Время завершения операции
     */
    @Column(name = "end_time")
    private LocalDateTime endTime;
    /**
     * Идентификатор источника данных:
     * - Для импорта: имя загруженного файла
     * - Для экспорта: id исходного файла
     * - Для утилит: описание источника данных
     */
    @Column(name = "source_identifier")
    private String sourceIdentifier;
    /**
     * Список ошибок, возникших при выполнении операции
     */
    @Type(JsonBinaryType.class)
    @Column(name = "errors", columnDefinition = "jsonb")
    private List<String> errors = new ArrayList<>();
    /**
     * Общее количество записей для обработки
     */
    @Column(name = "total_records")
    private Integer totalRecords;
    /**
     * Количество успешно обработанных записей
     */
    @Column(name = "processed_records")
    private Integer processedRecords;
    /**
     * Количество записей с ошибками
     */
    @Column(name = "failed_records")
    private Integer failedRecords;
    /**
     * Время выполнения операции в секундах
     */
    @Column(name = "processing_time_seconds")
    private Long processingTimeSeconds;
    /**
     * Скорость обработки (записей в секунду)
     */
    @Column(name = "processing_speed")
    private Double processingSpeed;
    /**
     * Типы ошибок и их количество
     */
    @Type(JsonBinaryType.class)
    @Column(name = "error_types", columnDefinition = "jsonb")
    private Map<String, Integer> errorTypes = new HashMap<>();
    /**
     * Дополнительные метаданные операции:
     * - параметры конфигурации
     * - технические метрики
     * - служебная информация
     */
    @Type(JsonBinaryType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    public void addError(String error, String errorType) {
        if (error != null) {
            errors.add(error);
        }
        if (errorType != null) {
            errorTypes.merge(errorType, 1, Integer::sum);
        }
    }

    public void updateProcessingStatistics(Integer totalRecords, Integer processedRecords, Integer failedRecords) {
        this.totalRecords = totalRecords;
        this.processedRecords = processedRecords;
        this.failedRecords = failedRecords;

        if (processedRecords > 0 && processingTimeSeconds > 0) {
            this.processingSpeed = processedRecords.doubleValue() / processingTimeSeconds;
        }
    }

    /**
     * Увеличивает счетчик обработанных записей
     *
     * @param count количество записей
     */
    public void incrementProcessedRecords(int count) {
        if (this.processedRecords == null) {
            this.processedRecords = 0;
        }
        this.processedRecords += count;
    }

    @PrePersist
    protected void onCreate() {
        startTime = LocalDateTime.now();
        status = status == null ? OperationStatus.PENDING : status;
    }
}