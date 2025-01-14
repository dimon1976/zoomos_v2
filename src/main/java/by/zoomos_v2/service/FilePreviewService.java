package by.zoomos_v2.service;

import by.zoomos_v2.exception.FileProcessingException;
import by.zoomos_v2.model.FileType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Сервис для предварительного просмотра содержимого файлов
 */
@Slf4j
@Service
public class FilePreviewService {

    private static final int MAX_PREVIEW_ROWS = 10;

    /**
     * Получает предварительный просмотр содержимого файла
     */
    public Map<String, Object> getFilePreview(MultipartFile file, FileType fileType) {
        log.debug("Получение предпросмотра для файла: {}", file.getOriginalFilename());

        try {
            switch (fileType) {
                case CSV:
                    return getCSVPreview(file);
                case EXCEL:
                case XLS:
                    return getExcelPreview(file);
                default:
                    throw new FileProcessingException("Неподдерживаемый тип файла");
            }
        } catch (Exception e) {
            log.error("Ошибка при получении предпросмотра файла: {}", e.getMessage(), e);
            throw new FileProcessingException("Ошибка при чтении файла", e);
        }
    }

    /**
     * Получает предварительный просмотр CSV файла
     */
    private Map<String, Object> getCSVPreview(MultipartFile file) throws Exception {
        Map<String, Object> preview = new HashMap<>();
        List<Map<String, String>> rows = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()));
             CSVParser csvParser = CSVFormat.DEFAULT
                     .withFirstRecordAsHeader()
                     .withIgnoreHeaderCase()
                     .withTrim()
                     .parse(reader)) {

            preview.put("headers", csvParser.getHeaderNames());

            Iterator<org.apache.commons.csv.CSVRecord> iterator = csvParser.iterator();
            int rowCount = 0;
            while (iterator.hasNext() && rowCount < MAX_PREVIEW_ROWS) {
                org.apache.commons.csv.CSVRecord record = iterator.next();
                Map<String, String> row = new HashMap<>();
                for (String header : csvParser.getHeaderNames()) {
                    row.put(header, record.get(header));
                }
                rows.add(row);
                rowCount++;
            }
        }

        preview.put("rows", rows);
        preview.put("totalRows", rows.size());
        return preview;
    }

    /**
     * Получает предварительный просмотр Excel файла
     */
    private Map<String, Object> getExcelPreview(MultipartFile file) throws Exception {
        Map<String, Object> preview = new HashMap<>();
        List<Map<String, String>> rows = new ArrayList<>();
        List<String> headers = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);

            // Получаем заголовки
            if (headerRow != null) {
                for (Cell cell : headerRow) {
                    headers.add(getCellValueAsString(cell));
                }
            }

            // Получаем данные
            int rowCount = 0;
            for (int i = 1; i <= sheet.getLastRowNum() && rowCount < MAX_PREVIEW_ROWS; i++) {
                Row row = sheet.getRow(i);
                if (row != null) {
                    Map<String, String> rowData = new HashMap<>();
                    for (int j = 0; j < headers.size(); j++) {
                        Cell cell = row.getCell(j);
                        rowData.put(headers.get(j), getCellValueAsString(cell));
                    }
                    rows.add(rowData);
                    rowCount++;
                }
            }
        }

        preview.put("headers", headers);
        preview.put("rows", rows);
        preview.put("totalRows", rows.size());
        return preview;
    }

    /**
     * Получает значение ячейки Excel как строку
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toString();
                }
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }
}