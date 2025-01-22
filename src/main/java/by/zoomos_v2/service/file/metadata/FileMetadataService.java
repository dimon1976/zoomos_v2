package by.zoomos_v2.service.file.metadata;

import by.zoomos_v2.model.FileMetadata;
import by.zoomos_v2.repository.FileMetadataRepository;
import by.zoomos_v2.service.file.ProcessingStats;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Сервис для работы с метаданными файлов.
 * Предоставляет методы для обновления статусов и результатов обработки файлов.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileMetadataService {

    private final FileMetadataRepository fileMetadataRepository;
    private final ObjectMapper objectMapper;

    /**
     * Обновляет статус файла и соответствующие временные метки.
     *
     * @param metadata метаданные файла для обновления
     * @param status новый статус файла (PROCESSING, COMPLETED, ERROR, CANCELLED)
     * @param errorMessage сообщение об ошибке (если есть)
     */
    @Transactional
    public void updateStatus(FileMetadata metadata, String status, String errorMessage) {
        log.debug("Обновление статуса файла {} на {}", metadata.getId(), status);

        metadata.setStatus(status);
        metadata.setErrorMessage(errorMessage);

        if ("PROCESSING".equals(status)) {
            metadata.setProcessingStartedAt(LocalDateTime.now());
        } else if ("COMPLETED".equals(status) || "ERROR".equals(status) || "CANCELLED".equals(status)) {
            metadata.setProcessingCompletedAt(LocalDateTime.now());
        }

        fileMetadataRepository.save(metadata);
    }

    /**
     * Обновляет результаты обработки файла.
     * Устанавливает итоговый статус на основе наличия ошибок.
     *
     * @param metadata метаданные файла
     * @param stats статистика обработки файла
     */
    @Transactional
    public void updateProcessingResults(FileMetadata metadata, ProcessingStats stats) {
        try {

            // Исключение из хранения в БД излишних данных в метаданных файла
            ObjectNode node = objectMapper.valueToTree(stats);
            node.remove("processedData");
            String s = objectMapper.writeValueAsString(node);
            metadata.setProcessingResults(s);
            metadata.updateProcessingStatistics(
                    stats.getTotalCount(),
                    stats.getSuccessCount(),
                    stats.getErrorCount()
            );

            if (stats.getErrorCount() > 0) {
                String message = String.format("Обработано с ошибками (%d из %d записей)",
                        stats.getErrorCount(), stats.getTotalCount());
                updateStatus(metadata, "COMPLETED_WITH_ERRORS", message);
            } else {
                updateStatus(metadata, "COMPLETED", null);
            }

            log.info("Файл {} обработан. Всего: {}, Успешно: {}, Ошибок: {}",
                    metadata.getOriginalFilename(),
                    stats.getTotalCount(),
                    stats.getSuccessCount(),
                    stats.getErrorCount());
        } catch (Exception e) {
            log.error("Ошибка при сохранении результатов обработки: {}", e.getMessage(), e);
            updateStatus(metadata, "ERROR", "Ошибка при сохранении результатов: " + e.getMessage());
        }
    }

    /**
     * Инициализирует обработку файла.
     * Устанавливает начальный статус и время начала обработки.
     *
     * @param fileId идентификатор файла
     * @return метаданные файла
     * @throws IllegalArgumentException если файл не найден
     */
    @Transactional
    public FileMetadata initializeProcessing(Long fileId) {
        FileMetadata metadata = fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("Файл не найден"));

        log.debug("Инициализация обработки файла {}", fileId);
        updateStatus(metadata, "PROCESSING", null);

        return metadata;
    }

    /**
     * Отмечает файл как обработанный с ошибкой.
     *
     * @param fileId идентификатор файла
     * @param error информация об ошибке
     */
    @Transactional
    public void markAsError(Long fileId, String error) {
        log.error("Ошибка при обработке файла {}: {}", fileId, error);
        try {
            FileMetadata metadata = fileMetadataRepository.findById(fileId)
                    .orElseThrow(() -> new IllegalArgumentException("Файл не найден"));
            updateStatus(metadata, "ERROR", error);
        } catch (Exception ex) {
            log.error("Ошибка при обновлении статуса файла: {}", ex.getMessage(), ex);
        }
    }

    /**
     * Получает список доступных для экспорта файлов клиента
     * (только успешно обработанные файлы)
     */
    @Transactional(readOnly = true)
    public List<FileMetadata> getFilesByClientId(Long clientId) {
        return fileMetadataRepository.findByClientIdAndStatusOrderByUploadedAtDesc(clientId, "COMPLETED");
    }

    /**
     * Получает информацию о конкретном файле
     *
     * @param fileId идентификатор файла
     * @return метаданные файла или null, если файл не найден
     */
    @Transactional(readOnly = true)
    public FileMetadata getFileById(Long fileId) {
        return fileMetadataRepository.findById(fileId).orElse(null);
    }

    /**
     * Проверяет доступность файла для экспорта
     */
    @Transactional(readOnly = true)
    public boolean isFileAvailableForExport(Long fileId, Long clientId) {
        FileMetadata metadata = fileMetadataRepository.findById(fileId).orElse(null);
        return metadata != null &&
                metadata.belongsToShop(clientId) &&
                "COMPLETED".equals(metadata.getStatus());
    }
}