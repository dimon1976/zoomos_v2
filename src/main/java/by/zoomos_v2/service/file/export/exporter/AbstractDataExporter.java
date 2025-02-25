package by.zoomos_v2.service.file.export.exporter;
import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.model.ExportResult;
import by.zoomos_v2.service.file.BatchProcessingData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * Абстрактный базовый класс для экспортеров данных
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractDataExporter implements DataExporter{
    /**
     * Шаблонный метод для процесса экспорта
     */
    @Override
    public ExportResult export(List<Map<String, Object>> data,
                               OutputStream outputStream,
                               ExportConfig exportConfig,
                               BatchProcessingData batchProcessingData) {
        try {
            long startTime = System.currentTimeMillis();

            // Валидация входных данных
            validateInput(data, outputStream, exportConfig);

            // Подготовка данных к экспорту
            List<Map<String, Object>> preparedData = prepareData(data, exportConfig);

            // Выполнение экспорта
            doExport(preparedData, outputStream, exportConfig);

            // Формирование результата
            return ExportResult.success(
                    batchProcessingData,
                    generateFileName(exportConfig)
            );

        } catch (Exception e) {
            log.error("Error during export: ", e);
            if (batchProcessingData != null) {
//                processingStats.addError(e.getMessage(), "EXPORT_ERROR");
            }
            return ExportResult.error(e.getMessage());
        }
    }

    /**
     * Метод для валидации входных данных
     */
    protected void validateInput(List<Map<String, Object>> data,
                                 OutputStream outputStream,
                                 ExportConfig exportConfig) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("No data to export");
        }
        if (outputStream == null) {
            throw new IllegalArgumentException("Output stream is null");
        }
        if (exportConfig == null) {
            throw new IllegalArgumentException("Export config is null");
        }
    }

    /**
     * Метод для подготовки данных к экспорту
     */
    protected List<Map<String, Object>> prepareData(List<Map<String, Object>> data,
                                                    ExportConfig exportConfig) {
        return data; // По умолчанию возвращаем данные без изменений
    }

    /**
     * Абстрактный метод для реализации конкретного способа экспорта
     */
    protected abstract void doExport(List<Map<String, Object>> data,
                                     OutputStream outputStream,
                                     ExportConfig exportConfig) throws Exception;

    /**
     * Метод для генерации имени файла
     */
    protected String generateFileName(ExportConfig exportConfig) {
        return String.format("export_%s.%s",
                System.currentTimeMillis(),
                getFileType().toLowerCase());
    }
}
