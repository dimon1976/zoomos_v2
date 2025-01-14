package by.zoomos_v2.service;

import by.zoomos_v2.mapping.ClientMappingConfig;
import by.zoomos_v2.repository.ClientMappingConfigRepository;
import by.zoomos_v2.exception.FileProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Сервис для управления конфигурациями маппинга.
 * Обеспечивает создание, получение, обновление и удаление
 * конфигураций маппинга для магазинов.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MappingConfigService {

    private final ClientMappingConfigRepository mappingRepository;

    /**
     * Получает все конфигурации маппинга для магазина
     *
     * @param clientId идентификатор магазина
     * @return список конфигураций маппинга
     */
    @Transactional(readOnly = true)
    public List<ClientMappingConfig> getMappingsForClient(Long clientId) {
        log.debug("Получение конфигураций маппинга для магазина: {}", clientId);
        return mappingRepository.findByClientId(clientId);
    }

    /**
     * Получает конфигурацию маппинга по ID
     *
     * @param id идентификатор конфигурации
     * @return конфигурация маппинга
     * @throws FileProcessingException если конфигурация не найдена
     */
    @Transactional(readOnly = true)
    public ClientMappingConfig getMappingById(Long id) {
        log.debug("Получение конфигурации маппинга по ID: {}", id);
        return mappingRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Конфигурация маппинга с ID {} не найдена", id);
                    return new FileProcessingException("Конфигурация маппинга не найдена");
                });
    }

    /**
     * Создает новую конфигурацию маппинга
     *
     * @param mapping новая конфигурация
     * @return сохраненная конфигурация
     */
    @Transactional
    public ClientMappingConfig createMapping(ClientMappingConfig mapping) {
        log.debug("Создание новой конфигурации маппинга для магазина: {}", mapping.getClientId());
        validateMapping(mapping);
        mapping.setId(null); // Гарантируем создание новой записи
        return mappingRepository.save(mapping);
    }

    /**
     * Обновляет существующую конфигурацию маппинга
     *
     * @param mapping обновленная конфигурация
     * @return обновленная конфигурация
     * @throws FileProcessingException если конфигурация не найдена
     */
    @Transactional
    public ClientMappingConfig updateMapping(ClientMappingConfig mapping) {
        log.debug("Обновление конфигурации маппинга с ID: {}", mapping.getId());
        validateMapping(mapping);

        if (!mappingRepository.existsById(mapping.getId())) {
            throw new FileProcessingException("Конфигурация маппинга не найдена");
        }

        return mappingRepository.save(mapping);
    }

    /**
     * Удаляет конфигурацию маппинга
     *
     * @param id идентификатор конфигурации
     * @throws FileProcessingException если конфигурация не найдена
     */
    @Transactional
    public void deleteMapping(Long id) {
        log.debug("Удаление конфигурации маппинга с ID: {}", id);
        if (!mappingRepository.existsById(id)) {
            throw new FileProcessingException("Конфигурация маппинга не найдена");
        }
        mappingRepository.deleteById(id);
    }

    /**
     * Проверяет базовую валидность конфигурации маппинга
     *
     * @param mapping конфигурация для проверки
     * @throws FileProcessingException если конфигурация невалидна
     */
    private void validateMapping(ClientMappingConfig mapping) {
        if (mapping == null) {
            throw new FileProcessingException("Конфигурация маппинга не может быть null");
        }
        if (mapping.getClientId() == null) {
            throw new FileProcessingException("ID магазина не может быть null");
        }
        if (mapping.getName() == null || mapping.getName().trim().isEmpty()) {
            throw new FileProcessingException("Название конфигурации не может быть пустым");
        }
        if (mapping.getColumnsConfig() == null || mapping.getColumnsConfig().trim().isEmpty()) {
            throw new FileProcessingException("Конфигурация колонок не может быть пустой");
        }
    }
}