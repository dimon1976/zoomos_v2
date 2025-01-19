package by.zoomos_v2.service.processing.client;
import by.zoomos_v2.model.*;

import java.util.Map;
import java.util.List;

/**
 * Интерфейс для обработки данных клиентов
 */
public interface ClientDataProcessor {
    /**
     * Обрабатывает данные для конкретного клиента
     * @param data список записей для обработки
     * @return результат обработки
     */
    ProcessingResult processData(List<Map<String, String>> data, ExportConfig config);

    /**
     * Проверяет валидность данных для клиента
     */
    ValidationResult validateData(List<Map<String, String>> data);

    /**
     * Обрабатывает загруженный файл
     */
    void processFile(FileMetadata fileMetadata, List<Map<String, String>> data);

    /**
     * Выполняет действия после обработки
     */
    void afterProcessing(FileMetadata metadata);

    /**
     * Проверяет, поддерживает ли процессор данного клиента
     */
    boolean supports(Client client);
}
