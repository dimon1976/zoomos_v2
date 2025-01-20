package by.zoomos_v2.service.processor.client;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class ProcessingResult {
    private boolean success;
    private List<Map<String, String>> processedData;
    private List<String> errors = new ArrayList<>(); // инициализируем пустым списком
    private Map<String, Object> statistics;
}

