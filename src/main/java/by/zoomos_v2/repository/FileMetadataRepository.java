package by.zoomos_v2.repository;

import by.zoomos_v2.model.FileMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {
    /**
     * Находит все файлы магазина, отсортированные по дате загрузки (сначала новые)
     */
    List<FileMetadata> findByShopIdOrderByUploadedAtDesc(Long shopId);

    /**
     * Находит все файлы магазина с указанным статусом
     */
    List<FileMetadata> findByShopIdAndStatus(Long shopId, String status);
}
