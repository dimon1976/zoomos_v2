package by.zoomos_v2.repository;

import by.zoomos_v2.model.entity.Product;
import by.zoomos_v2.model.enums.DataSourceType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;

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

    /**
     * Находит продукты по типу источника и номеру задания с пагинацией
     *
     * @param dataSource тип источника данных
     * @param taskNumber номер задания
     * @param pageable параметры пагинации
     * @return список продуктов
     */
    List<Product> findByDataSourceAndProductAdditional1(DataSourceType dataSource,
                                                        String taskNumber,
                                                        Pageable pageable);

    /**
     * Получает уникальные ключи валидации для задания
     *
     * @param dataSource тип источника данных
     * @param taskNumber номер задания
     * @return множество ключей валидации
     */
    @Query("SELECT UPPER(CONCAT(p.productId, '_', p.productCategory1, '_', cd.competitorAdditional)) " +
            "FROM Product p " +
            "JOIN p.competitorDataList cd " +
            "WHERE p.dataSource = :dataSource " +
            "AND p.productAdditional1 = :taskNumber")
    Set<String> findValidationKeysByTaskNumber(@Param("dataSource") DataSourceType dataSource,
                                               @Param("taskNumber") String taskNumber);

    /**
     * Подсчитывает количество записей для задания/отчета
     *
     * @param dataSource тип источника данных
     * @param taskNumber номер задания
     * @return количество записей
     */
    long countByDataSourceAndProductAdditional1(DataSourceType dataSource, String taskNumber);

    /**
     * Проверяет существование продукта с указанными параметрами
     */
    @Query("SELECT COUNT(p) > 0 FROM Product p " +
            "JOIN p.competitorDataList cd " +
            "WHERE p.productId = :productId " +
            "AND p.productCategory1 = :category " +
            "AND cd.competitorAdditional = :retailCode " +
            "AND p.productAdditional1 = :taskNumber " +
            "AND p.dataSource = :dataSource")
    boolean existsByProductAndTaskCriteria(@Param("productId") String productId,
                                           @Param("category") String category,
                                           @Param("retailCode") String retailCode,
                                           @Param("taskNumber") String taskNumber,
                                           @Param("dataSource") DataSourceType dataSource);
}
