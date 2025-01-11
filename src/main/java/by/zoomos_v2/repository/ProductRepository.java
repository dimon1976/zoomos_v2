package by.zoomos_v2.repository;

import by.zoomos_v2.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    // Поиск всех продуктов по productId
    List<Product> findByProductId(String productId);
}
