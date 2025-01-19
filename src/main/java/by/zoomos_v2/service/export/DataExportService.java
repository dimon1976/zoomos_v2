package by.zoomos_v2.service.export;
import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.model.ExportResult;
import by.zoomos_v2.service.processing.processor.ProcessingStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

/**
 * Сервис для экспорта данных в различные форматы
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataExportService {
    private final DataExporterFactory exporterFactory;

    /**
     * Экспортирует данные в соответствии с конфигурацией
     *
     * @param data данные для экспорта
     * @param exportConfig конфигурация экспорта
     * @param fileType тип файла (CSV, XLSX)
     * @return результат экспорта с байтами файла
     */
    public ExportResult exportData(List<Map<String, Object>> data,
                                   ExportConfig exportConfig,
                                   String fileType) {
        log.info("Starting export process for config: {}, fileType: {}", exportConfig.getName(), fileType);

        try {
            // Получаем нужный экспортер
            DataExporter exporter = exporterFactory.getExporter(fileType);

            // Создаем статистику
            ProcessingStats stats = ProcessingStats.builder()
                    .totalCount(data.size())
                    .build();

            // Создаем выходной поток для данных
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            // Выполняем экспорт
            ExportResult result = exporter.export(data, outputStream, exportConfig, stats);

            // Добавляем данные файла в результат
            result.setFileContent(outputStream.toByteArray());

            log.info("Export completed successfully. Processed {} records", stats.getTotalCount());
            return result;

        } catch (Exception e) {
            log.error("Error during export", e);
            return ExportResult.error(e.getMessage());
        }
    }

    /**
     * Проверяет поддерживается ли указанный тип файла
     */
    public boolean isFormatSupported(String fileType) {
        return exporterFactory.isSupported(fileType);
    }

    /**
     * Возвращает список поддерживаемых форматов
     */
    public List<String> getSupportedFormats() {
        return exporterFactory.getSupportedTypes();
    }
}
