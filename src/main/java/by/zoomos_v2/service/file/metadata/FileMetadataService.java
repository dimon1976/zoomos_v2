package by.zoomos_v2.service.file.metadata;

import by.zoomos_v2.DTO.FileInfoDTO;
import by.zoomos_v2.model.FileMetadata;
import by.zoomos_v2.model.enums.OperationStatus;
import by.zoomos_v2.model.operation.BaseOperation;
import by.zoomos_v2.model.operation.ImportOperation;
import by.zoomos_v2.repository.FileMetadataRepository;
import by.zoomos_v2.repository.ImportOperationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
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
                    Optional<ImportOperation> operation = importOperationRepository
                            .findByFileId(file.getId());

                    return FileInfoDTO.builder()
                            .id(file.getId())
                            .originalFilename(file.getOriginalFilename())
                            .fileType(file.getFileType())
                            .uploadedAt(file.getUploadedAt())
                            .status(operation.map(BaseOperation::getStatus)
                                    .orElse(OperationStatus.PENDING))
                            .errorMessage(operation.map(op -> !op.getErrors().isEmpty() ?
                                            op.getErrors().get(0) : null)
                                    .orElse(null))
                            .totalRecords(operation.map(BaseOperation::getTotalRecords)
                                    .orElse(0))
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