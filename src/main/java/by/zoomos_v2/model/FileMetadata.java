package by.zoomos_v2.model;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Сущность для хранения метаданных загруженных файлов.
 * Содержит информацию о загруженных файлах и их состоянии обработки.
 */
@Entity
@Data
@NoArgsConstructor
@Table(name = "file_metadata")
public class FileMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ID магазина, которому принадлежит файл
     */
    @Column(name = "shop_id", nullable = false)
    private Long clientId;

    /**
     * Оригинальное имя файла
     */
    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    /**
     * Имя файла в системе хранения
     */
    @Column(name = "stored_filename", nullable = false)
    private String storedFilename;

    /**
     * Тип файла (CSV, EXCEL и т.д.)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false)
    private FileType fileType;

    /**
     * MIME-тип файла
     */
    @Column(name = "content_type")
    private String contentType;

    /**
     * Размер файла в байтах
     */
    @Column(nullable = false)
    private Long size;

    @Column
    private String encoding;

    @Column
    private String delimiter;

    /**
     * Статус обработки файла
     */
    @Column(nullable = false)
    private String status = "PENDING"; // PENDING, PROCESSING, COMPLETED, ERROR, CANCELLED

    /**
     * ID конфигурации маппинга, если она была указана при загрузке
     */
    @Column(name = "mapping_config_id")
    private Long mappingConfigId;

    /**
     * Сообщение об ошибке, если она возникла при обработке
     */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    /**
     * Дата и время загрузки файла
     */
    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    /**
     * Дата и время начала обработки файла
     */
    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    /**
     * Дата и время завершения обработки файла
     */
    @Column(name = "processing_completed_at")
    private LocalDateTime processingCompletedAt;

    /**
     * Результаты обработки файла в формате JSON
     */
    @Type(JsonBinaryType.class)
    @Column(name = "processing_results", columnDefinition = "jsonb")
    private String processingResults;


    /**
     * Общее количество записей в файле
     */
    @Column(name = "total_records")
    private Integer totalRecords;

    /**
     * Количество успешно обработанных записей
     */
    @Column(name = "success_records")
    private Integer successRecords;

    /**
     * Количество записей с ошибками
     */
    @Column(name = "failed_records")
    private Integer failedRecords;

    /**
     * Список ошибок, возникших при обработке файла
     */
    @Type(JsonBinaryType.class)
    @Column(name = "processing_errors", columnDefinition = "jsonb")
    private List<String> processingErrors;


    /**
     * Добавляет ошибку в список ошибок обработки файла
     *
     * @param error текст ошибки
     */
    public void addProcessingError(String error) {
        if (processingErrors == null) {
            processingErrors = new ArrayList<>();
        }
        processingErrors.add(error);
    }

    /**
     * Обновляет статистику обработки файла
     *
     * @param totalRecords общее количество записей
     * @param successRecords количество успешно обработанных записей
     * @param failedRecords количество записей с ошибками
     */
    public void updateProcessingStatistics(Integer totalRecords, Integer successRecords,
                                           Integer failedRecords) {
        this.totalRecords = totalRecords;
        this.successRecords = successRecords;
        this.failedRecords = failedRecords;
    }

    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
        if (status == null) {
            status = "PENDING";
        }
    }

    public void updateTextParameters(TextFileParameters parameters) {
        this.encoding = parameters.getEncoding();
        this.delimiter = String.valueOf(parameters.getDelimiter());
    }

    /**
     * Проверяет, принадлежит ли файл указанному магазину
     */
    public boolean belongsToShop(Long shopId) {
        return this.clientId != null && this.clientId.equals(shopId);
    }

    /**
     * Проверяет, завершена ли обработка файла
     */
    public boolean isProcessingCompleted() {
        return "COMPLETED".equals(status) || "ERROR".equals(status) || "CANCELLED".equals(status);
    }

    /**
     * Проверяет, находится ли файл в процессе обработки
     */
    public boolean isProcessing() {
        return "PROCESSING".equals(status);
    }

    /**
     * Обновляет статус обработки файла
     */
    public void updateStatus(String newStatus, String errorMessage) {
        this.status = newStatus;
        this.errorMessage = errorMessage;

        if ("PROCESSING".equals(newStatus)) {
            this.processingStartedAt = LocalDateTime.now();
        } else if (isProcessingCompleted()) {
            this.processingCompletedAt = LocalDateTime.now();
        }
    }
}
