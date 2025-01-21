package by.zoomos_v2.service.file.export.exporter;
import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.model.ExportField;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Реализация экспортера для CSV формата
 */
@Slf4j
@Component
public class CSVDataExporter extends AbstractDataExporter{
    private static final String FILE_TYPE = "CSV";
    private static final String DEFAULT_DELIMITER = ",";

    @Override
    public String getFileType() {
        return FILE_TYPE;
    }

    @Override
    protected void doExport(List<Map<String, Object>> data,
                            OutputStream outputStream,
                            ExportConfig exportConfig) throws Exception {
        log.debug("Начало экспорта данных в CSV формат");

        // Получаем настройки из конфигурации
        String delimiter = exportConfig.getParam("delimiter");
        if (delimiter == null || delimiter.isEmpty()) {
            delimiter = DEFAULT_DELIMITER;
        }

        // Создаем формат CSV
        CSVFormat csvFormat = CSVFormat.DEFAULT
                .withDelimiter(delimiter.charAt(0))
                .withHeader(getHeadersArray(exportConfig));

        try (OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
             CSVPrinter csvPrinter = new CSVPrinter(writer, csvFormat)) {

            // Экспортируем каждую запись
            for (Map<String, Object> record : data) {
                List<String> values = exportConfig.getFields().stream()
                        .map(field -> formatValue(record.get(field.getSourceField())))
                        .collect(Collectors.toList());
                csvPrinter.printRecord(values);
            }

            csvPrinter.flush();
            log.info("Экспорт в CSV успешно завершен");
        } catch (IOException e) {
            log.error("Ошибка при экспорте в CSV: {}", e.getMessage());
            throw e;
        }
    }

    private String[] getHeadersArray(ExportConfig exportConfig) {
        return exportConfig.getFields().stream()
                .map(ExportField::getDisplayName)
                .toArray(String[]::new);
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "";
        }
        return value.toString().trim();
    }

}
