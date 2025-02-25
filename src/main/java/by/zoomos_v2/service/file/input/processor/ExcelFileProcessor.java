package by.zoomos_v2.service.file.input.processor;

import by.zoomos_v2.exception.FileProcessingException;
import by.zoomos_v2.model.FileMetadata;
import by.zoomos_v2.model.FileType;
import by.zoomos_v2.service.file.input.callback.ProcessingProgressCallback;
import by.zoomos_v2.service.file.input.service.StreamingFileProcessor;
import by.zoomos_v2.util.PathResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static by.zoomos_v2.util.HeapSize.getHeapSizeAsString;

/**
 * Процессор для обработки Excel файлов
 */
@Slf4j
@Component
public class ExcelFileProcessor implements FileProcessor, StreamingFileProcessor {

    private static final int BATCH_SIZE = 1000;

    private final PathResolver pathResolver;
    private final ObjectMapper objectMapper;

    public ExcelFileProcessor(PathResolver pathResolver, ObjectMapper objectMapper) {
        this.pathResolver = pathResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> processFile(Path filePath, FileMetadata metadata,
                                           ProcessingProgressCallback progressCallback) {
        log.debug("Начало обработки Excel файла: {}", metadata.getOriginalFilename());
        log.info("Initial heap processFile: {}", getHeapSizeAsString());

        Path tempFile = null;

        try {
            // Создаем временный файл
            Path tempDir = pathResolver.getTempDirectory();
            Files.createDirectories(tempDir);
            tempFile = tempDir.resolve("excel_processing_" +
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
            log.error("Ошибка при обработке Excel файла: {}", e.getMessage(), e);
            throw new FileProcessingException("Ошибка при обработке Excel файла: " + e.getMessage(), e);
        }
    }

    @Override
    public void processFileStreaming(Path filePath, FileMetadata metadata,
                                     Path tempOutputPath,
                                     ProcessingProgressCallback progressCallback,
                                     List<String> headers) throws IOException {
        log.debug("Начало потоковой обработки Excel файла: {}", metadata.getOriginalFilename());

        List<Map<String, String>> currentBatch = new ArrayList<>(BATCH_SIZE);
        long processedRows = 0;

        try (InputStream is = Files.newInputStream(filePath);
             Workbook workbook = WorkbookFactory.create(is);
             BufferedWriter writer = Files.newBufferedWriter(tempOutputPath, StandardCharsets.UTF_8)) {

            Sheet sheet = workbook.getSheetAt(0);
            int totalRows = sheet.getLastRowNum();

            // Пропускаем заголовки (строка 0) и начинаем с первой строки данных
            for (int rowNum = 1; rowNum <= totalRows; rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row != null) {
                    Map<String, String> record = new LinkedHashMap<>();

                    // Обрабатываем каждую ячейку
                    for (int colNum = 0; colNum < headers.size(); colNum++) {
                        Cell cell = row.getCell(colNum);
                        record.put(headers.get(colNum), getCellValueAsString(cell));
                    }

                    currentBatch.add(record);

                    // Если достигли размера батча, записываем и очищаем
                    if (currentBatch.size() >= BATCH_SIZE) {
                        writeBatchToFile(currentBatch, writer);
                        processedRows += currentBatch.size();
                        updateProgress(processedRows, totalRows, progressCallback);
                        currentBatch.clear();

                        // Вызываем GC каждые 10 батчей
                        if (processedRows % (BATCH_SIZE * 10) == 0) {
                            System.gc();
                            log.debug("Выполнен GC. Обработано строк: {}", processedRows);
                        }
                    }
                }
            }

            // Записываем оставшиеся данные
            if (!currentBatch.isEmpty()) {
                writeBatchToFile(currentBatch, writer);
                processedRows += currentBatch.size();
                updateProgress(processedRows, totalRows, progressCallback);
            }
        }
    }


    private void writeBatchToFile(List<Map<String, String>> batch, BufferedWriter writer) throws IOException {
        for (Map<String, String> record : batch) {
            writer.write(objectMapper.writeValueAsString(record));
            writer.newLine();
        }
        writer.flush();
        log.debug("Записан батч данных. Размер: {}", batch.size());
    }

    private void updateProgress(long processed, long total, ProcessingProgressCallback progressCallback) {
        int progress = (int) (processed * 100.0 / total);
        progressCallback.updateProgress(progress,
                String.format("Обработано строк: %d из %d", processed, total));
    }

    @Override
    public long countLines(Path filePath, FileMetadata metadata) throws IOException {
        try (InputStream is = Files.newInputStream(filePath);
             Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            return sheet.getPhysicalNumberOfRows() - 1; // Минус заголовок
        } catch (Exception e) {
            throw new IOException("Ошибка при подсчете строк", e);
        }
    }

    @Override
    public List<String> readHeaders(Path filePath, FileMetadata metadata) throws IOException {
        try (InputStream is = Files.newInputStream(filePath);
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);

            if (headerRow == null) {
                throw new FileProcessingException("Excel файл пуст или не содержит заголовков");
            }

            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(getCellValueAsString(cell));
            }
            return headers;
        } catch (Exception e) {
            throw new IOException("Ошибка при чтении заголовков", e);
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
        return FileType.EXCEL.equals(fileMetadata.getFileType()) ||
                FileType.XLS.equals(fileMetadata.getFileType());
    }

//    @Override
//    public boolean supports(FileMetadata fileMetadata) {
//        return FileType.EXCEL.equals(fileMetadata.getFileType()) ||
//                FileType.XLS.equals(fileMetadata.getFileType());
//    }
//
//    @Override
//    public Map<String, Object> processFile(Path filePath, FileMetadata metadata,
//                                           ProcessingProgressCallback progressCallback) {
//        log.debug("Начало обработки Excel файла: {}", metadata.getOriginalFilename());
//
//        Map<String, Object> results = new HashMap<>();
//        List<Map<String, String>> records = new ArrayList<>();
//        List<String> headers = new ArrayList<>();
//
//        try (Workbook workbook = WorkbookFactory.create(filePath.toFile())) {
//            Sheet sheet = workbook.getSheetAt(0);
//            Row headerRow = sheet.getRow(0);
//
//            // Получаем заголовки
//            if (headerRow == null) {
//                throw new FileProcessingException("Файл не содержит заголовков");
//            }
//
//            for (Cell cell : headerRow) {
//                headers.add(getCellValueAsString(cell));
//            }
//            results.put("headers", headers);
//
//            // Подсчитываем общее количество строк
//            int totalRows = sheet.getLastRowNum();
//
//            // Обрабатываем каждую строку
//            for (int i = 1; i <= totalRows; i++) {
//                Row row = sheet.getRow(i);
//                if (row != null) {
//                    Map<String, String> record = new HashMap<>();
//                    for (int j = 0; j < headers.size(); j++) {
//                        Cell cell = row.getCell(j);
//                        record.put(headers.get(j), getCellValueAsString(cell));
//                    }
//                    records.add(record);
//                }
//
//                // Обновляем прогресс
//                int progress = (int) ((i * 100.0) / totalRows);
//                progressCallback.updateProgress(progress,
//                        String.format("Обработано строк: %d из %d", i, totalRows));
//            }
//
////            results.put("successCount", records);
////            results.put("totalCount", records.size());
//            results.put("records", records);
//            results.put("totalCount", records.size());
//            results.put("successCount", records.size());
//            results.put("headers", headers); // Обновляем заголовки с учетом дополнительных столбцов
//
//            log.info("Excel файл успешно обработан: {}", metadata.getOriginalFilename());
//            return results;
//
//        } catch (Exception e) {
//            log.error("Ошибка при обработке Excel файла: {}", e.getMessage(), e);
//            throw new FileProcessingException("Ошибка при обработке Excel файла", e);
//        }
//    }
//
//    private String getCellValueAsString(Cell cell) {
//        if (cell == null) {
//            return "";
//        }
//
//        switch (cell.getCellType()) {
//            case STRING:
//                return cell.getStringCellValue();
//            case NUMERIC:
//                if (DateUtil.isCellDateFormatted(cell)) {
//                    return cell.getLocalDateTimeCellValue().toString();
//                }
//                return String.valueOf(cell.getNumericCellValue());
//            case BOOLEAN:
//                return String.valueOf(cell.getBooleanCellValue());
//            case FORMULA:
//                try {
//                    return String.valueOf(cell.getNumericCellValue());
//                } catch (Exception e) {
//                    return cell.getStringCellValue();
//                }
//            default:
//                return "";
//        }
//    }
}