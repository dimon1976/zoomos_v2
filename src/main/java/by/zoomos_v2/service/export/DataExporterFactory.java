package by.zoomos_v2.service.export;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Фабрика для создания экспортеров данных
 */
@Component
@RequiredArgsConstructor
public class DataExporterFactory {
    private final List<DataExporter> exporters;

    /**
     * Возвращает экспортер для указанного типа файла
     *
     * @param fileType тип файла (CSV, XLSX и т.д.)
     * @return экспортер данных
     * @throws IllegalArgumentException если экспортер не найден
     */
    public DataExporter getExporter(String fileType) {
        return findExporter(fileType)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Exporter not found for file type: %s", fileType)));
    }

    /**
     * Проверяет поддерживается ли указанный тип файла
     */
    public boolean isSupported(String fileType) {
        return findExporter(fileType).isPresent();
    }

    /**
     * Возвращает список поддерживаемых типов файлов
     */
    public List<String> getSupportedTypes() {
        return exporters.stream()
                .map(DataExporter::getFileType)
                .toList();
    }

    private Optional<DataExporter> findExporter(String fileType) {
        if (fileType == null) {
            return Optional.empty();
        }

        return exporters.stream()
                .filter(exporter -> fileType.equalsIgnoreCase(exporter.getFileType()))
                .findFirst();
    }
}
