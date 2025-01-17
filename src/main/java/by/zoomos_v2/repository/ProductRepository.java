package by.zoomos_v2.repository;

import by.zoomos_v2.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    // Поиск всех продуктов по productId
    List<Product> findByProductId(String productId);

    /**
     * Получает все данные по файлу в виде Map
     * @param fileId идентификатор файла
     * @return список записей в виде Map
     */
    @Query("""
        SELECT 
            p.productName AS productName,
            p.productBrand AS productBrand,
            c.competitorName AS competitorName
        FROM Product p
        LEFT JOIN p.regionDataList r
        LEFT JOIN p.competitorDataList c
        WHERE p.fileId = :fileId
        """)
    List<Map<String, String>> findAllByFileId(@Param("fileId") Long fileId);
}
