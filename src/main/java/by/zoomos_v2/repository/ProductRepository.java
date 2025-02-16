package by.zoomos_v2.repository;

import by.zoomos_v2.model.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    /**
     * Находит все продукты, связанные с определенным файлом
     * Использует JOIN FETCH для загрузки связанных данных регионов и конкурентов
     *
     * @param fileId идентификатор файла
     * @return список продуктов со всеми связанными данными
     */
    @Query("SELECT DISTINCT p FROM Product p WHERE p.fileId = :fileId")
    List<Product> findByFileId(@Param("fileId") Long fileId);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.regionDataList WHERE p.id IN :productIds")
    List<Product> findByIdInWithRegionData(@Param("productIds") Collection<Long> productIds);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.competitorDataList WHERE p.id IN :productIds")
    List<Product> findByIdInWithCompetitorData(@Param("productIds") Collection<Long> productIds);

    @Query("SELECT DISTINCT p.productId FROM Product p WHERE p.productAdditional1 = :taskNumber")
    List<String> findProductIdsByTaskNumber(@Param("taskNumber") String taskNumber);
}
