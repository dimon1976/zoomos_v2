package by.zoomos_v2.DTO;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class StartExportRequest {
    /**
     * ID конфигурации экспорта
     */
    private Long configId;

    /**
     * ID одного файла для экспорта (используется при одиночном экспорте)
     */
    private Long fileId;

    /**
     * Список ID файлов для экспорта (используется при множественном экспорте)
     */
    private List<Long> fileIds;

    /**
     * Тип файла экспорта (CSV, XLSX)
     */
    private String fileType;

    /**
     * Параметры стратегии обработки данных
     */
    private Map<String, String> strategyParams;
}
