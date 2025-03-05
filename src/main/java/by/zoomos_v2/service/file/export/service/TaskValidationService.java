package by.zoomos_v2.service.file.export.service;

import by.zoomos_v2.model.enums.DataSourceType;
import by.zoomos_v2.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Оптимизированный сервис для валидации и работы с заданиями.
 * Обеспечивает эффективную загрузку и проверку данных задания.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskValidationService {

    private final ProductRepository productRepository;

    // Кэш для хранения ключей валидации по номеру задания
    private final ConcurrentHashMap<String, Set<String>> taskValidationCache = new ConcurrentHashMap<>();

    // Размер батча для загрузки ключей валидации
    private static final int BATCH_SIZE = 10000;

    // Максимальное количество параллельных потоков
    private static final int MAX_THREADS = Runtime.getRuntime().availableProcessors();

    /**
     * Создает набор ключей валидации для указанного задания.
     * Ключ формируется в формате: productId_category_retailCode
     *
     * @param taskNumber номер задания
     * @return множество ключей валидации
     */
    @Transactional(readOnly = true)
    public Set<String> createValidationKeysForTask(String taskNumber) {
        // Проверяем наличие в кэше
        if (taskValidationCache.containsKey(taskNumber)) {
            Set<String> cachedKeys = taskValidationCache.get(taskNumber);
            if (cachedKeys != null && !cachedKeys.isEmpty()) {
                log.debug("Используем кэшированные ключи валидации для задания {}: {} ключей",
                        taskNumber, cachedKeys.size());
                return cachedKeys;
            }
            // Удаляем пустой или null кэш
            taskValidationCache.remove(taskNumber);
        }

        log.info("Создание ключей валидации для задания {}. Начало выполнения...", taskNumber);
        long startTime = System.currentTimeMillis();

        try {
            // Подсчитываем общее количество записей
            long totalRecords = productRepository.countByDataSourceAndProductAdditional1(
                    DataSourceType.TASK, taskNumber);

            log.info("Найдено {} записей для задания {}. Начинаем загрузку...", totalRecords, taskNumber);

            // Для небольших заданий используем прямой запрос
            if (totalRecords < BATCH_SIZE) {
                return loadKeysInSingleQuery(taskNumber);
            }

            // Для больших заданий применяем батчевую загрузку
            return loadKeysInBatches(taskNumber, totalRecords);

        } catch (Exception e) {
            log.error("Ошибка при создании ключей валидации для задания {}: {}", taskNumber, e.getMessage(), e);
            return new HashSet<>();
        } finally {
            log.info("Создание ключей валидации для задания {} завершено за {} мс",
                    taskNumber, System.currentTimeMillis() - startTime);
        }
    }

    /**
     * Загружает ключи валидации одним запросом.
     * Подходит для небольших заданий.
     */
    private Set<String> loadKeysInSingleQuery(String taskNumber) {
        long startTime = System.currentTimeMillis();

        try {
            Set<String> validationKeys = productRepository.findValidationKeysByTaskNumberOptimized(
                    DataSourceType.TASK.name(), taskNumber);

            if (validationKeys == null) {
                log.warn("Repository вернул null вместо пустого набора для задания {}", taskNumber);
                validationKeys = new HashSet<>();
            }

            log.info("Загружено {} ключей валидации одним запросом за {} мс",
                    validationKeys.size(), System.currentTimeMillis() - startTime);

            // Кэшируем только непустой результат
            if (!validationKeys.isEmpty()) {
                taskValidationCache.put(taskNumber, validationKeys);
            }

            return validationKeys;
        } catch (Exception e) {
            log.error("Ошибка при выполнении loadKeysInSingleQuery для задания {}: {}",
                    taskNumber, e.getMessage(), e);
            return new HashSet<>();
        }
    }

    /**
     * Загружает ключи валидации батчами с использованием параллельных потоков.
     * Подходит для больших заданий.
     */
    private Set<String> loadKeysInBatches(String taskNumber, long totalRecords) {
        long startTime = System.currentTimeMillis();

        try {
            // Рассчитываем количество батчей
            int totalBatches = (int) Math.ceil((double) totalRecords / BATCH_SIZE);
            log.info("Задание будет загружено {} батчами по {} записей", totalBatches, BATCH_SIZE);

            // Создаем потокобезопасный набор для результатов
            Set<String> validationKeys = ConcurrentHashMap.newKeySet(Math.toIntExact(totalRecords));

            // Параллельная загрузка батчами
            ExecutorService executor = Executors.newFixedThreadPool(MAX_THREADS);

            try {
                for (int batch = 0; batch < totalBatches; batch++) {
                    final int currentBatch = batch;

                    executor.submit(() -> {
                        int offset = currentBatch * BATCH_SIZE;
                        log.debug("Загрузка батча {}/{} (смещение: {})", currentBatch + 1, totalBatches, offset);

                        try {
                            Set<String> batchKeys = productRepository.findValidationKeysByTaskNumberPaginated(
                                    DataSourceType.TASK.name(), taskNumber, BATCH_SIZE, offset);

                            if (batchKeys != null) {
                                validationKeys.addAll(batchKeys);
                                log.debug("Батч {}/{} загружен, добавлено {} ключей, всего: {}",
                                        currentBatch + 1, totalBatches, batchKeys.size(), validationKeys.size());
                            } else {
                                log.warn("Batch {}/{} вернул null вместо пустого набора",
                                        currentBatch + 1, totalBatches);
                            }
                        } catch (Exception e) {
                            log.error("Ошибка при загрузке батча {}/{} для задания {}: {}",
                                    currentBatch + 1, totalBatches, taskNumber, e.getMessage(), e);
                        }
                    });
                }

                // Корректно завершаем ExecutorService и ждем выполнения всех задач
                executor.shutdown();
                if (!executor.awaitTermination(30, TimeUnit.MINUTES)) {
                    log.warn("Превышено время ожидания загрузки ключей валидации. Принудительное завершение.");
                    executor.shutdownNow();
                }

                log.info("Загружено {} ключей валидации батчами за {} мс",
                        validationKeys.size(), System.currentTimeMillis() - startTime);

                // Кэшируем только непустой результат
                if (!validationKeys.isEmpty()) {
                    taskValidationCache.put(taskNumber, validationKeys);
                }

                return validationKeys;

            } catch (Exception e) {
                log.error("Ошибка при батчевой загрузке ключей валидации: {}", e.getMessage(), e);
                executor.shutdownNow();
                return validationKeys; // Возвращаем то, что успели загрузить
            }
        } catch (Exception e) {
            log.error("Критическая ошибка в loadKeysInBatches для задания {}: {}",
                    taskNumber, e.getMessage(), e);
            return new HashSet<>();
        }
    }

    /**
     * Очищает кэш ключей валидации для указанного задания.
     * Используется при обновлении данных задания.
     *
     * @param taskNumber номер задания
     */
    public void clearValidationCache(String taskNumber) {
        log.debug("Очистка кэша ключей валидации для задания {}", taskNumber);
        taskValidationCache.remove(taskNumber);
    }

    /**
     * Очищает весь кэш ключей валидации.
     */
    public void clearAllValidationCache() {
        log.debug("Очистка всего кэша ключей валидации");
        taskValidationCache.clear();
    }

    /**
     * Получает количество товаров в задании
     *
     * @param taskNumber номер задания
     * @return количество товаров
     */
    @Transactional(readOnly = true)
    public long getTaskProductsCount(String taskNumber) {
        return productRepository.countByDataSourceAndProductAdditional1(
                DataSourceType.TASK,
                taskNumber
        );
    }
}