package by.zoomos_v2.service.file.input.processor;

import by.zoomos_v2.exception.FileProcessingException;
import by.zoomos_v2.exception.ValidationError;
import by.zoomos_v2.model.FileMetadata;
import by.zoomos_v2.model.FileType;
import by.zoomos_v2.util.PathResolver;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Процессор для обработки CSV файлов
 */
@Slf4j
@Component
public class CsvFileProcessor implements FileProcessor {


    //    @Override
//    public Map<String, Object> processFile(Path filePath, FileMetadata metadata,
//                                           ProcessingProgressCallback progressCallback) {
//        log.debug("Начало обработки CSV файла: {}", metadata.getOriginalFilename());
//
//        Map<String, Object> results = new HashMap<>();
//        List<Map<String, String>> records = new ArrayList<>();
////        List<String> validationErrors = new ArrayList<>();
//        List<ValidationError> validationErrors = new ArrayList<>();
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
//
//    @Override
//    public boolean supports(FileMetadata fileMetadata) {
//        return FileType.CSV.equals(fileMetadata.getFileType());
//    }
//
//
//    private void finalizeResults(Map<String, Object> results,
//                                 Integer totalCount,
//                                 List<Map<String, String>> records,
//                                 List<String> headers,
//                                 List<ValidationError> validationErrors) {
//        results.put("records", records);
//        results.put("totalCount", totalCount);
//        results.put("successCount", records.size());
//        results.put("headers", headers); // Обновляем заголовки с учетом дополнительных столбцов
//        results.put("errorCount", validationErrors.size());
//        results.put("validationErrors", validationErrors);
//
//    }
//
//    private void logResults(String filename, int recordCount, int errorCount) {
//        if (errorCount > 0) {
//            log.warn("CSV файл обработан с ошибками: {}. Записей: {}, Ошибок: {}",
//                    filename, recordCount, errorCount);
//        } else {
//            log.info("CSV файл успешно обработан: {}. Всего записей: {}",
//                    filename, recordCount);
//        }
//    }
    private static final int BATCH_SIZE = 1000;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PathResolver pathResolver;

    public CsvFileProcessor(PathResolver pathResolver) {
        this.pathResolver = pathResolver;
    }

    @Override
    public Map<String, Object> processFile(Path filePath, FileMetadata metadata,
                                           ProcessingProgressCallback progressCallback) {
        log.debug("Начало обработки CSV файла: {}", metadata.getOriginalFilename());
        log.info("Initial heap: {}", getHeapSizeAsString());

        Map<String, Object> results = new HashMap<>();
        List<ValidationError> validationErrors = new ArrayList<>();
        Path tempFile = null;

        try {
            Path tempDir = pathResolver.getTempDirectory();
            Files.createDirectories(tempDir);
            tempFile = tempDir.resolve("csv_processing_" +
                    System.currentTimeMillis() + "_" +
                    metadata.getOriginalFilename() + ".tmp");

            // Читаем заголовки и общее количество строк
            List<String> headers = readHeaders(filePath, metadata);
            results.put("headers", headers);

            long totalLines = Files.lines(filePath, Charset.forName(metadata.getEncoding())).count() - 1;
            AtomicInteger processedRecords = new AtomicInteger(0);

            // Обрабатываем записи и сохраняем во временный файл
            processRecords(filePath, tempFile, metadata, headers, totalLines,
                    processedRecords, progressCallback);

            // Читаем результаты из временного файла
            List<Map<String, String>> records = readRecordsFromTempFile(tempFile);

            finalizeResults(results, (int) totalLines, records, headers, validationErrors);
            logResults(metadata.getOriginalFilename(), records.size(), validationErrors.size());

            archiveOriginalFile(filePath, metadata);

            log.info("Final heap: {}", getHeapSizeAsString());
            return results;

        } catch (Exception e) {
            log.error("Ошибка при обработке CSV файла: {}", e.getMessage(), e);
            throw new FileProcessingException("Ошибка при обработке CSV файла: " + e.getMessage(), e);
        } finally {
            cleanupTempFile(tempFile);
        }
    }

    private static class CsvRecordIterator implements Iterator<String[]> {
        private final CSVReader csvReader;
        private String[] nextLine;

        public CsvRecordIterator(CSVReader csvReader) {
            this.csvReader = csvReader;
            advance();
        }

        private void advance() {
            try {
                nextLine = csvReader.readNext();
            } catch (IOException | CsvValidationException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public boolean hasNext() {
            return nextLine != null;
        }

        @Override
        public String[] next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            String[] current = nextLine;
            advance();
            return current;
        }
    }

    private void processRecords(Path filePath, Path tempFile, FileMetadata metadata,
                                List<String> headers, long totalLines, AtomicInteger processedRecords,
                                ProcessingProgressCallback progressCallback) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                Files.newBufferedReader(filePath, Charset.forName(metadata.getEncoding())),
                8192 * 4);
             BufferedWriter tempWriter = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8);
             CSVReader csvReader = new CSVReaderBuilder(reader)
                     .withCSVParser(new CSVParserBuilder()
                             .withSeparator(metadata.getDelimiter().charAt(0))
                             .withIgnoreQuotations(false)
                             .build())
                     .build()) {

            // Пропускаем заголовки
            csvReader.readNext();

            Stream<String[]> recordStream = StreamSupport.stream(
                    Spliterators.spliteratorUnknownSize(
                            new CsvRecordIterator(csvReader),
                            Spliterator.ORDERED | Spliterator.NONNULL
                    ),
                    false
            );

            List<Map<String, String>> currentBatch = new ArrayList<>(BATCH_SIZE);

            recordStream.forEach(nextLine -> {
                try {
                    Map<String, String> record = processRecord(nextLine, headers);
                    currentBatch.add(record);

                    if (currentBatch.size() >= BATCH_SIZE) {
                        writeBatchToTempFile(currentBatch, tempWriter);
                        currentBatch.clear();

                        int processed = processedRecords.addAndGet(BATCH_SIZE);
                        updateProgress(processed, totalLines, progressCallback);
                        log.debug("Processed batch. Current heap: {}", getHeapSizeAsString());

                        if (processed % (BATCH_SIZE * 10) == 0) {
                            System.gc();
                            log.debug("GC called. Heap after GC: {}", getHeapSizeAsString());
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            if (!currentBatch.isEmpty()) {
                writeBatchToTempFile(currentBatch, tempWriter);
                processedRecords.addAndGet(currentBatch.size());
            }
        } catch (CsvValidationException e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> readHeaders(Path filePath, FileMetadata metadata) throws IOException {
        try (Reader reader = Files.newBufferedReader(filePath, Charset.forName(metadata.getEncoding()));
             CSVReader csvReader = new CSVReaderBuilder(reader)
                     .withCSVParser(new CSVParserBuilder()
                             .withSeparator(metadata.getDelimiter().charAt(0))
                             .withIgnoreQuotations(false)
                             .build())
                     .build()) {

            String[] headerArray = csvReader.readNext();
            if (headerArray == null) {
                throw new FileProcessingException("CSV файл пуст или не содержит заголовков");
            }
            return new ArrayList<>(Arrays.asList(headerArray));
        } catch (CsvValidationException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, String> processRecord(String[] line, List<String> headers) {
        Map<String, String> record = new HashMap<>();

        // Обработка основных колонок
        for (int i = 0; i < headers.size() && i < line.length; i++) {
            String value = line[i];
            record.put(headers.get(i), value != null ? value.trim() : "");
        }

        // Обработка дополнительных колонок
        for (int i = headers.size(); i < line.length; i++) {
            String extraHeader = "Column_" + (i + 1);
            record.put(extraHeader, line[i].trim());
        }

        return record;
    }

    private void writeBatchToTempFile(List<Map<String, String>> batch, BufferedWriter writer) throws IOException {
        for (Map<String, String> record : batch) {
            writer.write(objectMapper.writeValueAsString(record));
            writer.newLine();
        }
        writer.flush();
    }

    private List<Map<String, String>> readRecordsFromTempFile(Path tempFile) throws IOException {
        List<Map<String, String>> records = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(tempFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                records.add(objectMapper.readValue(line, new TypeReference<Map<String, String>>() {}));
            }
        }
        return records;
    }

    private void updateProgress(int processed, long total, ProcessingProgressCallback progressCallback) {
        int progress = (int) ((processed * 100.0) / total);
        progressCallback.updateProgress(progress,
                String.format("Обработано записей: %d из %d", processed, total));
    }

    private void archiveOriginalFile(Path filePath, FileMetadata metadata) {
        try {
            Path archivePath = pathResolver.getArchiveFilePath(
                    metadata.getClientId(),
                    metadata.getOriginalFilename()
            );

            Files.createDirectories(archivePath.getParent());
            Files.move(filePath, archivePath, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Файл перемещен в архив: {}", archivePath);
        } catch (IOException e) {
            log.warn("Не удалось переместить файл в архив: {}", e.getMessage(), e);
        }
    }

    private void cleanupTempFile(Path tempFile) {
        if (tempFile != null) {
            try {
                Files.deleteIfExists(tempFile);
                log.debug("Временный файл удален: {}", tempFile);
            } catch (IOException e) {
                log.warn("Не удалось удалить временный файл: {}", tempFile, e);
            }
        }
    }

    private void finalizeResults(Map<String, Object> results,
                                 Integer totalCount,
                                 List<Map<String, String>> records,
                                 List<String> headers,
                                 List<ValidationError> validationErrors) {
        results.put("records", records);
        results.put("totalCount", totalCount);
        results.put("successCount", records.size());
        results.put("headers", headers);
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

    public static String getHeapSizeAsString() {

        // Get current size of heap in bytes
        long heapSize = Runtime.getRuntime().totalMemory();

        // Get maximum size of heap in bytes. The heap cannot grow beyond this
        // size.// Any attempt will result in an OutOfMemoryException.
        long heapMaxSize = Runtime.getRuntime().maxMemory();

        // Get amount of free memory within the heap in bytes. This size will
        // increase // after garbage collection and decrease as new objects are
        // created.
        // long heapFreeSize = Runtime.getRuntime().freeMemory();

        return "heapSize = " + (heapSize / 1024 / 1024) + " / " + (heapMaxSize / 1024 / 1024) + " ("
                + (heapSize * 100 / heapMaxSize) + "%)";

        // + ", heapFreeSize = " + (heapFreeSize / 1024 / 1024)
    }

    @Override
    public boolean supports(FileMetadata fileMetadata) {
        return FileType.CSV.equals(fileMetadata.getFileType());
    }
}
