package by.zoomos_v2.service.file.export.strategy.av;

import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.model.entity.ReferenceData;
import by.zoomos_v2.repository.ProductRepository;
import by.zoomos_v2.repository.ReferenceDataRepository;
import by.zoomos_v2.service.file.BatchProcessingData;
import by.zoomos_v2.service.file.export.strategy.DataProcessingStrategy;
import by.zoomos_v2.service.file.export.strategy.ProcessingStrategyType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
@Slf4j
@Component
public class TaskBasedFilterStrategy implements DataProcessingStrategy {
    private final ReferenceDataRepository referenceDataRepository;
    private final ProductRepository productRepository;

    public TaskBasedFilterStrategy(ReferenceDataRepository referenceDataRepository, ProductRepository productRepository) {
        this.referenceDataRepository = referenceDataRepository;
        this.productRepository = productRepository;
    }

    @Override
    public List<Map<String, Object>> processData(List<Map<String, Object>> data,
                                                 ExportConfig exportConfig,
                                                 BatchProcessingData batchProcessingData) {
        log.debug("Начало обработки данных на основе задания");

        // Получаем номер задачи из конфигурации
        String taskNumber = getTaskNumber(exportConfig);

        // Получаем актуальные справочные данные
        ReferenceData referenceData = getReferenceData(exportConfig.getClient().getId(), taskNumber);

        // Получаем список товаров из задания
        List<String> taskProducts = getTaskProducts(taskNumber);

        List<Map<String, Object>> filteredData = new ArrayList<>();

        for (Map<String, Object> record : data) {
            String productId = (String) record.get("product.id");
            String recordTaskNumber = (String) record.get("taskNumber");

            // Проверяем соответствие номера задачи и наличие товара в задании
            if (taskNumber.equals(recordTaskNumber) && taskProducts.contains(productId)) {
                // Обогащаем запись справочными данными
                enrichWithReferenceData(record, referenceData);
                filteredData.add(record);
//                batchProcessingData.incrementSuccessCount();
            }
        }

//        log.info("Обработка данных завершена. Отфильтровано записей: {}", batchProcessingData.getSuccessCount());
        return filteredData;
    }

    private String getTaskNumber(ExportConfig exportConfig) {
//        Map<String, Object> parameters = getStrategyParameters(exportConfig);
        Map<String, Object> parameters = null;
        return (String) parameters.getOrDefault("taskNumber", "");
    }

    private ReferenceData getReferenceData(Long clientId, String taskNumber) {
        return referenceDataRepository.findByClientIdAndTaskNumberAndIsActiveTrue(clientId, taskNumber)
                .orElseThrow(() -> new IllegalStateException("Справочные данные не найдены"));
    }

    private List<String> getTaskProducts(String taskNumber) {
        return productRepository.findProductIdsByTaskNumber(taskNumber);
    }

    private void enrichWithReferenceData(Map<String, Object> record, ReferenceData referenceData) {
        // Здесь логика обогащения записи справочными данными
//        Map<String, Object> referenceDataMap = parseReferenceData(referenceData.getReferenceDataJson());
        Map<String, Object> referenceDataMap = null;
        // Добавляем справочные данные к записи
        record.putAll(referenceDataMap);
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
    public void validateParameters(Map<String, Object> parameters) {
        if (parameters == null || !parameters.containsKey("taskNumber")) {
            throw new IllegalArgumentException("Parameter 'taskNumber' is required");
        }
    }
}
