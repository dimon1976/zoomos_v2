package by.zoomos_v2.repository;

import by.zoomos_v2.model.operation.ImportOperation;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImportOperationRepository extends BaseOperationRepository<ImportOperation> {

    List<ImportOperation> findByClientIdOrderByStartTimeDesc(Long clientId);
    List<ImportOperation> findByClientId(Long clientId);

    /**
     * Находит последнюю операцию импорта по идентификатору источника
     * @param sourceIdentifier идентификатор источника (имя файла)
     * @return операция импорта или null
     */
    ImportOperation findFirstBySourceIdentifierOrderByStartTimeDesc(String sourceIdentifier);

    /**
     * Находит последнюю операцию импорта по идентификатору источника и клиенту
     * Использует более эффективный JPQL запрос
     */
    @Query("SELECT io FROM ImportOperation io " +
            "WHERE io.sourceIdentifier = :sourceIdentifier " +
            "AND io.clientId = :clientId " +
            "ORDER BY io.startTime DESC " +
            "LIMIT 1")
    ImportOperation findLastOperationBySourceAndClient(
            @Param("sourceIdentifier") String sourceIdentifier,
            @Param("clientId") Long clientId
    );
}
