package by.zoomos_v2.repository;

import by.zoomos_v2.model.operation.ImportOperation;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImportOperationRepository extends BaseOperationRepository<ImportOperation> {

    List<ImportOperation> findByClientIdOrderByStartTimeDesc(Long clientId);
}
