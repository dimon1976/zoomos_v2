package by.zoomos_v2.repository;

import by.zoomos_v2.model.entity.ReferenceData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReferenceDataRepository extends JpaRepository<ReferenceData, Long> {

    Optional<ReferenceData> findByClientIdAndTaskNumberAndIsActiveTrue(Long clientId, String taskNumber);


    @Query("UPDATE ReferenceData r SET r.isActive = false WHERE r.clientId = :clientId AND r.taskNumber = :taskNumber")
    void deactivateExisting(@Param("clientId") Long clientId, @Param("taskNumber") String taskNumber);
}
