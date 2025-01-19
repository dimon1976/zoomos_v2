package by.zoomos_v2.service.export;
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
import java.util.ArrayList;
import java.util.Comparator;
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
    private static final String DEFAULT_DELIMITER = ";";

    @Override
    protected void doExport(List<Map<String, Object>> data,
                            OutputStream outputStream,
                            ExportConfig exportConfig) throws Exception {

        // Получаем отсортированный список активных полей для экспорта
        List<ExportField> exportFields = exportConfig.getFields().stream()
                .filter(ExportField::isEnabled)
                .sorted(Comparator.comparingInt(ExportField::getPosition))
                .collect(Collectors.toList());

        // Создаем заголовки для CSV, используя displayName или sourceField если displayName не задан
        String[] headers = exportFields.stream()
                .map(field -> field.getDisplayName() != null ?
                        field.getDisplayName() : field.getSourceField())
                .toArray(String[]::new);

        // Настраиваем формат CSV
        CSVFormat csvFormat = CSVFormat.DEFAULT
                .builder()
                .setDelimiter(getDelimiter(exportConfig))
                .setHeader(headers)
                .build();

        // Создаем CSV принтер с указанной кодировкой
        try (OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
             CSVPrinter csvPrinter = new CSVPrinter(writer, csvFormat)) {

            // Экспортируем данные
            for (Map<String, Object> row : data) {
                List<Object> rowData = new ArrayList<>();
                for (ExportField field : exportFields) {
                    Object value = row.get(field.getSourceField());
                    rowData.add(formatValue(value, field));
                }
                csvPrinter.printRecord(rowData);
            }

            csvPrinter.flush();
        }
    }

    @Override
    public String getFileType() {
        return FILE_TYPE;
    }

    /**
     * Получает разделитель из конфигурации или использует значение по умолчанию
     */
    private String getDelimiter(ExportConfig exportConfig) {
        String delimiter = exportConfig.getParam(ExportParams.DELIMITER);
        return delimiter != null ? delimiter : DEFAULT_DELIMITER;
    }

    /**
     * Форматирует значение в соответствии с настройками поля
     */
    private Object formatValue(Object value, ExportField field) {
        if (value == null) {
            return "";
        }

        // Здесь можно добавить дополнительное форматирование
        // в зависимости от типа поля или других параметров
        return value.toString();
    }

}
