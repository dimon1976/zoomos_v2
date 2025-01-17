package by.zoomos_v2.repository;
import by.zoomos_v2.model.ExportConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExportConfigRepository extends JpaRepository<ExportConfig, Long>{
    Optional<ExportConfig> findByClientIdAndIsDefaultTrue(Long clientId);
}
