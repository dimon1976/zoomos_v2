package by.zoomos_v2.repository;

import by.zoomos_v2.model.ClientProcessingStrategy;
import by.zoomos_v2.service.file.export.strategy.ProcessingStrategyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientProcessingStrategyRepository extends JpaRepository<ClientProcessingStrategy, Long> {
    List<ClientProcessingStrategy> findByClientIdAndIsActiveTrue(Long clientId);

    Optional<ClientProcessingStrategy> findByClientIdAndStrategyTypeAndIsActiveTrue(
            Long clientId,
            ProcessingStrategyType strategyType
    );
}
