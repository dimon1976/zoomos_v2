package by.zoomos_v2.service.file.input.processor;

import by.zoomos_v2.exception.FileProcessingException;
import by.zoomos_v2.model.FileMetadata;
import by.zoomos_v2.model.FileType;
import by.zoomos_v2.service.file.input.callback.ProcessingProgressCallback;
import by.zoomos_v2.service.file.input.service.StreamingFileProcessor;
import by.zoomos_v2.util.PathResolver;
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
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static by.zoomos_v2.util.HeapSize.getHeapSizeAsString;

/**
 * Процессор для обработки CSV файлов
 */
@Slf4j
@Component
public class CsvFileProcessor implements FileProcessor, StreamingFileProcessor {

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
        log.info("Initial heap processFile: {}", getHeapSizeAsString());

        Path tempFile = null;

        try {
            // Создаем временный файл
            Path tempDir = pathResolver.getTempDirectory();
            Files.createDirectories(tempDir);
            tempFile = tempDir.resolve("csv_processing_" +
                    System.currentTimeMillis() + "_" +
                    metadata.getOriginalFilename() + ".tmp");

            // Читаем заголовки
            List<String> headers = readHeaders(filePath, metadata);

            // Считаем общее количество строк
            long totalLines = countLines(filePath, metadata);

            // Обрабатываем файл в потоковом режиме
            processFileStreaming(filePath, metadata, tempFile, progressCallback, headers);

            // Возвращаем метаданные обработки
            Map<String, Object> results = new HashMap<>();
            results.put("headers", headers);
            results.put("totalCount", totalLines);
            results.put("tempFilePath", tempFile);

            log.info("Final heap processFile: {}", getHeapSizeAsString());
            return results;

        } catch (Exception e) {
            cleanupTempFile(tempFile);
            log.error("Ошибка при обработке CSV файла: {}", e.getMessage(), e);
            throw new FileProcessingException("Ошибка при обработке CSV файла: " + e.getMessage(), e);
        }
    }

    @Override
    public void processFileStreaming(Path filePath, FileMetadata metadata,
                                     Path tempOutputPath,
                                     ProcessingProgressCallback progressCallback,
                                     List<String> headers) throws IOException {
        log.debug("Начало потоковой обработки CSV файла: {}", metadata.getOriginalFilename());
        Files.createDirectories(tempOutputPath.getParent());

        try (BufferedReader reader = new BufferedReader(
                Files.newBufferedReader(filePath, Charset.forName(metadata.getEncoding())),
                8192 * 4);
             BufferedWriter tempWriter = Files.newBufferedWriter(tempOutputPath, StandardCharsets.UTF_8);
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
            AtomicInteger processedRecords = new AtomicInteger(0);
            AtomicLong totalProcessed = new AtomicLong(0);
            long totalLines = countLines(filePath, metadata);

            recordStream.forEach(nextLine -> {
                try {
                    Map<String, String> record = processRecord(nextLine, headers);
                    currentBatch.add(record);

                    if (currentBatch.size() >= BATCH_SIZE) {
                        writeBatchToTempFile(currentBatch, tempWriter);
                        currentBatch.clear();

                        int processed = processedRecords.addAndGet(BATCH_SIZE);
                        totalProcessed.addAndGet(BATCH_SIZE);

                        updateProgress(totalProcessed.get(), totalLines, progressCallback);

                        log.debug("Processed batch. Current heap: {}", getHeapSizeAsString());

                        if (processed % (BATCH_SIZE * 10) == 0) {
                            System.gc();
                            log.debug("GC called. Heap after GC: {}", getHeapSizeAsString());
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Ошибка при обработке записи CSV: " + e.getMessage(), e);
                }
            });

            if (!currentBatch.isEmpty()) {
                writeBatchToTempFile(currentBatch, tempWriter);
                totalProcessed.addAndGet(currentBatch.size());
                updateProgress(totalProcessed.get(), totalLines, progressCallback);
            }

        } catch (CsvValidationException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public long countLines(Path filePath, FileMetadata metadata) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(filePath, Charset.forName(metadata.getEncoding()))) {
            return reader.lines().count() - 1; // Минус заголовок
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

    public List<String> readHeaders(Path filePath, FileMetadata metadata) throws IOException {
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


    private void updateProgress(long processed, long total, ProcessingProgressCallback progressCallback) {
        int progress = (int) ((processed * 100.0) / total);
        progressCallback.updateProgress(progress,
                String.format("Обработано записей: %d из %d", processed, total));
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


    @Override
    public boolean supports(FileMetadata fileMetadata) {
        return FileType.CSV.equals(fileMetadata.getFileType());
    }
}
