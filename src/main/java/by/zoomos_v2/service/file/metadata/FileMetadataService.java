package by.zoomos_v2.service.file.metadata;

import by.zoomos_v2.DTO.FileInfoDTO;
import by.zoomos_v2.model.FileMetadata;
import by.zoomos_v2.model.enums.OperationStatus;
import by.zoomos_v2.model.operation.ImportOperation;
import by.zoomos_v2.repository.FileMetadataRepository;
import by.zoomos_v2.repository.ImportOperationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Сервис для работы с метаданными файлов.
 * Предоставляет методы для обновления статусов и результатов обработки файлов.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileMetadataService {

    private final FileMetadataRepository fileMetadataRepository;
    private final ImportOperationRepository importOperationRepository;

//    /**
//     * Обновляет статус файла и соответствующие временные метки.
//     *
//     * @param metadata метаданные файла для обновления
//     * @param status новый статус файла (PROCESSING, COMPLETED, ERROR, CANCELLED)
//     * @param errorMessage сообщение об ошибке (если есть)
//     */
//    @Transactional
//    public void updateStatus(FileMetadata metadata, String status, String errorMessage) {
//        log.debug("Обновление статуса файла {} на {}", metadata.getId(), status);
//
//        metadata.setStatus(status);
//        metadata.setErrorMessage(errorMessage);
//
//        switch (status) {
//            case "PROCESSING" -> metadata.setProcessingStartedAt(LocalDateTime.now());
//            case "COMPLETED", "ERROR", "CANCELLED", "COMPLETED_WITH_ERRORS" ->
//                    metadata.setProcessingCompletedAt(LocalDateTime.now());
//        }
//
//        fileMetadataRepository.save(metadata);
//    }
//
//    /**
//     * Обновляет результаты обработки файла на основе операции импорта
//     * @param metadata метаданные файла
//     * @param operation операция импорта
//     */
//    @Transactional
//    public void updateProcessingResults(FileMetadata metadata, ImportOperation operation) {
//        try {
//            // Сохраняем метрики операции
//            ObjectNode operationMetrics = objectMapper.createObjectNode()
//                    .put("totalRecords", operation.getTotalRecords())
//                    .put("processedRecords", operation.getProcessedRecords())
//                    .put("failedRecords", operation.getFailedRecords())
//                    .put("processingTimeSeconds", operation.getProcessingTimeSeconds())
//                    .put("processingSpeed", operation.getProcessingSpeed());
//
//            // Добавляем дополнительные метаданные
//            if (operation.getMetadata() != null) {
//                operationMetrics.set("metadata", objectMapper.valueToTree(operation.getMetadata()));
//            }
//
//            metadata.setProcessingResults(objectMapper.writeValueAsString(operationMetrics));
//
//            // Обновляем статистику
//            metadata.updateProcessingStatistics(
//                    operation.getTotalRecords(),
//                    operation.getProcessedRecords(),
//                    operation.getFailedRecords()
//            );
//
//            // Определяем финальный статус
//            if (operation.getErrors().isEmpty()) {
//                updateStatus(metadata, "COMPLETED", null);
//            } else {
//                String message = String.format("Обработано с ошибками (%d из %d записей)",
//                        operation.getFailedRecords(), operation.getTotalRecords());
//                updateStatus(metadata, "COMPLETED_WITH_ERRORS", message);
//            }
//
//            log.info("Файл {} обработан. Всего: {}, Успешно: {}, Ошибок: {}",
//                    metadata.getOriginalFilename(),
//                    operation.getTotalRecords(),
//                    operation.getProcessedRecords(),
//                    operation.getFailedRecords());
//        } catch (Exception e) {
//            log.error("Ошибка при сохранении результатов обработки: {}", e.getMessage(), e);
//            updateStatus(metadata, "ERROR", "Ошибка при сохранении результатов: " + e.getMessage());
//        }
//    }
//
//    /**
//     * Отмечает файл как обработанный с ошибкой.
//     *
//     * @param fileId идентификатор файла
//     * @param error информация об ошибке
//     */
//    @Transactional
//    public void markAsError(Long fileId, String error) {
//        log.error("Ошибка при обработке файла {}: {}", fileId, error);
//        try {
//            FileMetadata metadata = fileMetadataRepository.findById(fileId)
//                    .orElseThrow(() -> new IllegalArgumentException("Файл не найден"));
//            updateStatus(metadata, "ERROR", error);
//        } catch (Exception ex) {
//            log.error("Ошибка при обновлении статуса файла: {}", ex.getMessage(), ex);
//        }
//    }
//
//    /**
//     * Получает список доступных для экспорта файлов клиента
//     * (только успешно обработанные файлы)
//     */
//    @Transactional(readOnly = true)
//    public List<FileMetadata> getFilesByClientId(Long clientId) {
//        return fileMetadataRepository.findByClientIdAndStatusOrderByUploadedAtDesc(clientId, "COMPLETED");
//    }

    /**
     * Создает DTO с информацией о файле и его статусе
     */
    public FileInfoDTO createFileInfo(FileMetadata metadata, Long clientId) {
        ImportOperation operation = importOperationRepository
                .findLastOperationBySourceAndClient(metadata.getOriginalFilename(), clientId);

        return FileInfoDTO.builder()
                .id(metadata.getId())
                .originalFilename(metadata.getOriginalFilename())
                .fileType(metadata.getFileType())
                .uploadedAt(metadata.getUploadedAt())
                .status(operation != null ? operation.getStatus() : OperationStatus.PENDING)
                .errorMessage(operation != null && !operation.getErrors().isEmpty() ?
                        operation.getErrors().get(0) : null)
                .totalRecords(operation != null && operation.getTotalRecords() != null ?
                        operation.getTotalRecords() : 0)
                .build();
    }




    /**
     * Получает список файлов с информацией об их обработке для клиента
     */
    @Transactional(readOnly = true)
    public List<FileInfoDTO> getFilesInfoByClientId(Long clientId) {
        List<FileMetadata> files = fileMetadataRepository.findByClientIdOrderByUploadedAtDesc(clientId);
        return files.stream()
                .map(file -> {
                    ImportOperation operation = importOperationRepository
                            .findLastOperationBySourceAndClient(file.getOriginalFilename(), clientId);

                    return FileInfoDTO.builder()
                            .id(file.getId())
                            .originalFilename(file.getOriginalFilename())
                            .fileType(file.getFileType())
                            .uploadedAt(file.getUploadedAt())
                            .status(operation != null ? operation.getStatus() : OperationStatus.PENDING)
                            .errorMessage(operation != null && !operation.getErrors().isEmpty() ?
                                    operation.getErrors().get(0) : null)
                            .totalRecords(operation != null && operation.getTotalRecords() != null ?
                                    operation.getTotalRecords() : 0)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Получает информацию о файле по ID
     */
    @Transactional(readOnly = true)
    public FileMetadata getFileById(Long fileId) {
        return fileMetadataRepository.findById(fileId).orElse(null);
    }

    /**
     * Получает список файлов клиента
     */
    @Transactional(readOnly = true)
    public List<FileMetadata> getFilesByClientId(Long clientId) {
        return fileMetadataRepository.findByClientIdOrderByUploadedAtDesc(clientId);
    }

    /**
     * Проверяет доступность файла для клиента
     */
    @Transactional(readOnly = true)
    public boolean isFileAvailableForClient(Long fileId, Long clientId) {
        FileMetadata metadata = fileMetadataRepository.findById(fileId).orElse(null);
        return metadata != null && metadata.belongsToShop(clientId);
    }

    /**
     * Сохраняет или обновляет метаданные файла
     */
    @Transactional
    public FileMetadata save(FileMetadata metadata) {
        return fileMetadataRepository.save(metadata);
    }
}