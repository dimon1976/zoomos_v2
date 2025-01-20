package by.zoomos_v2.service.processing.client;
import by.zoomos_v2.constant.FileStatus;
import by.zoomos_v2.model.Client;
import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.model.FileMetadata;
import by.zoomos_v2.service.processing.processor.ProcessingStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Процессор данных по умолчанию
 * Пропускает данные без изменений
 */
@Slf4j
@Component
public class DefaultClientDataProcessor implements ClientDataProcessor {


    @Override
    public ValidationResult validateData(List<Map<String, String>> data) {
        ValidationResult result = new ValidationResult();
        List<String> errors = new ArrayList<>();

        if (data == null || data.isEmpty()) {
            errors.add("Файл не содержит данных");
            result.setValid(false);
            result.setErrors(errors);
            return result;
        }

        result.setValid(true);
        result.setErrors(errors);
        return result;
    }

    @Override
    public void processFile(FileMetadata fileMetadata, List<Map<String, String>> data) {
        try {
            log.info("Начало обработки файла {} процессором по умолчанию",
                    fileMetadata.getOriginalFilename());

            // Обновляем статус
            fileMetadata.updateStatus(FileStatus.PROCESSING, null);

            // Статистика
            fileMetadata.updateProcessingStatistics(
                    data.size(),    // всего записей
                    data.size(),    // успешных записей (все, т.к. не меняем данные)
                    0               // ошибок нет
            );

            // Успешное завершение
            fileMetadata.updateStatus(FileStatus.COMPLETED, null);

            log.info("Завершение обработки файла {}. Обработано записей: {}",
                    fileMetadata.getOriginalFilename(), data.size());

        } catch (Exception e) {
            log.error("Ошибка при обработке файла: {}", e.getMessage(), e);
            fileMetadata.updateStatus(FileStatus.ERROR, e.getMessage());
            fileMetadata.addProcessingError(e.getMessage());
        }
    }

    @Override
    public void afterProcessing(FileMetadata metadata) {
        if (FileStatus.COMPLETED.equals(metadata.getStatus())) {
            log.info("Завершена обработка файла {}", metadata.getOriginalFilename());
        }
    }

    @Override
    public boolean supports(Client client) {
        return true; // Поддерживает всех клиентов
    }
}
