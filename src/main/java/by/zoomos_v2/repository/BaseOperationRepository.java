package by.zoomos_v2.repository;

import by.zoomos_v2.model.enums.OperationStatus;
import by.zoomos_v2.model.operation.BaseOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.Optional;

@NoRepositoryBean
public interface BaseOperationRepository<T extends BaseOperation> extends JpaRepository<T, Long> {
    List<T> findByClientId(Long clientId);
    List<T> findByClientIdAndStatusOrderByStartTimeDesc(Long clientId, OperationStatus status);
    Optional<T> findByIdAndClientId(Long id, Long clientId);
}
