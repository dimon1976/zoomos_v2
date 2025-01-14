package by.zoomos_v2.service;

import by.zoomos_v2.mapping.ClientMappingConfig;
import by.zoomos_v2.repository.ClientMappingConfigRepository;
import by.zoomos_v2.aspect.LogExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Сервис для управления конфигурациями маппинга данных.
 * Обеспечивает бизнес-логику работы с настройками маппинга для магазинов.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MappingConfigService {

    private final ClientMappingConfigRepository mappingRepository;

    /**
     * Получает все маппинги для конкретного магазина
     */
    @Transactional(readOnly = true)
    public List<ClientMappingConfig> getMappingsForClient(Long clientId) {
        log.debug("Получение маппингов для магазина с ID: {}", clientId);
        return mappingRepository.findByClientId(clientId);
    }

    /**
     * Получает конкретный маппинг по ID
     */
    @Transactional(readOnly = true)
    public ClientMappingConfig getMappingById(Long id) {
        log.debug("Получение маппинга по ID: {}", id);
        return mappingRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Маппинг с ID {} не найден", id);
                    return new EntityNotFoundException("Настройки маппинга не найдены");
                });
    }

    /**
     * Создает новый маппинг
     */
    @Transactional
    @LogExecution("Создание маппинга")
    public ClientMappingConfig createMapping(ClientMappingConfig mapping) {
        log.debug("Создание нового маппинга для магазина: {}", mapping.getClientId());
        validateMapping(mapping);

        mapping.setId(null);
        mapping.setCreatedAt(LocalDateTime.now());
        mapping.setUpdatedAt(LocalDateTime.now());

        try {
            ClientMappingConfig savedMapping = mappingRepository.save(mapping);
            log.info("Создан новый маппинг с ID: {}", savedMapping.getId());
            return savedMapping;
        } catch (Exception e) {
            log.error("Ошибка при создании маппинга: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка при создании настроек маппинга", e);
        }
    }

    /**
     * Обновляет существующий маппинг
     */
    @Transactional
    @LogExecution("Обновление маппинга")
    public ClientMappingConfig updateMapping(ClientMappingConfig mapping) {
        log.debug("Обновление маппинга с ID: {}", mapping.getId());
        validateMapping(mapping);

        if (!mappingRepository.existsById(mapping.getId())) {
            log.error("Попытка обновить несуществующий маппинг с ID: {}", mapping.getId());
            throw new EntityNotFoundException("Настройки маппинга не найдены");
        }

        mapping.setUpdatedAt(LocalDateTime.now());

        try {
            ClientMappingConfig updatedMapping = mappingRepository.save(mapping);
            log.info("Маппинг с ID: {} успешно обновлен", mapping.getId());
            return updatedMapping;
        } catch (Exception e) {
            log.error("Ошибка при обновлении маппинга: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка при обновлении настроек маппинга", e);
        }
    }

    /**
     * Удаляет маппинг
     */
    @Transactional
    @LogExecution("Удаление маппинга")
    public void deleteMapping(Long id) {
        log.debug("Удаление маппинга с ID: {}", id);
        if (!mappingRepository.existsById(id)) {
            log.error("Попытка удалить несуществующий маппинг с ID: {}", id);
            throw new EntityNotFoundException("Настройки маппинга не найдены");
        }

        try {
            mappingRepository.deleteById(id);
            log.info("Маппинг с ID: {} успешно удален", id);
        } catch (Exception e) {
            log.error("Ошибка при удалении маппинга: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка при удалении настроек маппинга", e);
        }
    }

    /**
     * Валидирует данные маппинга
     */
    private void validateMapping(ClientMappingConfig mapping) {
        if (mapping == null) {
            throw new IllegalArgumentException("Маппинг не может быть null");
        }
        if (mapping.getClientId() == null) {
            throw new IllegalArgumentException("ID магазина не может быть null");
        }
        if (mapping.getName() == null || mapping.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Название маппинга не может быть пустым");
        }
        if (mapping.getColumnsConfig() == null || mapping.getColumnsConfig().trim().isEmpty()) {
            throw new IllegalArgumentException("Конфигурация колонок не может быть пустой");
        }
        // Можно добавить дополнительные проверки по необходимости
    }
}