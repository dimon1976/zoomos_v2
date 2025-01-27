package by.zoomos_v2.service.file.input.processor;

import by.zoomos_v2.exception.FileProcessingException;
import by.zoomos_v2.exception.ValidationError;
import by.zoomos_v2.exception.ValidationException;
import by.zoomos_v2.model.FileMetadata;
import by.zoomos_v2.model.FileType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Процессор для обработки CSV файлов
 */
@Slf4j
@Component
public class CsvFileProcessor implements FileProcessor {


    @Override
    public Map<String, Object> processFile(Path filePath, FileMetadata metadata,
                                           ProcessingProgressCallback progressCallback) {
        log.debug("Начало обработки CSV файла: {}", metadata.getOriginalFilename());

        Map<String, Object> results = new HashMap<>();
        List<Map<String, String>> records = new ArrayList<>();
//        List<String> validationErrors = new ArrayList<>();
        List<ValidationError> validationErrors = new ArrayList<>();

        // Получаем кодировку и разделитель из metadata
        String encoding = metadata.getEncoding() != null ? metadata.getEncoding() : "UTF-8";
        char delimiter = metadata.getDelimiter() != null && !metadata.getDelimiter().isEmpty()
                ? metadata.getDelimiter().charAt(0)
                : ';';

        try (BufferedReader reader = Files.newBufferedReader(filePath, Charset.forName(encoding));
             CSVParser csvParser = CSVFormat.DEFAULT
                     .withDelimiter(delimiter)
                     .withFirstRecordAsHeader()
                     .withIgnoreHeaderCase()
                     .withTrim()
                     .withIgnoreEmptyLines()
                     .parse(reader)) {

            List<String> headers = csvParser.getHeaderNames();
            results.put("headers", headers);
            int expectedColumnCount = headers.size();

            List<CSVRecord> allRecords = csvParser.getRecords();
            int totalCount = allRecords.size();

            // Обрабатываем каждую запись
            for (int i = 0; i < allRecords.size(); i++) {
                CSVRecord record = allRecords.get(i);
                Map<String, String> recordMap = new HashMap<>();

                // Сохраняем все значения по заголовкам
                for (String header : headers) {
                    try {
                        String value = record.get(header);
                        recordMap.put(header, value != null ? value.trim() : "");
                    } catch (IllegalArgumentException e) {
                        // Если значения для заголовка нет, ставим пустую строку
                        recordMap.put(header, "");
                    }
                }

                // Если в строке больше значений чем заголовков,
                // сохраняем дополнительные значения с автоматически сгенерированными заголовками
                for (int j = headers.size(); j < record.size(); j++) {
                    String extraHeader = "Column_" + (j + 1);
                    recordMap.put(extraHeader, record.get(j).trim());
                    if (!headers.contains(extraHeader)) {
                        headers.add(extraHeader);
                    }
                }

                records.add(recordMap);

                // Обновляем прогресс
                int progress = (int) ((i + 1.0) / totalCount * 100);
                progressCallback.updateProgress(progress,
                        String.format("Обработано записей: %d из %d", i + 1, totalCount));
            }


            finalizeResults(results, totalCount, records, headers, validationErrors);
            logResults(metadata.getOriginalFilename(), records.size(), validationErrors.size());
            return results;

        } catch (Exception e) {
            log.error("Ошибка при обработке CSV файла: {}", e.getMessage(), e);
            throw new FileProcessingException("Ошибка при обработке CSV файла: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean supports(FileMetadata fileMetadata) {
        return FileType.CSV.equals(fileMetadata.getFileType());
    }


    private void finalizeResults(Map<String, Object> results,
                                 Integer totalCount,
                                 List<Map<String, String>> records,
                                 List<String> headers,
                                 List<ValidationError> validationErrors) {
        results.put("records", records);
        results.put("totalCount", totalCount);
        results.put("successCount", records.size());
        results.put("headers", headers); // Обновляем заголовки с учетом дополнительных столбцов
        results.put("errorCount", validationErrors.size());
        results.put("validationErrors", validationErrors);

    }

    private void logResults(String filename, int recordCount, int errorCount) {
        if (errorCount > 0) {
            log.warn("CSV файл обработан с ошибками: {}. Записей: {}, Ошибок: {}",
                    filename, recordCount, errorCount);
        } else {
            log.info("CSV файл успешно обработан: {}. Всего записей: {}",
                    filename, recordCount);
        }
    }
}
