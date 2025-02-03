package by.zoomos_v2.service.statistics;

import by.zoomos_v2.model.enums.OperationStatus;
import by.zoomos_v2.model.enums.OperationType;
import by.zoomos_v2.model.operation.BaseOperation;
import by.zoomos_v2.repository.BaseOperationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Сервис для работы со статистикой операций
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationStatsService {

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
    public void updateOperationStatus(Long operationId, OperationStatus status, String error) {
        Optional<? extends BaseOperation> operationOpt = findOperation(operationId);
        if (operationOpt.isPresent()) {
            BaseOperation operation = operationOpt.get();
            operation.setStatus(status);
            if (error != null) {
                operation.addError(error);
            }
            if (status.equals(OperationStatus.COMPLETED) ||
                    status.equals(OperationStatus.FAILED) ||
                    status.equals(OperationStatus.CANCELLED)) {
                operation.setEndTime(LocalDateTime.now());
            }
            getRepositoryForType((Class<BaseOperation>) operation.getClass()).save(operation);
        }
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
                .flatMap(repo -> repo.findByClientId(clientId).stream())
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
}