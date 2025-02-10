package by.zoomos_v2.service.file.input.processor;

import by.zoomos_v2.exception.FileProcessingException;
import by.zoomos_v2.model.FileMetadata;
import by.zoomos_v2.model.FileType;
import by.zoomos_v2.service.file.input.callback.ProcessingProgressCallback;
import by.zoomos_v2.service.file.input.service.StreamingFileProcessor;
import by.zoomos_v2.util.PathResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
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
    private final ObjectMapper objectMapper;
    private final PathResolver pathResolver;


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

        try (InputStream is = Files.newInputStream(filePath);
             BufferedWriter writer = Files.newBufferedWriter(tempOutputPath, StandardCharsets.UTF_8);
             OPCPackage pkg = OPCPackage.open(is)) {

            XSSFReader xssfReader = new XSSFReader(pkg);
            SharedStrings sst = (SharedStrings) xssfReader.getSharedStringsTable();
            StylesTable styles = xssfReader.getStylesTable();

            ExcelSheetHandler sheetHandler = new ExcelSheetHandler(writer, headers, progressCallback);
            processSheet(xssfReader, sst, styles, sheetHandler);

        } catch (Exception e) {
            throw new IOException("Ошибка при потоковой обработке Excel файла", e);
        }
    }

    private void processSheet(XSSFReader xssfReader, SharedStrings sst, StylesTable styles,
                              ExcelSheetHandler sheetHandler) throws Exception {
        XMLReader parser = createXMLReader();
        parser.setContentHandler(new XSSFSheetXMLHandler(styles, sst, sheetHandler, false));

        XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) xssfReader.getSheetsData();
        if (sheets.hasNext()) {
            try (InputStream sheetStream = sheets.next()) {
                InputSource sheetSource = new InputSource(sheetStream);
                parser.parse(sheetSource);
            }
        }
    }

    private XMLReader createXMLReader() throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        SAXParser saxParser = factory.newSAXParser();
        return saxParser.getXMLReader();
    }

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

    private static class ExcelSheetHandler implements XSSFSheetXMLHandler.SheetContentsHandler {
        private final BufferedWriter writer;
        private final List<String> headers;
        private final ProcessingProgressCallback progressCallback;
        private List<String> currentRow;
        private int rowCount = 0;
        private final ObjectMapper objectMapper = new ObjectMapper();

        public ExcelSheetHandler(BufferedWriter writer, List<String> headers,
                                 ProcessingProgressCallback progressCallback) {
            this.writer = writer;
            this.headers = headers;
            this.progressCallback = progressCallback;
        }

        @Override
        public void startRow(int rowNum) {
            if (rowNum > 0) { // Пропускаем заголовки
                currentRow = new ArrayList<>();
            }
        }

        @Override
        public void endRow(int rowNum) {
            if (rowNum > 0) { // Пропускаем заголовки
                try {
                    Map<String, String> record = new LinkedHashMap<>();

                    // Дополняем строку пустыми значениями если необходимо
                    while (currentRow.size() < headers.size()) {
                        currentRow.add("");
                    }

                    // Создаем запись с данными
                    for (int i = 0; i < headers.size(); i++) {
                        record.put(headers.get(i), currentRow.get(i));
                    }

                    // Записываем строку в файл
                    writer.write(objectMapper.writeValueAsString(record));
                    writer.newLine();
                    rowCount++;

                    // Обновляем прогресс каждые 1000 записей
                    if (rowCount % 1000 == 0) {
                        writer.flush();
                        progressCallback.updateProgress(rowCount,
                                String.format("Обработано строк: %d", rowCount));
                    }

                } catch (IOException e) {
                    throw new RuntimeException("Ошибка при записи строки", e);
                }
            }
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            if (currentRow != null) { // Пропускаем обработку заголовков
                currentRow.add(formattedValue != null ? formattedValue : "");
            }
        }

        @Override
        public void headerFooter(String text, boolean isHeader, String tagName) {
            // Не используется
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