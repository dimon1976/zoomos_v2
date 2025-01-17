package by.zoomos_v2.repository;

import by.zoomos_v2.model.FileMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {
    /**
     * Находит все файлы магазина, отсортированные по дате загрузки (сначала новые)
     */
    List<FileMetadata> findByClientIdOrderByUploadedAtDesc(Long clientId);

    /**
     * Находит все файлы магазина с указанным статусом
     */
    List<FileMetadata> findByClientIdAndStatus(Long clientId, String status);

    /**
     * Находит последний успешно обработанный файл клиента
     */
    Optional<FileMetadata> findFirstByClientIdAndStatusOrderByProcessingCompletedAtDesc(
            Long clientId, String status);
}
