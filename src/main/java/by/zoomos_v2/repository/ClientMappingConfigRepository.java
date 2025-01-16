package by.zoomos_v2.repository;

import by.zoomos_v2.mapping.ClientMappingConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientMappingConfigRepository extends JpaRepository<ClientMappingConfig, Long> {

    // Найти все маппинги по ID клиента
    List<ClientMappingConfig> findByClientId(Long clientId);

    // Метод для получения конфигурации по ID
    Optional<ClientMappingConfig> findById(Long id);

    // Или по имени клиента
//    List<ClientMappingConfig> findByClientName(String clientName);
}
