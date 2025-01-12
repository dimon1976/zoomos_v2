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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// Сервис для чтения и записи данных
@Service
@Slf4j
public class FileProcessingService {
    private final FileAnalyzerService fileAnalyzerService;
    private final MappingConfigService mappingConfigService;

    public FileProcessingService(FileAnalyzerService fileAnalyzerService, MappingConfigService mappingConfigService) {
        this.fileAnalyzerService = fileAnalyzerService;
        this.mappingConfigService = mappingConfigService;
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

        // Инвертируем mapping: "заголовок файла" -> "поле сущности"
        Map<String, String> invertedMapping = mapping.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getValue,  // значение становится ключом
                        Map.Entry::getKey,    // ключ становится значением
                        (existing, replacement) -> existing,  // в случае дубликатов оставляем первое значение
                        LinkedHashMap::new
                ));

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
                Map<String, String> row = new LinkedHashMap<>();

                // До цикла добавим отладочную информацию
                log.debug("Headers: {}", metadata.getHeaders());
                log.debug("Mapping: {}", mapping);

                for (int i = 0; i < metadata.getHeaders().size() && i < line.length; i++) {
                    String header = metadata.getHeaders().get(i).replaceAll("^\"|\"$", "").trim();
                    String entityField = invertedMapping.get(header);  // Теперь ищем по заголовку файла

                    if (entityField != null) {
                        row.put(entityField, line[i].trim());
                    }
                }

                if (!row.isEmpty()) {
                    results.add(row);
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

        // Инвертируем mapping: "заголовок файла" -> "поле сущности"
        Map<String, String> invertedMapping = mapping.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getValue,  // значение становится ключом
                        Map.Entry::getKey,    // ключ становится значением
                        (existing, replacement) -> existing,  // в случае дубликатов оставляем первое значение
                        LinkedHashMap::new
                ));

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            // Пропускаем заголовок
            int firstDataRow = 1;

            for (int rowNum = firstDataRow; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null) continue;

                Map<String, String> rowData = new LinkedHashMap<>();

                for (int i = 0; i < metadata.getHeaders().size(); i++) {
                    Cell cell = row.getCell(i);
                    String header = metadata.getHeaders().get(i).replaceAll("^\"|\"$", "").trim();
                    String entityField = invertedMapping.get(header);

                    if (entityField != null) {
                        rowData.put(entityField, fileAnalyzerService.getCellValueAsString(cell));
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
    public void saveData(List<Map<String, String>> data, Long clientId) {
        // Здесь будет логика сохранения данных в БД
        // Нужно будет реализовать создание и сохранение сущностей
        // на основе полученных данных
    }

}
