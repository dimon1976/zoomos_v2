package by.zoomos_v2.service.file.export.strategy;

import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.model.RetailNetworkDirectory;
import by.zoomos_v2.service.directory.RetailNetworkDirectoryService;
import by.zoomos_v2.service.file.BatchProcessingData;
import by.zoomos_v2.service.file.export.service.TaskValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
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
            // 1. Получаем ключи валидации из задания
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
                    futures.add(executor.submit(() -> processBatch(batch, validationKeysSet, directoryMap)));
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
     * @return обработанные данные
     */
    private List<Map<String, Object>> processBatch(List<Map<String, Object>> batch,
                                                   Set<String> validationKeys,
                                                   Map<String, RetailNetworkDirectory> directoryMap) {
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> row : batch) {
            // Формируем ключ валидации
            String validationKey = createValidationKey(row);

            // Проверяем наличие в задании (быстрая проверка в HashSet)
            if (validationKeys.contains(validationKey)) {
                // Обогащаем данными из справочника
                enrichRowWithDirectory(row, directoryMap);
                result.add(row);
            }
        }

        return result;
    }

    /**
     * Создает ключ валидации из строки данных
     */
    private String createValidationKey(Map<String, Object> row) {
        return String.format("%s_%s_%s",
                toString(row.get("product.productId")).toUpperCase(),
                toString(row.get("product.productCategory1")).toUpperCase(),
                toString(row.get("competitordata.competitorAdditional")).toUpperCase());
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
        validateParameters(exportConfig.getParams());
        return exportConfig.getParam("taskNumber");
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