package by.zoomos_v2.repository;

import by.zoomos_v2.model.RegionData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RegionDataRepository extends JpaRepository<RegionData, Long> {
}
