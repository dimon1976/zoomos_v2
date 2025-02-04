package by.zoomos_v2.service.statistics;

import by.zoomos_v2.DTO.operation.ExportOperationDTO;
import by.zoomos_v2.DTO.operation.ImportOperationDTO;
import by.zoomos_v2.DTO.operation.ImportStatsSummaryDTO;
import by.zoomos_v2.model.enums.OperationStatus;
import by.zoomos_v2.model.enums.OperationType;
import by.zoomos_v2.model.operation.BaseOperation;
import by.zoomos_v2.model.operation.ExportOperation;
import by.zoomos_v2.model.operation.ImportOperation;
import by.zoomos_v2.repository.BaseOperationRepository;
import by.zoomos_v2.repository.ExportOperationRepository;
import by.zoomos_v2.repository.ImportOperationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Сервис для работы со статистикой операций
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationStatsService {

    private final ImportOperationRepository importOperationRepository;
    private final ExportOperationRepository exportOperationRepository;
    private final List<BaseOperationRepository<? extends BaseOperation>> repositories;

    /**
     * Создает новую операцию заданного типа
     */
    @Transactional
    public <T extends BaseOperation> T createOperation(T operation) {
        operation.setStartTime(LocalDateTime.now());
        operation.setStatus(OperationStatus.PENDING);
        BaseOperationRepository<T> repository = getRepositoryForType((Class<T>) operation.getClass());
        return repository.save(operation);
    }

    /**
     * Обновляет статус операции
     */
    @Transactional
    public void updateOperationStatus(Long operationId, OperationStatus status, String error, String errorType) {
        Optional<? extends BaseOperation> operationOpt = findOperation(operationId);
        if (operationOpt.isPresent()) {
            BaseOperation operation = operationOpt.get();
            operation.setStatus(status);
            if (error != null) {
                operation.addError(error, errorType);
            }
            if (status.equals(OperationStatus.COMPLETED) ||
                    status.equals(OperationStatus.FAILED) ||
                    status.equals(OperationStatus.CANCELLED)) {
                operation.setEndTime(LocalDateTime.now());
            }
            getRepositoryForType((Class<BaseOperation>) operation.getClass()).save(operation);
        }
    }

    public ImportStatsSummaryDTO getImportStatsSummary(Long clientId) {
        List<ImportOperation> imports = importOperationRepository.findByClientIdOrderByStartTimeDesc(clientId);

        return ImportStatsSummaryDTO.builder()
                .totalFiles((long) imports.size())
                .totalRecordsProcessed(calculateTotalRecords(imports))
                .averageSuccessRate(calculateAverageSuccessRate(imports))
                .lastImportDate(getLastImportDate(imports))
                .recentImports(getRecentImports(imports))
                .errorsByType(getErrorsByType(imports))
                .operationsByStatus(getOperationsByStatus(imports))
                .build();
    }

    private long calculateTotalRecords(List<ImportOperation> imports) {
        return imports.stream()
                .mapToLong(op -> op.getProcessedRecords() != null ? op.getProcessedRecords() : 0)
                .sum();
    }

    private double calculateAverageSuccessRate(List<ImportOperation> imports) {
        return imports.stream()
                .filter(op -> op.getTotalRecords() != null && op.getTotalRecords() > 0)
                .mapToDouble(op -> (double) op.getProcessedRecords() / op.getTotalRecords() * 100)
                .average()
                .orElse(0.0);
    }

    private LocalDateTime getLastImportDate(List<ImportOperation> imports) {
        return imports.isEmpty() ? null : imports.get(0).getStartTime();
    }

    private List<ImportOperationDTO> getRecentImports(List<ImportOperation> imports) {
        return imports.stream()
                .limit(10)
                .map(this::mapToImportDTO)
                .collect(Collectors.toList());
    }

    private Map<String, Long> getErrorsByType(List<ImportOperation> imports) {
        return imports.stream()
                .filter(op -> op.getErrors() != null && !op.getErrors().isEmpty())
                .flatMap(op -> op.getErrors().stream())
                .collect(Collectors.groupingBy(
                        error -> error,
                        Collectors.counting()
                ));
    }

    private Map<OperationStatus, Long> getOperationsByStatus(List<ImportOperation> imports) {
        return imports.stream()
                .collect(Collectors.groupingBy(
                        BaseOperation::getStatus,
                        Collectors.counting()
                ));
    }

    public List<ImportOperationDTO> getClientImportOperations(Long clientId) {
        return importOperationRepository.findByClientId(clientId).stream()
                .map(this::mapToImportDTO)
                .collect(Collectors.toList());
    }

    private ImportOperationDTO convertToDTO(ImportOperation operation) {
        return ImportOperationDTO.builder()
                .id(operation.getId())
                .status(operation.getStatus())
                .startTime(operation.getStartTime())
                // ... остальные поля
                .build();
    }

    private ImportOperationDTO mapToImportDTO(ImportOperation operation) {
        return ImportOperationDTO.builder()
                .id(operation.getId())
                .clientId(operation.getClientId())
                .type(operation.getType())
                .status(operation.getStatus())
                .startTime(operation.getStartTime())
                .endTime(operation.getEndTime())
                .totalRecords(operation.getTotalRecords())
                .processedRecords(operation.getProcessedRecords())
                .failedRecords(operation.getFailedRecords())
                .fileName(operation.getFileName())
                .fileSize(operation.getFileSize())
                .fileFormat(operation.getFileFormat())
                .processingSpeed(operation.getProcessingSpeed())
                .encoding(operation.getEncoding())
                .delimiter(operation.getDelimiter())
                .errors(operation.getErrors())
                .build();
    }

    /**
     * Обновляет статистику обработки
     */
    @Transactional
    public void updateProcessingStats(Long operationId, Integer totalRecords,
                                      Integer processedRecords, Integer failedRecords) {
        Optional<? extends BaseOperation> operationOpt = findOperation(operationId);
        if (operationOpt.isPresent()) {
            BaseOperation operation = operationOpt.get();
            operation.updateProcessingStatistics(totalRecords, processedRecords, failedRecords);
            getRepositoryForType((Class<BaseOperation>) operation.getClass()).save(operation);
        }
    }

    /**
     * Получает статистику по операциям клиента
     */
    @Transactional(readOnly = true)
    public List<? extends BaseOperation> getClientOperations(Long clientId, OperationType type) {
        return repositories.stream()
                .flatMap(repo ->
                        // Если clientId == null, получаем все операции, иначе — по clientId
                        (clientId == null ? repo.findAll() : repo.findByClientId(clientId)).stream()
                )
                .filter(op -> type == null || op.getType().equals(type))
                .toList();
    }

    /**
     * Находит операцию по ID
     */
    private Optional<? extends BaseOperation> findOperation(Long operationId) {
        return repositories.stream()
                .map(repo -> repo.findById(operationId))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    /**
     * Получает подходящий репозиторий для типа операции
     */
    @SuppressWarnings("unchecked")
    private <T extends BaseOperation> BaseOperationRepository<T> getRepositoryForType(Class<T> operationType) {
        for (BaseOperationRepository<?> repo : repositories) {
            ResolvableType resolvableType = ResolvableType.forClass(repo.getClass())
                    .as(BaseOperationRepository.class);
            Class<?> repoEntityType = resolvableType.getGenerics()[0].resolve();

            if (repoEntityType != null && operationType.equals(repoEntityType)) {
                @SuppressWarnings("unchecked")
                BaseOperationRepository<T> typedRepo = (BaseOperationRepository<T>) repo;
                return typedRepo;
            }
        }
        throw new IllegalArgumentException("Repository not found for type: " + operationType);
    }

    @Transactional(readOnly = true)
    public List<ExportOperationDTO> getClientExportOperations(Long clientId) {
        return exportOperationRepository.findByClientId(clientId).stream()
                .map(this::mapToExportDTO)
                .collect(Collectors.toList());
    }

    private ExportOperationDTO mapToExportDTO(ExportOperation operation) {
        return ExportOperationDTO.builder()
                .id(operation.getId())
                .clientId(operation.getClientId())
                .type(operation.getType())
                .status(operation.getStatus())
                .startTime(operation.getStartTime())
                .endTime(operation.getEndTime())
                .totalRecords(operation.getTotalRecords())
                .processedRecords(operation.getProcessedRecords())
                .failedRecords(operation.getFailedRecords())
                .exportFormat(operation.getExportFormat())
                .targetPath(operation.getTargetPath())
                .filesGenerated(operation.getFilesGenerated())
                .processingStrategy(operation.getProcessingStrategy())
                .errors(operation.getErrors())
                .build();
    }

    //    @Transactional(readOnly = true)
//    public ImportOperation findImportOperationBySourceIdentifier(String sourceIdentifier) {
//        return importOperationRepository.findFirstBySourceIdentifierOrderByStartTimeDesc(sourceIdentifier);
//    }
    @Transactional(readOnly = true)
    public ImportOperation findLastOperationBySourceAndClient(String sourceIdentifier, Long clientId) {
        return importOperationRepository.findLastOperationBySourceAndClient(sourceIdentifier, clientId);
    }
}