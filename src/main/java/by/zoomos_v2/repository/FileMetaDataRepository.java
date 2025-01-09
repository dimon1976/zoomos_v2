package by.zoomos_v2.repository;

import by.zoomos_v2.model.FileMetaData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
@Repository
public interface FileMetaDataRepository extends JpaRepository<FileMetaData, Long> {
    Optional<FileMetaData> findByFileHash(String fileHash);
}
