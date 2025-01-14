package by.zoomos_v2.service.processor;

import by.zoomos_v2.exception.FileProcessingException;
import by.zoomos_v2.model.FileMetadata;
import by.zoomos_v2.model.FileType;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.*;

/**
 * Процессор для обработки Excel файлов
 */
@Slf4j
@Component
public class ExcelFileProcessor implements FileProcessor {

    @Override
    public boolean supports(FileMetadata fileMetadata) {
        return FileType.EXCEL.equals(fileMetadata.getFileType()) ||
                FileType.XLS.equals(fileMetadata.getFileType());
    }

    @Override
    public Map<String, Object> processFile(Path filePath, FileMetadata metadata,
                                           ProcessingProgressCallback progressCallback) {
        log.debug("Начало обработки Excel файла: {}", metadata.getOriginalFilename());

        Map<String, Object> results = new HashMap<>();
        List<Map<String, String>> records = new ArrayList<>();
        List<String> headers = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(filePath.toFile())) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);

            // Получаем заголовки
            if (headerRow == null) {
                throw new FileProcessingException("Файл не содержит заголовков");
            }

            for (Cell cell : headerRow) {
                headers.add(getCellValueAsString(cell));
            }
            results.put("headers", headers);

            // Подсчитываем общее количество строк
            int totalRows = sheet.getLastRowNum();

            // Обрабатываем каждую строку
            for (int i = 1; i <= totalRows; i++) {
                Row row = sheet.getRow(i);
                if (row != null) {
                    Map<String, String> record = new HashMap<>();
                    for (int j = 0; j < headers.size(); j++) {
                        Cell cell = row.getCell(j);
                        record.put(headers.get(j), getCellValueAsString(cell));
                    }
                    records.add(record);
                }

                // Обновляем прогресс
                int progress = (int) ((i * 100.0) / totalRows);
                progressCallback.updateProgress(progress,
                        String.format("Обработано строк: %d из %d", i, totalRows));
            }

            results.put("records", records);
            results.put("totalRecords", records.size());

            log.info("Excel файл успешно обработан: {}", metadata.getOriginalFilename());
            return results;

        } catch (Exception e) {
            log.error("Ошибка при обработке Excel файла: {}", e.getMessage(), e);
            throw new FileProcessingException("Ошибка при обработке Excel файла", e);
        }
    }

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
                try {
                    return String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    return cell.getStringCellValue();
                }
            default:
                return "";
        }
    }
}