package by.zoomos_v2.repository;

import by.zoomos_v2.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
    @Query("SELECT DISTINCT p FROM Product p " +
            "LEFT JOIN FETCH p.regionDataList r " +
            "LEFT JOIN FETCH p.competitorDataList c " +
            "WHERE p.fileId = :fileId")
    List<Product> findByFileId(@Param("fileId") Long fileId);
}
