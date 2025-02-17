package by.zoomos_v2.service.file.export.service;

import by.zoomos_v2.model.enums.DataSourceType;
import by.zoomos_v2.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Сервис для валидации и работы с заданиями.
 * Обеспечивает загрузку и проверку данных задания.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskValidationService {

    private final ProductRepository productRepository;

    /**
     * Создает набор ключей валидации для указанного задания.
     * Ключ формируется в формате: productId_category_retailCode
     *
     * @param taskNumber номер задания
     * @return множество ключей валидации
     */
    @Transactional(readOnly = true)
    public Set<String> createValidationKeysForTask(String taskNumber) {
        log.debug("Создание ключей валидации для задания {}", taskNumber);

        Set<String> validationKeys = productRepository.findValidationKeysByTaskNumber(
                DataSourceType.TASK,
                taskNumber
        );

        log.debug("Создано {} ключей валидации для задания {}", validationKeys.size(), taskNumber);
        return validationKeys;
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