package by.zoomos_v2.service.directory;


import by.zoomos_v2.model.RetailNetworkDirectory;
import by.zoomos_v2.repository.RetailNetworkDirectoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Сервис для управления справочником розничных сетей.
 * Обеспечивает загрузку, обновление и получение данных справочника.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetailNetworkDirectoryService {
    private final RetailNetworkDirectoryRepository directoryRepository;
    private static final int BATCH_SIZE = 50;

    /**
     * Получает мапу записей справочника по списку кодов
     *
     * @param retailCodes список кодов розничных сетей
     * @return мапа код -> запись справочника
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "retailDirectoryMap", key = "#retailCodes.hashCode()")
    public Map<String, RetailNetworkDirectory> getDirectoryMap(List<String> retailCodes) {
        log.debug("Загрузка записей справочника для {} кодов", retailCodes.size());
        return directoryRepository.findAllByRetailCodeIn(retailCodes).stream()
                .collect(Collectors.toMap(
                        RetailNetworkDirectory::getRetailCode,
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
    }

    /**
     * Обновление справочника новыми данными
     *
     * @param directories список новых записей справочника
     */
    @Transactional
    @CacheEvict(value = "retailDirectoryMap", allEntries = true)
    public void updateDirectory(List<RetailNetworkDirectory> directories) {
        log.info("Начало обновления справочника. Количество записей: {}", directories.size());

        try {
            // Очищаем текущие данные
            directoryRepository.deleteAllRecords();
            log.debug("Существующие записи справочника удалены");

            // Сохраняем новые данные батчами
            for (int i = 0; i < directories.size(); i += BATCH_SIZE) {
                int endIndex = Math.min(i + BATCH_SIZE, directories.size());
                List<RetailNetworkDirectory> batch = directories.subList(i, endIndex);
                directoryRepository.saveAll(batch);
                log.debug("Сохранен батч записей {}-{}", i, endIndex);
            }

            log.info("Обновление справочника успешно завершено");
        } catch (Exception e) {
            log.error("Ошибка при обновлении справочника: {}", e.getMessage());
            throw new RuntimeException("Ошибка обновления справочника", e);
        }
    }

    /**
     * Проверка существования записи в справочнике
     *
     * @param retailCode код розничной сети
     * @return true если запись существует
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "retailDirectoryExists", key = "#retailCode")
    public boolean exists(String retailCode) {
        return directoryRepository.existsByRetailCode(retailCode);
    }
}