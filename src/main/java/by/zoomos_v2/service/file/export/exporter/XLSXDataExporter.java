package by.zoomos_v2.service.file.export.exporter;

import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.model.ExportField;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.rmi.server.ExportException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Реализация экспортера для XLSX формата
 */
@Slf4j
@Component
public class XLSXDataExporter extends AbstractDataExporter {
    //    private static final String FILE_TYPE = "XLSX";
//
//    @Override
//    public String getFileType() {
//        return FILE_TYPE;
//    }
//
//    @Override
//    protected void doExport(List<Map<String, Object>> data,
//                            OutputStream outputStream,
//                            ExportConfig exportConfig) throws Exception {
//        log.debug("Начало экспорта данных в XLSX формат");
//
//        try (Workbook workbook = new XSSFWorkbook()) {
//            Sheet sheet = workbook.createSheet("Export");
//
//            // Создаем стили
//            CellStyle headerStyle = createHeaderStyle(workbook);
//            CellStyle dataStyle = createDataStyle(workbook);
//
//            // Фильтруем и сортируем поля
//            List<ExportField> enabledFields = exportConfig.getFields().stream()
//                    .filter(ExportField::isEnabled)
//                    .sorted(Comparator.comparingInt(ExportField::getPosition))
//                    .collect(Collectors.toList());
//
//            // Создаем заголовки
//            Row headerRow = sheet.createRow(0);
//            for (int i = 0; i < enabledFields.size(); i++) {
//                Cell cell = headerRow.createCell(i);
//                cell.setCellValue(enabledFields.get(i).getDisplayName());
//                cell.setCellStyle(headerStyle);
//                sheet.setColumnWidth(i, 256 * 20); // 20 символов
//            }
//
//            // Заполняем данными
//            for (int rowNum = 0; rowNum < data.size(); rowNum++) {
//                Row row = sheet.createRow(rowNum + 1);
//                Map<String, Object> record = data.get(rowNum);
//
//                for (int colNum = 0; colNum < enabledFields.size(); colNum++) {
//                    Cell cell = row.createCell(colNum);
//                    setCellValue(cell, record.get(enabledFields.get(colNum).getSourceField()));
//                    cell.setCellStyle(dataStyle);
//                }
//            }
//            // Добавляем автофильтр
//            if (!data.isEmpty()) {
//                sheet.setAutoFilter(new CellRangeAddress(
//                        0, // первая строка (заголовки)
//                        data.size(), // последняя строка с данными
//                        0, // первая колонка
//                        enabledFields.size() - 1 // последняя колонка
//                ));
//            }
//            // Автоматически регулируем ширину колонок
//            for (int i = 0; i < enabledFields.size(); i++) {
//                sheet.autoSizeColumn(i);
//            }
//
//            workbook.write(outputStream);
//            log.info("Экспорт в XLSX успешно завершен");
//        } catch (Exception e) {
//            log.error("Ошибка при экспорте в XLSX: {}", e.getMessage());
//            throw e;
//        }
//    }
//
//    private CellStyle createHeaderStyle(Workbook workbook) {
//        CellStyle style = workbook.createCellStyle();
//
//        // Цвет фона
//        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
//        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
//
//        // Границы
//        style.setBorderBottom(BorderStyle.THIN);
//        style.setBorderTop(BorderStyle.THIN);
//        style.setBorderRight(BorderStyle.THIN);
//        style.setBorderLeft(BorderStyle.THIN);
//
//        // Шрифт
//        Font font = workbook.createFont();
//        font.setBold(true);
//        font.setFontName("Arial");
//        font.setFontHeightInPoints((short) 10);
//        style.setFont(font);
//
//        // Выравнивание
//        style.setAlignment(HorizontalAlignment.CENTER);
//        style.setVerticalAlignment(VerticalAlignment.CENTER);
//
//        // Перенос текста
//        style.setWrapText(true);
//
//        // Защита ячеек
//        style.setLocked(true);
//
//        return style;
//    }
//
//    private CellStyle createDataStyle(Workbook workbook) {
//        CellStyle style = workbook.createCellStyle();
//        style.setBorderBottom(BorderStyle.THIN);
//        style.setBorderTop(BorderStyle.THIN);
//        style.setBorderRight(BorderStyle.THIN);
//        style.setBorderLeft(BorderStyle.THIN);
//        return style;
//    }
//
//    private void setCellValue(Cell cell, Object value) {
//        if (value == null) {
//            cell.setBlank();
//            return;
//        }
//
//        if (value instanceof Number) {
//            cell.setCellValue(((Number) value).doubleValue());
//        } else if (value instanceof Boolean) {
//            cell.setCellValue((Boolean) value);
//        } else {
//            cell.setCellValue(value.toString());
//        }
//    }
    private static final String FILE_TYPE = "XLSX";
    private static final int BATCH_SIZE = 1000;
    private static final int DEFAULT_COLUMN_WIDTH = 256 * 20;
    private static final int LARGE_FILE_THRESHOLD = 10000;
    private static final Map<String, CellStyle> styleCache = new ConcurrentHashMap<>();

    /**
     * Экспортирует данные в XLSX формат с оптимизацией памяти
     *
     * @throws ExportException при критических ошибках
     */
    @Override
    protected void doExport(List<Map<String, Object>> data,
                            OutputStream outputStream,
                            ExportConfig exportConfig) throws ExportException {
        log.info("Начало экспорта данных. Записей: {}", data.size());
        Workbook workbook = null;

        try {
            workbook = createOptimizedWorkbook(data.size());
            Sheet sheet = workbook.createSheet("Export");
            List<ExportField> enabledFields = getEnabledFields(exportConfig);

            exportDataInBatches(sheet, data, enabledFields);

            workbook.write(outputStream);
            outputStream.flush();
            log.info("Экспорт успешно завершен");

        } catch (Exception e) {
            log.error("Критическая ошибка экспорта: {}", e.getMessage(), e);
            throw new ExportException("Ошибка экспорта", e);
        } finally {
            closeResources(workbook, data);
        }
    }

    private List<ExportField> getEnabledFields(ExportConfig exportConfig) {
        return exportConfig.getFields().stream()
                .filter(ExportField::isEnabled)
                .sorted(Comparator.comparingInt(ExportField::getPosition))
                .collect(Collectors.toList());
    }

    private Workbook createOptimizedWorkbook(int rowCount) {
        log.debug("Создание {} для {} строк",
                rowCount > LARGE_FILE_THRESHOLD ? "SXSSFWorkbook" : "XSSFWorkbook",
                rowCount);

        if (rowCount > LARGE_FILE_THRESHOLD) {
            SXSSFWorkbook workbook = new SXSSFWorkbook(100);
            workbook.setCompressTempFiles(true);
            return workbook;
        }
        return new XSSFWorkbook();
    }

    private void exportDataInBatches(Sheet sheet,
                                     List<Map<String, Object>> data,
                                     List<ExportField> fields) throws ExportException {
        int processedRows = 0;
        CellStyle headerStyle = getOrCreateStyle("header", sheet.getWorkbook());
        CellStyle dataStyle = getOrCreateStyle("data", sheet.getWorkbook());

        createHeaderRow(sheet, fields, headerStyle);

        for (List<Map<String, Object>> batch : getBatches(data)) {
            try {
                processBatch(sheet, batch, fields, dataStyle, processedRows + 1);
                processedRows += batch.size();

                if (processedRows % (BATCH_SIZE * 5) == 0) {
                    log.info("Прогресс: {} из {} строк", processedRows, data.size());
                }
            } catch (Exception e) {
                log.error("Ошибка обработки батча на строке {}: {}",
                        processedRows, e.getMessage());
            }
        }

        applyFormatting(sheet, processedRows, fields.size());
    }

    private List<List<Map<String, Object>>> getBatches(List<Map<String, Object>> data) {
        List<List<Map<String, Object>>> batches = new ArrayList<>();
        for (int i = 0; i < data.size(); i += BATCH_SIZE) {
            batches.add(data.subList(i, Math.min(data.size(), i + BATCH_SIZE)));
        }
        return batches;
    }

    private void createHeaderRow(Sheet sheet,
                                 List<ExportField> fields,
                                 CellStyle style) throws ExportException {
        try {
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < fields.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(fields.get(i).getDisplayName());
                cell.setCellStyle(style);
                sheet.setColumnWidth(i, DEFAULT_COLUMN_WIDTH);
            }
        } catch (Exception e) {
            log.error("Ошибка создания заголовков: {}", e.getMessage());
            throw new ExportException("Ошибка заголовков", e);
        }
    }

    private void processBatch(Sheet sheet,
                              List<Map<String, Object>> batch,
                              List<ExportField> fields,
                              CellStyle style,
                              int startRow) {
        log.debug("Обработка строк {}-{}", startRow, startRow + batch.size() - 1);

        for (Map<String, Object> record : batch) {
            try {
                Row row = sheet.createRow(startRow++);
                for (int i = 0; i < fields.size(); i++) {
                    Cell cell = row.createCell(i);
                    setOptimizedCellValue(cell,
                            record.get(fields.get(i).getSourceField()));
                    cell.setCellStyle(style);
                }
            } catch (Exception e) {
                log.warn("Ошибка в строке {}: {}", startRow, e.getMessage());
            }
        }
    }

    private void setOptimizedCellValue(Cell cell, Object value) {
        try {
            if (value == null) {
                cell.setBlank();
            } else if (value instanceof Number) {
                cell.setCellValue(((Number) value).doubleValue());
            } else if (value instanceof Boolean) {
                cell.setCellValue((Boolean) value);
            } else {
                cell.setCellValue(value.toString());
            }
        } catch (Exception e) {
            log.trace("Ошибка установки значения: {}", e.getMessage());
            cell.setBlank();
        }
    }

    private CellStyle getOrCreateStyle(String type, Workbook workbook) {
        return styleCache.computeIfAbsent(type, k ->
                createStyle(workbook, "header".equals(type)));
    }

    private CellStyle createStyle(Workbook workbook, boolean isHeader) {
        CellStyle style = workbook.createCellStyle();
        if (isHeader) {
            style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
        }
        return style;
    }

    private void applyFormatting(Sheet sheet, int rows, int cols) {
        try {
            if (rows > 0) {
                sheet.setAutoFilter(new CellRangeAddress(0, rows, 0, cols - 1));
            }
            if (rows <= LARGE_FILE_THRESHOLD) {
                for (int i = 0; i < cols; i++) {
                    sheet.autoSizeColumn(i);
                }
            }
        } catch (Exception e) {
            log.warn("Ошибка форматирования: {}", e.getMessage());
        }
    }

    private void closeResources(Workbook workbook, List<Map<String, Object>> data) {
        try {
            if (workbook instanceof SXSSFWorkbook) {
                ((SXSSFWorkbook) workbook).dispose();
            }
            if (workbook != null) {
                workbook.close();
            }
            data.clear();
            styleCache.clear();
            System.gc();
        } catch (Exception e) {
            log.warn("Ошибка закрытия ресурсов: {}", e.getMessage());
        }
    }

    @Override
    public String getFileType() {
        return FILE_TYPE;
    }
}
