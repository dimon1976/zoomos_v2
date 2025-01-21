package by.zoomos_v2.service.file.export.exporter;
import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.model.ExportField;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * Реализация экспортера для XLSX формата
 */
@Slf4j
@Component
public class XLSXDataExporter extends AbstractDataExporter{
    private static final String FILE_TYPE = "XLSX";

    @Override
    public String getFileType() {
        return FILE_TYPE;
    }

    @Override
    protected void doExport(List<Map<String, Object>> data,
                            OutputStream outputStream,
                            ExportConfig exportConfig) throws Exception {
        log.debug("Начало экспорта данных в XLSX формат");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Export");

            // Создаем стили
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);

            // Создаем заголовки
            Row headerRow = sheet.createRow(0);
            List<ExportField> fields = exportConfig.getFields();

            for (int i = 0; i < fields.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(fields.get(i).getDisplayName());
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 256 * 20); // 20 символов
            }

            // Заполняем данными
            for (int rowNum = 0; rowNum < data.size(); rowNum++) {
                Row row = sheet.createRow(rowNum + 1);
                Map<String, Object> record = data.get(rowNum);

                for (int colNum = 0; colNum < fields.size(); colNum++) {
                    Cell cell = row.createCell(colNum);
                    setCellValue(cell, record.get(fields.get(colNum).getSourceField()));
                    cell.setCellStyle(dataStyle);
                }
            }

            // Автоматически регулируем ширину колонок
            for (int i = 0; i < fields.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
            log.info("Экспорт в XLSX успешно завершен");
        } catch (Exception e) {
            log.error("Ошибка при экспорте в XLSX: {}", e.getMessage());
            throw e;
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        return style;
    }

    private void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
            return;
        }

        if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else {
            cell.setCellValue(value.toString());
        }
    }
}
