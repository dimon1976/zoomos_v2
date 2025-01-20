package by.zoomos_v2.service.processing.client;

import by.zoomos_v2.model.Client;
import by.zoomos_v2.model.FileMetadata;

import java.util.List;
import java.util.Map;

/**
 * Интерфейс для обработки данных клиентов
 */
public interface ClientDataProcessor {

    /**
     * Проверяет валидность данных из файла
     */
    ValidationResult validateData(List<Map<String, String>> data);

    /**
     * Обрабатывает загруженный файл
     */
    void processFile(FileMetadata fileMetadata, List<Map<String, String>> data);

    /**
     * Выполняет действия после обработки файла
     * Например, отправка уведомлений, обновление статусов и т.д.
     */
    void afterProcessing(FileMetadata metadata);

    /**
     * Проверяет, может ли процессор обработать файлы данного клиента
     */
    boolean supports(Client client);
}
