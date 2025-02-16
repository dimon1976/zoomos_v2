package by.zoomos_v2.service.file.export.strategy.av;

import com.fasterxml.jackson.core.type.TypeReference;
import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.model.entity.Product;
import by.zoomos_v2.model.entity.ReferenceData;
import by.zoomos_v2.model.enums.DataSourceType;
import by.zoomos_v2.repository.ClientProcessingStrategyRepository;
import by.zoomos_v2.repository.ProductRepository;
import by.zoomos_v2.repository.ReferenceDataRepository;
import by.zoomos_v2.service.file.BatchProcessingData;
import by.zoomos_v2.service.file.export.strategy.DataProcessingStrategy;
import by.zoomos_v2.service.file.export.strategy.ProcessingStrategyType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class TaskBasedFilterStrategy implements DataProcessingStrategy {
    private final ClientProcessingStrategyRepository strategyRepository;
    private final ReferenceDataRepository referenceDataRepository;
    private final ProductRepository productRepository;
    private final ObjectMapper objectMapper;

    public TaskBasedFilterStrategy(
            ClientProcessingStrategyRepository strategyRepository,
            ReferenceDataRepository referenceDataRepository,
            ProductRepository productRepository,
            ObjectMapper objectMapper) {
        this.strategyRepository = strategyRepository;
        this.referenceDataRepository = referenceDataRepository;
        this.productRepository = productRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Map<String, Object>> processData(List<Map<String, Object>> data,
                                                 ExportConfig exportConfig,
                                                 BatchProcessingData batchProcessingData) {
        log.debug("Начало обработки данных на основе задания");

        // Получаем номер задачи из конфигурации
        String taskNumber = getTaskNumberFromConfig(exportConfig);

        // Получаем продукты из задания для сравнения
        List<Product> taskProducts = productRepository.findByProductAdditional1AndDataSource(
                taskNumber, DataSourceType.TASK);

        // Получаем справочные данные
        ReferenceData referenceData = referenceDataRepository
                .findByClientIdAndTaskNumberAndIsActiveTrue(
                        exportConfig.getClient().getId(), taskNumber)
                .orElseThrow(() -> new IllegalStateException("Справочные данные не найдены"));

        List<Map<String, Object>> filteredData = new ArrayList<>();

        for (Map<String, Object> record : data) {
            String productId = (String) record.get("product.productId");
            String category = (String) record.get("product.productCategory1");
            String retailCode = (String) record.get("competitordata.competitorAdditional");

            // Проверяем наличие товара в задании с соответствующей категорией и сетью
            boolean isValid = taskProducts.stream()
                    .anyMatch(tp -> tp.getProductId().equals(productId)
                            && tp.getProductCategory1().equals(category)
                            && tp.getCompetitorAdditional().equals(retailCode));

            if (isValid) {
                // Обогащаем данные из справочника
                enrichWithReferenceData(record, referenceData);
                filteredData.add(record);
                batchProcessingData.incrementSuccessCount();
                log.debug("Обработана запись для товара: {}", productId);
            }
        }

        log.info("Обработка данных завершена. Отфильтровано записей: {}", batchProcessingData.getSuccessCount());
        return filteredData;
    }

    private String getTaskNumberFromConfig(ExportConfig exportConfig) {
        try {
            return strategyRepository
                    .findByClientIdAndStrategyTypeAndIsActiveTrue(
                            exportConfig.getClient().getId(),
                            getStrategyType())
                    .map(strategy -> {
                        try {
                            Map<String, Object> parameters = objectMapper.readValue(
                                    strategy.getParameters(),
                                    new TypeReference<Map<String, Object>>() {
                                    });
                            return (String) parameters.get("taskNumber");
                        } catch (Exception e) {
                            log.error("Ошибка при чтении параметров стратегии: {}", e.getMessage());
                            throw new IllegalStateException("Не удалось получить номер задания", e);
                        }
                    })
                    .orElseThrow(() -> new IllegalStateException("Не найдены параметры стратегии"));
        } catch (Exception e) {
            log.error("Ошибка при получении конфигурации стратегии: {}", e.getMessage());
            throw new IllegalStateException("Ошибка получения номера задания", e);
        }
    }

    private void enrichWithReferenceData(Map<String, Object> record, ReferenceData referenceData) {
        String retailCode = (String) record.get("competitordata.competitorAdditional");

        if (retailCode != null && retailCode.equals(referenceData.getRetailCode())) {
            if (isEmpty(record.get("competitordata.competitorName"))) {
                record.put("competitordata.competitorName", referenceData.getRetailName());
            }
            if (isEmpty(record.get("regiondata.regionAddress"))) {
                record.put("regiondata.regionAddress", referenceData.getPhysicalAddress());
            }
            if (isEmpty(record.get("regiondata.region"))) {
                record.put("regiondata.region", referenceData.getRegionName());
            }
        }
    }

    private boolean isEmpty(Object value) {
        return value == null || value.toString().trim().isEmpty();
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
        String taskNumber = (String) parameters.get("taskNumber");
        if (taskNumber == null || taskNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Parameter 'taskNumber' cannot be empty");
        }
    }
}
