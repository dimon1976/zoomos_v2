package by.zoomos_v2.service.file.export.strategy;

import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.service.file.BatchProcessingData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DefaultProcessingStrategy implements DataProcessingStrategy {
    @Override
    public List<Map<String, Object>> processData(List<Map<String, Object>> data,
                                                 ExportConfig exportConfig,
                                                 BatchProcessingData batchProcessingData) {
        log.debug("Применяется стандартная стратегия обработки данных");
        return data;
    }

    @Override
    public boolean supports(ExportConfig exportConfig) {
        return exportConfig.getStrategyType() == null ||
                ProcessingStrategyType.DEFAULT.equals(exportConfig.getStrategyType());
    }

    @Override
    public ProcessingStrategyType getStrategyType() {
        return ProcessingStrategyType.DEFAULT;
    }
}
