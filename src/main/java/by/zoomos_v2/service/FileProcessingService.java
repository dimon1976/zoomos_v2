package by.zoomos_v2.service;

import by.zoomos_v2.config.UploadFileException;
import by.zoomos_v2.mapping.ClientMappingConfig;
import by.zoomos_v2.model.FileMetadata;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

// Сервис для чтения и записи данных
@Service
@Slf4j
public class FileProcessingService {
    private final FileAnalyzerService fileAnalyzerService;
    private final MappingConfigService mappingConfigService;
    private final DataPersistenceService dataPersistenceService;

    public FileProcessingService(FileAnalyzerService fileAnalyzerService, MappingConfigService mappingConfigService, DataPersistenceService dataPersistenceService) {
        this.fileAnalyzerService = fileAnalyzerService;
        this.mappingConfigService = mappingConfigService;
        this.dataPersistenceService = dataPersistenceService;
    }

    public List<Map<String, String>> readFile(MultipartFile file, Long mappingConfigId) throws IOException {
        FileMetadata metadata = fileAnalyzerService.analyzeFile(file);
        ClientMappingConfig mappingConfig = mappingConfigService.getConfigById(mappingConfigId);
        Map<String, String> mapping = mappingConfigService.parseMappingJson(mappingConfig.getMappingData());

        switch (metadata.getFileType()) {
            case CSV:
            case TXT:
                return readTextFile(file, metadata, mapping);
            case XLS:
            case XLSX:
                return readExcelFile(file, metadata, mapping);
            default:
                throw new UploadFileException("Неподдерживаемый тип файла");
        }
    }

    private List<Map<String, String>> readTextFile(MultipartFile file, FileMetadata metadata,
                                                   Map<String, String> mapping) throws IOException {
        List<Map<String, String>> results = new ArrayList<>();

        // Создаем карту соответствия индексов колонок и заголовков файла
        Map<Integer, String> headerIndexMap = new HashMap<>();
        for (int i = 0; i < metadata.getHeaders().size(); i++) {
            String header = metadata.getHeaders().get(i).replaceAll("^\"|\"$", "").trim();
            headerIndexMap.put(i, header);
        }

        CSVParser parser = new CSVParserBuilder()
                .withSeparator(metadata.getDelimiter().charAt(0))
                .build();

        try (CSVReader reader = new CSVReaderBuilder(
                new InputStreamReader(file.getInputStream(), metadata.getCharset()))
                .withCSVParser(parser)
                .build()) {

            // Пропускаем заголовок
            reader.readNext();

            String[] line;
            while ((line = reader.readNext()) != null) {
                Map<String, String> rowData = new LinkedHashMap<>();

                // Проходим по маппингу (поле сущности -> заголовок файла)
                for (Map.Entry<String, String> entry : mapping.entrySet()) {
                    String entityField = entry.getKey();      // поле сущности
                    String fileHeader = entry.getValue();     // заголовок в файле

                    // Ищем индекс колонки с нужным заголовком
                    for (Map.Entry<Integer, String> headerEntry : headerIndexMap.entrySet()) {
                        if (headerEntry.getValue().equals(fileHeader)) {
                            int columnIndex = headerEntry.getKey();
                            if (columnIndex < line.length) {
                                String value = line[columnIndex].trim();
                                rowData.put(entityField, value);
                            }
                        }
                    }
                }

                if (!rowData.isEmpty()) {
                    results.add(rowData);
                } else {
                    log.warn("Empty row found in file");
                }
            }
        } catch (CsvValidationException e) {
            throw new IOException("Error reading CSV file", e);
        }

        log.info("Processed {} rows from file", results.size());
        return results;
    }

    private List<Map<String, String>> readExcelFile(MultipartFile file, FileMetadata metadata,
                                                    Map<String, String> mapping) throws IOException {
        List<Map<String, String>> results = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            // Создаем карту соответствия индексов колонок и заголовков файла
            Map<Integer, String> headerIndexMap = new HashMap<>();
            for (int i = 0; i < metadata.getHeaders().size(); i++) {
                String header = metadata.getHeaders().get(i).replaceAll("^\"|\"$", "").trim();
                headerIndexMap.put(i, header);
            }

            // Пропускаем заголовок
            int firstDataRow = 1;

            for (int rowNum = firstDataRow; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null) continue;

                Map<String, String> rowData = new LinkedHashMap<>();

                // Проходим по маппингу (поле сущности -> заголовок файла)
                for (Map.Entry<String, String> entry : mapping.entrySet()) {
                    String entityField = entry.getKey();      // поле сущности
                    String fileHeader = entry.getValue();     // заголовок в файле

                    // Ищем индекс колонки с нужным заголовком
                    for (Map.Entry<Integer, String> headerEntry : headerIndexMap.entrySet()) {
                        if (headerEntry.getValue().equals(fileHeader)) {
                            int columnIndex = headerEntry.getKey();
                            Cell cell = row.getCell(columnIndex);
                            String cellValue = fileAnalyzerService.getCellValueAsString(cell);
                            rowData.put(entityField, cellValue);
                        }
                    }
                }

                if (!rowData.isEmpty()) {
                    results.add(rowData);
                }
            }
        }

        return results;
    }

    // Сохранение данных в БД
    @Transactional
    public void saveData(List<Map<String, String>> data, Long clientId, Long mappingId) {
        ClientMappingConfig mappingConfig = mappingConfigService.getConfigById(mappingId);
        Map<String, String> mapping = mappingConfigService.parseMappingJson(mappingConfig.getMappingData());
        dataPersistenceService.saveEntities(data, clientId, mapping);
    }

}
