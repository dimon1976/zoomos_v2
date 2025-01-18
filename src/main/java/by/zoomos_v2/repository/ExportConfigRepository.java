package by.zoomos_v2.repository;

import by.zoomos_v2.model.ExportConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExportConfigRepository extends JpaRepository<ExportConfig, Long> {

    // Найти все маппинги по ID клиента
    List<ExportConfig> findByClientId(Long clientId);

    Optional<ExportConfig> findByClientIdAndIsDefaultTrue(Long clientId);

    Optional<ExportConfig> findById(Long configId);

}
