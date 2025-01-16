package by.zoomos_v2.service.processor.client;
import java.util.Map;
import java.util.List;

/**
 * Интерфейс для обработки данных клиентов
 */
public interface ClientDataProcessor {
    /**
     * Обрабатывает данные для конкретного клиента
     * @param data список записей для обработки
     * @param clientId идентификатор клиента
     * @return результат обработки
     */
    ProcessingResult processData(List<Map<String, String>> data, Long clientId);

    /**
     * Проверяет валидность данных для клиента
     * @param data данные для проверки
     * @return результат валидации
     */
    ValidationResult validateData(List<Map<String, String>> data);
}
