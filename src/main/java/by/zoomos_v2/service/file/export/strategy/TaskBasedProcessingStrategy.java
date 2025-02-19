package by.zoomos_v2.service.file.export.strategy;

import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.model.RetailNetworkDirectory;
import by.zoomos_v2.service.directory.RetailNetworkDirectoryService;
import by.zoomos_v2.service.file.BatchProcessingData;
import by.zoomos_v2.service.file.export.service.TaskValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Стратегия обработки данных на основе задания.
 * Фильтрует данные по заданию и обогащает их справочной информацией.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskBasedProcessingStrategy implements DataProcessingStrategy {

    private final TaskValidationService taskValidationService;
    private final RetailNetworkDirectoryService directoryService;

    @Override
    public List<Map<String, Object>> processData(List<Map<String, Object>> data,
                                                 ExportConfig exportConfig,
                                                 BatchProcessingData batchProcessingData) {
        String taskNumber = getTaskNumber(exportConfig);
        log.info("Начало обработки данных по заданию {}. Количество записей: {}",
                taskNumber, data.size());

        try {
            // Получаем ключи валидации из задания
            Set<String> validationKeys = taskValidationService.createValidationKeysForTask(taskNumber);
            log.debug("Получено {} ключей валидации из задания", validationKeys.size());

            // Собираем все коды розничных сетей для оптимизации запросов к справочнику
            Set<String> retailCodes = data.stream()
                    .map(row -> (String) row.get("competitordata.competitorAdditional"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            // Загружаем справочник
            Map<String, RetailNetworkDirectory> directoryMap = directoryService.getDirectoryMap(
                    new ArrayList<>(retailCodes)
            );

            // Фильтруем и обогащаем данные
            List<Map<String, Object>> processedData = data.stream()
                    .filter(row -> isValidRow(row, validationKeys))
                    .map(row -> enrichRowWithDirectory(row, directoryMap))
                    .collect(Collectors.toList());

            // Обновляем статистику
            batchProcessingData.setSuccessCount(processedData.size());

            log.info("Обработка данных завершена. Отфильтровано записей: {}", processedData.size());
            return processedData;

        } catch (Exception e) {
            log.error("Ошибка при обработке данных: {}", e.getMessage(), e);
            throw new IllegalStateException("Ошибка обработки данных: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean supports(ExportConfig exportConfig) {
        return ProcessingStrategyType.TASK_BASED_FILTER.equals(exportConfig.getStrategyType());
    }

    @Override
    public ProcessingStrategyType getStrategyType() {
        return ProcessingStrategyType.TASK_BASED_FILTER;
    }

    @Override
    public List<StrategyParameterDescriptor> getParameterDescriptors() {
        return List.of(
                StrategyParameterDescriptor.builder()
                        .key("taskNumber")
                        .displayName("Номер задания")
                        .description("Номер задания для фильтрации данных")
                        .type(ParameterType.STRING)
                        .required(true)
                        .build()
        );
    }

    @Override
    public Set<String> getRequiredParameters() {
        return getParameterDescriptors().stream()
                .filter(StrategyParameterDescriptor::isRequired)
                .map(StrategyParameterDescriptor::getKey)
                .collect(Collectors.toSet());
    }

    /**
     * Получает номер задания из конфигурации
     */
    private String getTaskNumber(ExportConfig exportConfig) {
        validateParameters(exportConfig.getParams());
        return exportConfig.getParam("taskNumber");
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
}