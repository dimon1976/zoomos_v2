package by.zoomos_v2.service.processing.client;

import by.zoomos_v2.constant.FileStatus;
import by.zoomos_v2.model.Client;
import by.zoomos_v2.model.FileMetadata;
import by.zoomos_v2.service.client.ClientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Процессор данных для Client1
 */
@Slf4j
@Component
public class Client1DataProcessor implements ClientDataProcessor {
    /**
     * Список сайтов конкурентов, для которых необходимо очищать данные при обработке
     */
    private static final List<String> EXCLUDED_COMPETITORS = List.of(
            "auchan.ru",
            "lenta.com",
            "metro-cc.ru",
            "myspar.ru",
            "okeydostavka.ru",
            "perekrestok.ru",
            "winelab.ru"
    );

    public Client1DataProcessor(ClientService clientService) {
    }

    @Override
    public ValidationResult validateData(List<Map<String, String>> data) {
        ValidationResult result = new ValidationResult();
        List<String> errors = new ArrayList<>();

        try {
            if (data == null || data.isEmpty()) {
                errors.add("Отсутствуют данные для обработки");
                result.setValid(false);
                result.setErrors(errors);
                return result;
            }

            // Проверяем каждую строку данных
            for (int i = 0; i < data.size(); i++) {
                Map<String, String> row = data.get(i);
                validateRequiredFields(row, i + 1, errors);
                validateDataFormats(row, i + 1, errors);
            }

            result.setValid(errors.isEmpty());
            result.setErrors(errors);

        } catch (Exception e) {
            log.error("Ошибка валидации данных: {}", e.getMessage(), e);
            errors.add("Ошибка валидации: " + e.getMessage());
            result.setValid(false);
            result.setErrors(errors);
        }

        return result;
    }

    @Override
    public void processFile(FileMetadata fileMetadata, List<Map<String, String>> data) {
        try {
            log.info("Начало обработки файла {} для клиента {}",
                    fileMetadata.getOriginalFilename(),
                    fileMetadata.getClientId());

            // Обновляем статус файла
            fileMetadata.updateStatus(FileStatus.PROCESSING, null);

            // Обработка данных
            List<Map<String, String>> processedData = new ArrayList<>();
            int successCount = 0;
            List<String> errors = new ArrayList<>();

            for (Map<String, String> row : data) {
                try {
                    Map<String, String> processedRow = processRow(row);
                    processedData.add(processedRow);
                    successCount++;
                } catch (Exception e) {
                    errors.add("Ошибка обработки строки: " + e.getMessage());
                }
            }

            // Обновляем статистику
            fileMetadata.updateProcessingStatistics(
                    data.size(),
                    successCount,
                    errors.size()
            );

            // Если были ошибки, добавляем их
            errors.forEach(fileMetadata::addProcessingError);

            // Обновляем статус файла
            if (errors.isEmpty()) {
                fileMetadata.updateStatus(FileStatus.COMPLETED, null);
            } else {
                fileMetadata.updateStatus(FileStatus.ERROR,
                        "Обработано с ошибками: " + errors.size() + " из " + data.size());
            }

            log.info("Завершение обработки файла {}. Успешно: {}, Ошибок: {}",
                    fileMetadata.getOriginalFilename(), successCount, errors.size());

        } catch (Exception e) {
            log.error("Ошибка при обработке файла: {}", e.getMessage(), e);
            fileMetadata.updateStatus(FileStatus.ERROR, e.getMessage());
        }
    }

    @Override
    public void afterProcessing(FileMetadata metadata) {
        // Дополнительные действия после обработки
        if (FileStatus.COMPLETED.equals(metadata.getStatus())) {
            log.info("Пост-обработка файла {}", metadata.getOriginalFilename());
            // Здесь можно добавить специфичную логику
        }
    }

    @Override
    public boolean supports(Client client) {
        return client != null && client.getId().equals(1L);
    }

    private void validateRequiredFields(Map<String, String> row, int rowNumber, List<String> errors) {
        if (!row.containsKey("competitorName")) {
            errors.add(String.format("Строка %d: отсутствует обязательное поле 'competitorName'", rowNumber));
        }
    }

    private void validateDataFormats(Map<String, String> row, int rowNumber, List<String> errors) {
        // Проверка форматов конкретных полей
    }

    private Map<String, String> processRow(Map<String, String> row) {
        // Очистка данных о конкурентах
        String competitorName = row.get("competitorName");
        if (competitorName != null && EXCLUDED_COMPETITORS.contains(competitorName.toLowerCase())) {
            row.put("competitorName", "");
            log.debug("Очищено значение конкурента: {}", competitorName);
        }

        return row;
    }
}
