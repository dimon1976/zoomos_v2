package by.zoomos_v2.service.statistics.dashboard;

import by.zoomos_v2.DTO.dashboard.ClientDashboardStatsDTO;
import by.zoomos_v2.DTO.dashboard.DashboardOverviewDTO;
import by.zoomos_v2.DTO.dashboard.SystemResourcesDTO;
import by.zoomos_v2.DTO.operation.OperationStatsDTO;
import by.zoomos_v2.model.enums.OperationStatus;
import by.zoomos_v2.model.enums.OperationType;
import by.zoomos_v2.model.operation.BaseOperation;
import by.zoomos_v2.model.operation.ImportOperation;
import by.zoomos_v2.service.statistics.OperationStatsService;
import by.zoomos_v2.service.statistics.StatisticsProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardStatisticsService {
    private final OperationStatsService operationStatsService;
    private final StatisticsProcessor statisticsProcessor;

    /**
     * Получает общую статистику по клиенту для дашборда
     * @param clientId ID клиента
     * @param from дата начала периода
     * @param to дата окончания периода
     * @return DTO со статистикой дашборда
     */
    @Transactional(readOnly = true)
    public DashboardOverviewDTO getDashboardOverview(Long clientId, LocalDateTime from, LocalDateTime to) {
        log.debug("Получение статистики дашборда для клиента {}, период: {} - {}", clientId, from, to);

        // Получаем все операции клиента
        List<? extends BaseOperation> allOperations = operationStatsService.getClientOperations(clientId, null);

        // Получаем операции за указанный период
        List<? extends BaseOperation> periodOperations = filterOperationsByPeriod(allOperations, from, to);

        return DashboardOverviewDTO.builder()
                .stats(buildClientStats(allOperations, periodOperations))
                .recentOperations(getRecentOperations(periodOperations))
                .systemResources(buildSystemResources())
                .build();
    }

    /**
     * Формирует статистику по клиенту
     */
    private ClientDashboardStatsDTO buildClientStats(List<? extends BaseOperation> allOperations,
                                                     List<? extends BaseOperation> periodOperations) {
        return ClientDashboardStatsDTO.builder()
                .totalFiles(countTotalFiles(allOperations))
                .totalSizeBytes(calculateTotalSize(allOperations))
                .formattedTotalSize(statisticsProcessor.formatBytes(calculateTotalSize(allOperations)))
                .recentFilesCount(countTotalFiles(periodOperations))
                .activeOperationsCount(countActiveOperations(allOperations))
                .operationsByStatus(groupOperationsByStatus(allOperations))
                .operationsByType(groupOperationsByType(allOperations))
                .overallSuccessRate(calculateSuccessRate(allOperations))
                .lastOperationDate(findLastOperationDate(allOperations))
                .build();
    }

    /**
     * Получает список последних операций
     */
    private List<OperationStatsDTO> getRecentOperations(List<? extends BaseOperation> operations) {
        return operations.stream()
                .sorted(Comparator.comparing(BaseOperation::getStartTime).reversed())
                .limit(20)
                .map(this::mapToOperationStatsDTO)
                .collect(Collectors.toList());
    }

    /**
     * Формирует информацию о системных ресурсах
     */
    private SystemResourcesDTO buildSystemResources() {
        return SystemResourcesDTO.builder()
                .peakMemoryUsage(statisticsProcessor.getPeakMemoryUsage())
                .currentMemoryUsage(statisticsProcessor.getPeakMemoryUsage()) // используем тот же метод, так как логика идентична
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    /**
     * Фильтрует операции по периоду
     */
    private List<? extends BaseOperation> filterOperationsByPeriod(List<? extends BaseOperation> operations,
                                                                   LocalDateTime from,
                                                                   LocalDateTime to) {
        if (from == null && to == null) {
            // Если период не указан, берем последние 2 недели
            from = LocalDateTime.now().minusWeeks(2);
            to = LocalDateTime.now();
        }

        LocalDateTime finalFrom = from;
        LocalDateTime finalTo = to;

        return operations.stream()
                .filter(op -> isOperationInPeriod(op, finalFrom, finalTo))
                .collect(Collectors.toList());
    }

    private boolean isOperationInPeriod(BaseOperation operation, LocalDateTime from, LocalDateTime to) {
        return operation.getStartTime() != null &&
                operation.getStartTime().isAfter(from) &&
                operation.getStartTime().isBefore(to);
    }

    // Вспомогательные методы для подсчета статистики

    private long countTotalFiles(List<? extends BaseOperation> operations) {
        return operations.stream()
                .filter(op -> op.getType() == OperationType.IMPORT)
                .count();
    }

    private long calculateTotalSize(List<? extends BaseOperation> operations) {
        return operations.stream()
                .filter(op -> op instanceof ImportOperation)
                .mapToLong(op -> ((ImportOperation) op).getFileSize() != null ?
                        ((ImportOperation) op).getFileSize() : 0)
                .sum();
    }

    private long countActiveOperations(List<? extends BaseOperation> operations) {
        return operations.stream()
                .filter(op -> op.getStatus() == OperationStatus.IN_PROGRESS)
                .count();
    }

    private Map<OperationStatus, Long> groupOperationsByStatus(List<? extends BaseOperation> operations) {
        return operations.stream()
                .collect(Collectors.groupingBy(
                        BaseOperation::getStatus,
                        Collectors.counting()
                ));
    }

    private Map<OperationType, Long> groupOperationsByType(List<? extends BaseOperation> operations) {
        return operations.stream()
                .collect(Collectors.groupingBy(
                        BaseOperation::getType,
                        Collectors.counting()
                ));
    }

    private double calculateSuccessRate(List<? extends BaseOperation> operations) {
        if (operations.isEmpty()) {
            return 0.0;
        }

        long successfulOperations = operations.stream()
                .filter(op -> op.getStatus() == OperationStatus.COMPLETED)
                .count();

        return (double) successfulOperations / operations.size() * 100;
    }

    private LocalDateTime findLastOperationDate(List<? extends BaseOperation> operations) {
        return operations.stream()
                .map(BaseOperation::getStartTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }


    private OperationStatsDTO mapToOperationStatsDTO(BaseOperation operation) {
        return OperationStatsDTO.builder()
                .id(operation.getId())
                .clientId(operation.getClientId())
                .type(operation.getType())
                .status(operation.getStatus())
                .startTime(operation.getStartTime())
                .endTime(operation.getEndTime())
                .sourceIdentifier(operation.getSourceIdentifier())
                .metadata(operation.getMetadata())
                .errors(operation.getErrors())
                .totalRecords(operation.getTotalRecords())
                .processedRecords(operation.getProcessedRecords())
                .failedRecords(operation.getFailedRecords())
                .processingTimeSeconds(operation.getProcessingTimeSeconds())
                .successRate(calculateOperationSuccessRate(operation))
                .build();
    }

    private double calculateOperationSuccessRate(BaseOperation operation) {
        if (operation.getTotalRecords() == null || operation.getTotalRecords() == 0) {
            return 0.0;
        }
        return (operation.getProcessedRecords() * 100.0) / operation.getTotalRecords();
    }
}