package by.zoomos_v2.service.file;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Класс для хранения информации во время обработки файлов.
 */
@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchProcessingData {

    private Path tempFilePath;
    private List<String> headers = new ArrayList<>();
    private List<Map<String, String>> processedData;

    public synchronized void writeRecord(Map<String, String> record) throws IOException {
        if (tempFilePath == null) {
            tempFilePath = Files.createTempFile("processing_data_", ".tmp");
        }
        try (BufferedWriter writer = Files.newBufferedWriter(tempFilePath,
                StandardCharsets.UTF_8, StandardOpenOption.APPEND)) {
            writer.write(new ObjectMapper().writeValueAsString(record));
            writer.newLine();
        }
    }

    public void processTempFileInBatches(int batchSize,
                                         Consumer<List<Map<String, String>>> batchProcessor) throws IOException {
        if (tempFilePath == null || !Files.exists(tempFilePath)) {
            return;
        }

        List<Map<String, String>> batch = new ArrayList<>(batchSize);
        try (BufferedReader reader = Files.newBufferedReader(tempFilePath)) {
            String line;
            ObjectMapper mapper = new ObjectMapper();
            while ((line = reader.readLine()) != null) {
                batch.add(mapper.readValue(line, new TypeReference<Map<String, String>>() {
                }));

                if (batch.size() >= batchSize) {
                    batchProcessor.accept(new ArrayList<>(batch));
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                batchProcessor.accept(batch);
            }
        }
    }

    public void cleanup() {
        if (tempFilePath != null) {
            try {
                Files.deleteIfExists(tempFilePath);
            } catch (IOException e) {
                log.warn("Не удалось удалить временный файл: {}", tempFilePath, e);
            }
        }
    }

    /**
     * Создает новый экземпляр BatchProcessingData
     *
     * @return новый объект BatchProcessingData
     */
    public static BatchProcessingData createNew() {
        return BatchProcessingData.builder()
                .headers(new ArrayList<>())
                .processedData(new ArrayList<>())
                .build();
    }
}
