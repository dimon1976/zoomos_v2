package by.zoomos_v2.service.export;

import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.model.ExportResult;
import by.zoomos_v2.service.processing.processor.ProcessingStats;

import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * Базовый интерфейс для всех экспортеров данных.
 * Определяет основной контракт для экспорта данных в различные форматы.
 */
public interface DataExporter {
    /**
     * Экспортирует данные в выходной поток в соответствии с конфигурацией
     *
     * @param data данные для экспорта в виде списка Map
     * @param outputStream поток для записи результата
     * @param exportConfig конфигурация экспорта
     * @param processingStats статистика обработки
     * @return результат экспорта
     */
    ExportResult export(List<Map<String, Object>> data,
                        OutputStream outputStream,
                        ExportConfig exportConfig,
                        ProcessingStats processingStats);

    /**
     * Возвращает поддерживаемый тип файла для экспорта
     */
    String getFileType();
}
