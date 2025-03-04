package by.zoomos_v2.service.file.export.strategy;

import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.model.RetailNetworkDirectory;
import by.zoomos_v2.service.directory.RetailNetworkDirectoryService;
import by.zoomos_v2.service.file.BatchProcessingData;
import by.zoomos_v2.service.file.export.service.TaskValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Оптимизированная стратегия обработки данных на основе задания.
 * Фильтрует данные по заданию и обогащает их справочной информацией.
 * Реализует батчевую и параллельную обработку для повышения производительности.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskBasedProcessingStrategy implements DataProcessingStrategy {

    private final TaskValidationService taskValidationService;
    private final RetailNetworkDirectoryService directoryService;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    // Размер батча для обработки данных
    private static final int BATCH_SIZE = 5000;

    // Максимальное количество параллельных потоков
    private static final int MAX_THREADS = Runtime.getRuntime().availableProcessors();

    @Override
    public List<Map<String, Object>> processData(List<Map<String, Object>> data,
                                                 ExportConfig exportConfig,
                                                 BatchProcessingData batchProcessingData) {
        String taskNumber = getTaskNumber(exportConfig);
        log.info("Начало оптимизированной обработки данных по заданию {}. Количество записей: {}",
                taskNumber, data.size());

        try {
            // 1. Получаем ключи валидации из задания (теперь только competitorAdditional)
            long startTime = System.currentTimeMillis();
            Set<String> validationKeys = taskValidationService.createValidationKeysForTask(taskNumber);
            log.info("Получено {} ключей валидации из задания за {} мс",
                    validationKeys.size(), System.currentTimeMillis() - startTime);

            // Преобразуем в HashSet для быстрого поиска
            Set<String> validationKeysSet = new HashSet<>(validationKeys);

            // 2. Собираем все коды розничных сетей для оптимизации запросов к справочнику
            startTime = System.currentTimeMillis();
            Set<String> retailCodes = data.stream()
                    .map(row -> (String) row.get("competitordata.competitorAdditional"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            // 3. Загружаем справочник (это кэшируется в сервисе)
            Map<String, RetailNetworkDirectory> directoryMap = directoryService.getDirectoryMap(
                    new ArrayList<>(retailCodes)
            );
            log.info("Загружен справочник с {} записями за {} мс",
                    directoryMap.size(), System.currentTimeMillis() - startTime);

            // Получаем максимальную дату из параметров (если задана)
            LocalDate maxDate = getMaxDateFromParams(exportConfig);
            if (maxDate != null) {
                log.info("Установлена максимальная дата: {}", maxDate);
            }

            // 4. Разбиваем данные на батчи для параллельной обработки
            List<List<Map<String, Object>>> batches = partitionData(data, BATCH_SIZE);
            log.info("Данные разбиты на {} батчей по {} записей", batches.size(), BATCH_SIZE);

            // 5. Создаем ExecutorService для параллельной обработки
            ExecutorService executor = Executors.newFixedThreadPool(MAX_THREADS);

            try {
                // 6. Обрабатываем батчи параллельно
                startTime = System.currentTimeMillis();
                List<Future<List<Map<String, Object>>>> futures = new ArrayList<>();

                for (List<Map<String, Object>> batch : batches) {
                    futures.add(executor.submit(() ->
                            processBatch(batch, validationKeysSet, directoryMap, maxDate)));
                }

                // 7. Собираем результаты
                List<Map<String, Object>> processedData = new ArrayList<>();
                for (Future<List<Map<String, Object>>> future : futures) {
                    processedData.addAll(future.get());
                }

                log.info("Параллельная обработка завершена за {} мс. Отфильтровано записей: {}/{}",
                        System.currentTimeMillis() - startTime, processedData.size(), data.size());

                // 8. Обновляем статистику
                batchProcessingData.setSuccessCount(processedData.size());

                return processedData;
            } finally {
                // Корректно завершаем ExecutorService
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        } catch (Exception e) {
            log.error("Ошибка при обработке данных: {}", e.getMessage(), e);
            throw new IllegalStateException("Ошибка обработки данных: " + e.getMessage(), e);
        }
    }

    /**
     * Обрабатывает один батч данных
     *
     * @param batch батч данных для обработки
     * @param validationKeys набор ключей валидации
     * @param directoryMap справочник
     * @param maxDate максимальная дата для фильтрации (может быть null)
     * @return обработанные данные
     */
    private List<Map<String, Object>> processBatch(List<Map<String, Object>> batch,
                                                   Set<String> validationKeys,
                                                   Map<String, RetailNetworkDirectory> directoryMap,
                                                   LocalDate maxDate) {
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> row : batch) {
            // Формируем ключ валидации - ТОЛЬКО по competitorAdditional
            String validationKey = createValidationKey(row);

            // Проверяем наличие в задании (быстрая проверка в HashSet)
            if (validationKeys.contains(validationKey)) {
                // Обрабатываем дату, если задана максимальная дата
                if (maxDate != null) {
                    processDateForRow(row, maxDate);
                }

                // Обогащаем данными из справочника
                enrichRowWithDirectory(row, directoryMap);
                result.add(row);
            }
        }

        return result;
    }

    /**
     * Создает ключ валидации из строки данных.
     * Используем ТОЛЬКО competitorAdditional в соответствии с новыми требованиями.
     */
    private String createValidationKey(Map<String, Object> row) {
        // Получаем только значение competitorAdditional
        return toString(row.get("competitordata.competitorAdditional")).toUpperCase();
    }

    /**
     * Обрабатывает дату в строке данных.
     * Если дата старше максимальной, заменяем её на максимальную.
     *
     * @param row строка данных
     * @param maxDate максимальная дата
     */
    private void processDateForRow(Map<String, Object> row, LocalDate maxDate) {
        try {
            Object dateObj = row.get("competitordata.competitorDate");
            if (dateObj != null) {
                String dateStr = toString(dateObj);
                if (!dateStr.isEmpty()) {
                    LocalDate date = LocalDate.parse(dateStr, DATE_FORMATTER);

                    // Если дата старше максимальной, заменяем её на максимальную
                    if (date.isAfter(maxDate)) {
                        row.put("competitordata.competitorDate", maxDate.toString());

                        // Если есть поле с LocalDateTime, синхронизируем и его
                        if (row.containsKey("competitordata.competitorLocalDateTime")) {
                            // Заменяем только дату, сохраняя время, если это возможно
                            try {
                                Object dtObj = row.get("competitordata.competitorLocalDateTime");
                                if (dtObj != null) {
                                    if (dtObj instanceof LocalDateTime) {
                                        LocalDateTime dt = (LocalDateTime) dtObj;
                                        row.put("competitordata.competitorLocalDateTime",
                                                maxDate.atTime(dt.toLocalTime()));
                                    }
                                }
                            } catch (Exception e) {
                                log.debug("Не удалось обновить LocalDateTime: {}", e.getMessage());
                            }
                        }
                    }
                }
            }
        } catch (DateTimeParseException e) {
            log.warn("Ошибка при парсинге даты '{}': {}",
                    row.get("competitordata.competitorDate"), e.getMessage());
        } catch (Exception e) {
            log.warn("Ошибка при обработке даты: {}", e.getMessage());
        }
    }

    /**
     * Обогащает строку данными из справочника
     */
    private void enrichRowWithDirectory(Map<String, Object> row,
                                        Map<String, RetailNetworkDirectory> directoryMap) {
        String retailCode = (String) row.get("competitordata.competitorAdditional");
        if (retailCode != null) {
            RetailNetworkDirectory directory = directoryMap.get(retailCode);
            if (directory != null) {
                row.put("competitordata.competitorName", directory.getRetailName());
                row.put("regiondata.region", directory.getRegionName());
                row.put("regiondata.regionAddress", directory.getPhysicalAddress());
            }
        }
    }

    /**
     * Получает максимальную дату из параметров стратегии
     * @param exportConfig конфигурация экспорта
     * @return максимальная дата или null, если не задана
     */
    private LocalDate getMaxDateFromParams(ExportConfig exportConfig) {
        try {
            String maxDateStr = exportConfig.getParam("maxDate");
            if (maxDateStr != null && !maxDateStr.isEmpty()) {
                return LocalDate.parse(maxDateStr, DATE_FORMATTER);
            }
        } catch (Exception e) {
            log.warn("Ошибка при парсинге максимальной даты: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Разбивает данные на батчи заданного размера
     */
    private List<List<Map<String, Object>>> partitionData(List<Map<String, Object>> data, int batchSize) {
        List<List<Map<String, Object>>> batches = new ArrayList<>();
        for (int i = 0; i < data.size(); i += batchSize) {
            batches.add(data.subList(i, Math.min(i + batchSize, data.size())));
        }
        return batches;
    }

    /**
     * Получает номер задания из конфигурации
     */
    private String getTaskNumber(ExportConfig exportConfig) {
        String taskNumber = exportConfig.getParam("taskNumber");
        if (taskNumber == null || taskNumber.isEmpty()) {
            throw new IllegalArgumentException("Не указан номер задания");
        }
        return taskNumber;
    }

    private String toString(Object value) {
        return value != null ? value.toString().trim() : "";
    }

    @Override
    public boolean supports(ExportConfig exportConfig) {
        return ProcessingStrategyType.TASK_BASED_FILTER.equals(exportConfig.getStrategyType());
    }

    @Override
    public ProcessingStrategyType getStrategyType() {
        return ProcessingStrategyType.TASK_BASED_FILTER;
    }

    @Override
    public List<StrategyParameterDescriptor> getParameterDescriptors() {
        return List.of(
                StrategyParameterDescriptor.builder()
                        .key("taskNumber")
                        .displayName("Номер задания")
                        .description("Номер задания для фильтрации данных")
                        .type(ParameterType.STRING)
                        .required(true)
                        .build(),
                StrategyParameterDescriptor.builder()
                        .key("maxDate")
                        .displayName("Максимальная дата")
                        .description("Максимальная дата данных (YYYY-MM-DD)")
                        .type(ParameterType.DATE)
                        .required(false)
                        .build()
        );
    }

    @Override
    public Set<String> getRequiredParameters() {
        return getParameterDescriptors().stream()
                .filter(StrategyParameterDescriptor::isRequired)
                .map(StrategyParameterDescriptor::getKey)
                .collect(Collectors.toSet());
    }
}