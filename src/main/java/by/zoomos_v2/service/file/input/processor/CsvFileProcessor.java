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
    public boolean supports(FileMetadata fileMetadata) {
        return FileType.CSV.equals(fileMetadata.getFileType());
    }

//    @Override
//    public Map<String, Object> processFile(Path filePath, FileMetadata metadata,
//                                           ProcessingProgressCallback progressCallback) {
//        log.debug("Начало обработки CSV файла: {}", metadata.getOriginalFilename());
//
//        Map<String, Object> results = new HashMap<>();
//        List<Map<String, String>> records = new ArrayList<>();
//        List<String> validationErrors = new ArrayList<>();
//
//        // Получаем кодировку и разделитель из metadata
//        String encoding = metadata.getEncoding() != null ? metadata.getEncoding() : "UTF-8";
//        char delimiter = metadata.getDelimiter() != null && !metadata.getDelimiter().isEmpty()
//                ? metadata.getDelimiter().charAt(0)
//                : ';';
//
//        try (BufferedReader reader = Files.newBufferedReader(filePath, Charset.forName(encoding));
//             CSVParser csvParser = CSVFormat.DEFAULT
//                     .withDelimiter(delimiter)
//                     .withFirstRecordAsHeader()
//                     .withIgnoreHeaderCase()
//                     .withTrim()
//                     .withIgnoreEmptyLines()
//                     .parse(reader)) {
//
//            List<String> headers = csvParser.getHeaderNames();
//            results.put("headers", headers);
//            int expectedColumnCount = headers.size();
//
//            List<CSVRecord> allRecords = csvParser.getRecords();
//            int totalCount = allRecords.size();
//
//            // Обрабатываем каждую запись
//            for (int i = 0; i < allRecords.size(); i++) {
//                CSVRecord record = allRecords.get(i);
//                Map<String, String> recordMap = new HashMap<>();
//
//                // Проверка количества столбцов
//                if (record.size() != expectedColumnCount) {
//                    String error = String.format("Строка %d: ожидалось %d столбцов, найдено %d",
//                            record.getRecordNumber(), expectedColumnCount, record.size());
//                    validationErrors.add(error);
//                    log.warn(error);
//                }
//
//                // Сохраняем все значения по заголовкам
//                for (String header : headers) {
//                    try {
//                        String value = record.get(header);
//                        recordMap.put(header, value != null ? value.trim() : "");
//                    } catch (IllegalArgumentException e) {
//                        // Если значения для заголовка нет, ставим пустую строку
//                        recordMap.put(header, "");
//                    }
//                }
//
//                // Если в строке больше значений чем заголовков,
//                // сохраняем дополнительные значения с автоматически сгенерированными заголовками
//                for (int j = headers.size(); j < record.size(); j++) {
//                    String extraHeader = "Column_" + (j + 1);
//                    recordMap.put(extraHeader, record.get(j).trim());
//                    if (!headers.contains(extraHeader)) {
//                        headers.add(extraHeader);
//                    }
//                }
//
//                records.add(recordMap);
//
//                // Обновляем прогресс
//                int progress = (int) ((i + 1.0) / totalCount * 100);
//                progressCallback.updateProgress(progress,
//                        String.format("Обработано записей: %d из %d", i + 1, totalCount));
//            }
//
//
//            finalizeResults(results, totalCount, records, headers, validationErrors);
//            logResults(metadata.getOriginalFilename(), records.size(), validationErrors.size());
//            return results;
//
//        } catch (Exception e) {
//            log.error("Ошибка при обработке CSV файла: {}", e.getMessage(), e);
//            throw new FileProcessingException("Ошибка при обработке CSV файла: " + e.getMessage(), e);
//        }
//    }

    public Map<String, Object> processFile(Path filePath, FileMetadata metadata,
                                           ProcessingProgressCallback progressCallback) {
        log.debug("Начало обработки CSV файла: {}", metadata.getOriginalFilename());
        List<ValidationError> validationErrors = new ArrayList<>();

        // Базовая проверка BOM
        try {
            checkBOM(filePath, validationErrors);
        } catch (IOException e) {
            throw new ValidationException("Ошибка проверки BOM", e);
        }

        Map<String, Object> results = new HashMap<>();
        List<Map<String, String>> records = new ArrayList<>();

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
                     .withQuote('"')
                     .withEscape('\\')
                     .parse(reader)) {

            List<String> headers = validateHeaders(csvParser, validationErrors);
            results.put("headers", headers);

            List<CSVRecord> allRecords = csvParser.getRecords();
            processRecords(allRecords, headers, records, validationErrors, progressCallback);

//            if (!validationErrors.isEmpty()) {
//                throw new ValidationException("Обнаружены ошибки в CSV файле", validationErrors);
//            }

            finalizeResults(results, allRecords.size(), records, headers, validationErrors);
            logResults(metadata.getOriginalFilename(), records.size(), validationErrors.size());
            return results;

        } catch (IOException e) {
            throw new ValidationException("Ошибка при обработке CSV файла", e);
        }
    }

    private void checkBOM(Path filePath, List<ValidationError> errors) throws IOException {
        byte[] bytes = Files.readAllBytes(filePath);
        if (bytes.length >= 3 && bytes[0] == (byte)0xEF && bytes[1] == (byte)0xBB && bytes[2] == (byte)0xBF) {
            errors.add(new ValidationError(
                    "BOM_DETECTED",
                    "Обнаружен BOM-маркер в начале файла",
                    0,
                    null
            ));
        }
    }

    private List<String> validateHeaders(CSVParser parser, List<ValidationError> errors) {
        List<String> headers = parser.getHeaderNames();
        if (headers.isEmpty()) {
            errors.add(new ValidationError(
                    "NO_HEADERS",
                    "Отсутствуют заголовки в файле",
                    0,
                    null
            ));
        }
        return headers;
    }

    private void processRecords(List<CSVRecord> records, List<String> headers,
                                List<Map<String, String>> processedRecords,
                                List<ValidationError> errors,
                                ProcessingProgressCallback progressCallback) {

        int totalCount = records.size();
        Set<Character> foundDelimiters = new HashSet<>();

        for (int i = 0; i < records.size(); i++) {
            CSVRecord record = records.get(i);
            Map<String, String> recordMap = new HashMap<>();

            validateRecord(record, headers, i + 1, errors, foundDelimiters);

            for (String header : headers) {
                try {
                    String value = record.get(header);
                    recordMap.put(header, validateField(value, i + 1, header, errors));
                } catch (IllegalArgumentException e) {
                    recordMap.put(header, "");
                }
            }

            processedRecords.add(recordMap);
            progressCallback.updateProgress((int) ((i + 1.0) / totalCount * 100),
                    String.format("Обработано записей: %d из %d", i + 1, totalCount));
        }

        if (foundDelimiters.size() > 1) {
            errors.add(new ValidationError(
                    "MIXED_DELIMITERS",
                    "Обнаружены смешанные разделители в файле",
                    null,
                    null
            ));
        }
    }

    private void validateRecord(CSVRecord record, List<String> headers, int rowNum,
                                List<ValidationError> errors, Set<Character> foundDelimiters) {
        if (record.size() != headers.size()) {
            errors.add(new ValidationError(
                    "COLUMN_COUNT_MISMATCH",
                    String.format("Ожидалось %d столбцов, найдено %d", headers.size(), record.size()),
                    rowNum,
                    null
            ));
        }

        // Проверка разделителей в данных
        for (String value : record) {
            for (char c : value.toCharArray()) {
                if (c == ',' || c == ';' || c == '\t') {
                    foundDelimiters.add(c);
                }
            }
        }
    }

    private String validateField(String value, int rowNum, String header, List<ValidationError> errors) {
        if (value == null) return "";

        value = value.trim();

        // Проверка некорректных кавычек
        if (value.chars().filter(ch -> ch == '"').count() % 2 != 0) {
            errors.add(new ValidationError(
                    "INVALID_QUOTES",
                    "Некорректные кавычки в поле",
                    rowNum,
                    header
            ));
        }

        // Проверка переносов строк
        if (value.contains("\n") || value.contains("\r")) {
            errors.add(new ValidationError(
                    "NEWLINES_IN_FIELD",
                    "Обнаружены переносы строк внутри поля",
                    rowNum,
                    header
            ));
        }

        // Проверка спецсимволов
        if (value.matches(".*[\\x00-\\x1F\\x7F].*")) {
            errors.add(new ValidationError(
                    "SPECIAL_CHARS",
                    "Обнаружены специальные символы в поле",
                    rowNum,
                    header
            ));
        }

        return value;
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