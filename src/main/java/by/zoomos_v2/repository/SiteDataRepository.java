package by.zoomos_v2.repository;

import by.zoomos_v2.model.CompetitorData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SiteDataRepository extends JpaRepository<CompetitorData, Long> {
}
