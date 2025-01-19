package by.zoomos_v2.service.export;
import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.model.ExportField;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Реализация экспортера для XLSX формата
 */
@Slf4j
@Component
public class XLSXDataExporter extends AbstractDataExporter{
    private static final String FILE_TYPE = "XLSX";
    private static final int DEFAULT_COLUMN_WIDTH = 5000;

    @Override
    protected void doExport(List<Map<String, Object>> data,
                            OutputStream outputStream,
                            ExportConfig exportConfig) throws Exception {

        // Получаем отсортированный список активных полей для экспорта
        List<ExportField> exportFields = exportConfig.getFields().stream()
                .filter(ExportField::isEnabled)
                .sorted(Comparator.comparingInt(ExportField::getPosition))
                .collect(Collectors.toList());

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Export");

            // Создаем стили для заголовков
            CellStyle headerStyle = createHeaderStyle(workbook);

            // Создаем заголовки
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < exportFields.size(); i++) {
                Cell cell = headerRow.createCell(i);
                ExportField field = exportFields.get(i);
                cell.setCellValue(field.getDisplayName() != null ?
                        field.getDisplayName() : field.getSourceField());
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, DEFAULT_COLUMN_WIDTH);
            }

            // Заполняем данными
            int rowNum = 1;
            for (Map<String, Object> row : data) {
                Row currentRow = sheet.createRow(rowNum++);
                for (int i = 0; i < exportFields.size(); i++) {
                    Cell cell = currentRow.createCell(i);
                    ExportField field = exportFields.get(i);
                    Object value = row.get(field.getSourceField());
                    setCellValue(cell, value, field);
                }
            }

            // Применяем автофильтр
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                    0, data.size(), 0, exportFields.size() - 1));

            workbook.write(outputStream);
        }
    }

    @Override
    public String getFileType() {
        return FILE_TYPE;
    }

    /**
     * Создает стиль для заголовков
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);

        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        return headerStyle;
    }

    /**
     * Устанавливает значение ячейки в зависимости от типа данных
     */
    private void setCellValue(Cell cell, Object value, ExportField field) {
        if (value == null) {
            cell.setCellValue("");
            return;
        }

        // В зависимости от типа данных используем соответствующий метод
        if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else {
            cell.setCellValue(value.toString());
        }
    }
}
