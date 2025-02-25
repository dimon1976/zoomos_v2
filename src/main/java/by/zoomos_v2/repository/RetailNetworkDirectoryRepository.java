package by.zoomos_v2.repository;

import by.zoomos_v2.model.RetailNetworkDirectory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы со справочником розничных сетей.
 * Предоставляет методы для поиска, обновления и управления данными справочника.
 */
@Repository
public interface RetailNetworkDirectoryRepository extends JpaRepository<RetailNetworkDirectory, Long> {

    /**
     * Поиск записи справочника по коду розничной сети
     */
    Optional<RetailNetworkDirectory> findByRetailCode(String retailCode);

    /**
     * Поиск всех записей по списку кодов розничных сетей
     */
    List<RetailNetworkDirectory> findAllByRetailCodeIn(List<String> retailCodes);

    /**
     * Проверка существования записи по коду розничной сети
     */
    boolean existsByRetailCode(String retailCode);

    /**
     * Удаление всех записей справочника
     */
    @Modifying
    @Query("DELETE FROM RetailNetworkDirectory")
    void deleteAllRecords();

    /**
     * Поиск записей по коду региона
     */
    List<RetailNetworkDirectory> findAllByRegionCode(String regionCode);
}