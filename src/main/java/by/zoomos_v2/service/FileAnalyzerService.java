package by.zoomos_v2.service;

import by.zoomos_v2.model.FileMetadata;
import by.zoomos_v2.model.FileType;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.*;
import org.mozilla.universalchardet.UniversalDetector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;

// Сервис для определения метаданных файла
@Service
@Slf4j
public class FileAnalyzerService {

    @Value("${file.analysis.max-bytes:4096}")
    private int maxBytesToAnalyze;

    public FileMetadata analyzeFile(MultipartFile file) throws IOException {
        FileMetadata metadata = new FileMetadata();

        // Определяем тип файла
        metadata.setFileType(FileType.fromFileName(file.getOriginalFilename()));

        // Для CSV и TXT определяем кодировку и разделитель
        if (metadata.getFileType() == FileType.CSV || metadata.getFileType() == FileType.TXT) {
            analyzeTextFile(file, metadata);
        } else {
            analyzeExcelFile(file, metadata);
        }

        return metadata;
    }

    private void analyzeTextFile(MultipartFile file, FileMetadata metadata) throws IOException {
        byte[] bytes = IOUtils.toByteArray(file.getInputStream());

        // Определение кодировки
        String encoding = detectEncoding(bytes);
        metadata.setCharset(encoding);

        // Определение разделителя
        char delimiter = detectDelimiter(new String(bytes, encoding));
        metadata.setDelimiter(String.valueOf(delimiter));

        // Настраиваем CSVReader с обнаруженным разделителем
//        CSVParser parser = new CSVParserBuilder()
//                .withSeparator(delimiter)
//                .build();
        CSVParser parser = new CSVParserBuilder()
                .withSeparator(delimiter)
                .withQuoteChar('"')
                .withEscapeChar('\\')
                .withStrictQuotes(false)
                .withIgnoreQuotations(true)  // Игнорировать кавычки полностью
                .build();

        try (CSVReader reader = new CSVReaderBuilder(
                new InputStreamReader(new ByteArrayInputStream(bytes), encoding))
                .withCSVParser(parser)
                .build()) {

            String[] headers = reader.readNext();
            if (headers == null || headers.length == 0) {
                throw new IllegalArgumentException("Файл пуст или не содержит данных.");
            }

            // Очищаем заголовки от кавычек, если они есть
            List<String> cleanHeaders = Arrays.stream(headers)
                    .map(header -> header.replaceAll("^\"|\"$", "").trim())
                    .collect(Collectors.toList());

            metadata.setHeaders(cleanHeaders);

        } catch (CsvValidationException e) {
            throw new RuntimeException("Ошибка при чтении CSV файла: " + e.getMessage(), e);
        }
    }

    // Метод для определения кодировки
    private String detectEncoding(byte[] data){
        UniversalDetector detector = new UniversalDetector(null);
        detector.handleData(data, 0, data.length);
        detector.dataEnd();
        String detectedCharset = detector.getDetectedCharset();

        detector.reset();
        if (detectedCharset != null) {
            log.info("Detected encoding: " + detectedCharset);
            return detectedCharset;
        } else {
            log.warn("Encoding not detected. Using default charset: " + Charset.defaultCharset().name());
            return Charset.defaultCharset().name();
        }
    }


    private void analyzeExcelFile(MultipartFile file, FileMetadata metadata) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);

            if (headerRow == null) {
                throw new IllegalArgumentException("Файл не содержит данных");
            }

            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(getCellValueAsString(cell));
            }
            metadata.setHeaders(headers);
        }
    }

    private char detectDelimiter(String line) {
        char[] commonDelimiters = {',', ';', '\t', '|'};
        Map<Character, Integer> delimiterCounts = new HashMap<>();

        for (char delimiter : commonDelimiters) {
            int count = StringUtils.countMatches(line, String.valueOf(delimiter));
            delimiterCounts.put(delimiter, count);
        }

        return delimiterCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(','); // По умолчанию запятая
    }

    public String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toString();
                }
                yield String.valueOf(cell.getNumericCellValue());
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }
}
