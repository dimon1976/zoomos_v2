package by.zoomos_v2.service.processor;

import by.zoomos_v2.exception.FileProcessingException;
import by.zoomos_v2.model.FileMetadata;
import by.zoomos_v2.model.FileType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Процессор для обработки CSV файлов
 */
@Slf4j
@Component
public class CsvFileProcessor implements FileProcessor {

    @Override
    public boolean supports(FileMetadata fileMetadata) {
        return FileType.CSV.equals(fileMetadata.getFileType());
    }

    @Override
    public Map<String, Object> processFile(Path filePath, FileMetadata metadata,
                                           ProcessingProgressCallback progressCallback) {
        log.debug("Начало обработки CSV файла: {}", metadata.getOriginalFilename());

        Map<String, Object> results = new HashMap<>();
        List<Map<String, String>> records = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(filePath);
             CSVParser csvParser = CSVFormat.DEFAULT
                     .withFirstRecordAsHeader()
                     .withIgnoreHeaderCase()
                     .withTrim()
                     .parse(reader)) {

            List<String> headers = csvParser.getHeaderNames();
            results.put("headers", headers);

            // Подсчитываем общее количество записей
            List<CSVRecord> allRecords = csvParser.getRecords();
            int totalRecords = allRecords.size();

            // Обрабатываем каждую запись
            for (int i = 0; i < allRecords.size(); i++) {
                CSVRecord record = allRecords.get(i);
                Map<String, String> recordMap = new HashMap<>();

                for (String header : headers) {
                    recordMap.put(header, record.get(header));
                }
                records.add(recordMap);

                // Обновляем прогресс
                int progress = (int) ((i + 1.0) / totalRecords * 100);
                progressCallback.updateProgress(progress,
                        String.format("Обработано записей: %d из %d", i + 1, totalRecords));
            }

            results.put("records", records);
            results.put("totalRecords", records.size());

            log.info("CSV файл успешно обработан: {}", metadata.getOriginalFilename());
            return results;

        } catch (Exception e) {
            log.error("Ошибка при обработке CSV файла: {}", e.getMessage(), e);
            throw new FileProcessingException("Ошибка при обработке CSV файла", e);
        }
    }
}