package by.zoomos_v2.service.file.export.strategy;

import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.model.entity.Product;
import by.zoomos_v2.model.RetailNetworkDirectory;
import by.zoomos_v2.service.file.BatchProcessingData;
import by.zoomos_v2.service.file.export.service.TaskValidationService;
import by.zoomos_v2.service.directory.RetailNetworkDirectoryService;
import by.zoomos_v2.service.statistics.OperationProgressTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Стратегия обработки данных на основе задания.
 * Фильтрует данные на основе задания и обогащает справочными данными.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskBasedProcessingStrategy implements DataProcessingStrategy {

    private final TaskValidationService taskValidationService;
    private final RetailNetworkDirectoryService directoryService;
    private final OperationProgressTracker progressTracker;

    @Override
    public List<Map<String, Object>> processData(List<Map<String, Object>> data,
                                                 ExportConfig exportConfig,
                                                 BatchProcessingData batchProcessingData) {
        log.info("Начало обработки данных с использованием стратегии на основе задания");

        try {
            // Получаем номер задания из конфигурации
            String taskNumber = getTaskNumber(exportConfig);
            log.debug("Номер задания для обработки: {}", taskNumber);

            // Получаем ключи валидации из задания
            Set<String> validationKeys = taskValidationService.createValidationKeysForTask(taskNumber);
            log.debug("Получено {} ключей валидации из задания", validationKeys.size());

            // Фильтруем и обрабатываем данные
            List<Map<String, Object>> processedData = filterAndEnrichData(data, validationKeys);

            // Обновляем статистику
            batchProcessingData.setSuccessCount(processedData.size());

            log.info("Обработка данных завершена. Обработано {} записей из {}",
                    processedData.size(), data.size());

            return processedData;

        } catch (Exception e) {
            log.error("Ошибка при обработке данных: {}", e.getMessage(), e);
            throw new IllegalStateException("Ошибка обработки данных: " + e.getMessage(), e);
        }
    }

    /**
     * Фильтрует и обогащает данные
     */
    private List<Map<String, Object>> filterAndEnrichData(List<Map<String, Object>> data,
                                                          Set<String> validationKeys) {
        // Собираем все коды розничных сетей для оптимизации запросов к справочнику
        Set<String> retailCodes = data.stream()
                .map(row -> (String) row.get("competitordata.competitorAdditional"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Загружаем справочник
        Map<String, RetailNetworkDirectory> directoryMap = directoryService.getDirectoryMap(
                new ArrayList<>(retailCodes)
        );

        return data.stream()
                .filter(row -> isValidRow(row, validationKeys))
                .map(row -> enrichRowWithDirectory(row, directoryMap))
                .collect(Collectors.toList());
    }

    /**
     * Проверяет валидность строки по ключам из задания
     */
    private boolean isValidRow(Map<String, Object> row, Set<String> validationKeys) {
        String validationKey = String.format("%s_%s_%s",
                row.get("product.productId"),
                row.get("product.productCategory1"),
                row.get("competitordata.competitorAdditional"));

        return validationKeys.contains(validationKey);
    }

    /**
     * Обогащает строку данными из справочника
     */
    private Map<String, Object> enrichRowWithDirectory(Map<String, Object> row,
                                                       Map<String, RetailNetworkDirectory> directoryMap) {
        String retailCode = (String) row.get("competitordata.competitorAdditional");
        if (retailCode != null) {
            RetailNetworkDirectory directory = directoryMap.get(retailCode);
            if (directory != null) {
                row.put("competitordata.competitorName", directory.getRetailName());
                row.put("regiondata.region", directory.getRegionName());
                row.put("regiondata.regionAddress", directory.getPhysicalAddress());
            }
        }
        return row;
    }

    /**
     * Получает номер задания из конфигурации
     */
    private String getTaskNumber(ExportConfig exportConfig) {
        String taskNumber = exportConfig.getParam("taskNumber");
        if (taskNumber == null || taskNumber.isEmpty()) {
            throw new IllegalArgumentException("Не указан номер задания в конфигурации экспорта");
        }
        return taskNumber;
    }

    @Override
    public boolean supports(ExportConfig exportConfig) {
        return ProcessingStrategyType.TASK_BASED_FILTER.equals(exportConfig.getStrategyType());
    }

    @Override
    public ProcessingStrategyType getStrategyType() {
        return ProcessingStrategyType.TASK_BASED_FILTER;
    }
}
