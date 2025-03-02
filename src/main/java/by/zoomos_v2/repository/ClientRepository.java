package by.zoomos_v2.repository;

import by.zoomos_v2.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    Optional<Client> findByName(String name);

    /**
     * Получение всех ID клиентов
     * Используется оптимизированный запрос, возвращающий только ID,
     * что значительно эффективнее получения всех объектов Client
     *
     * @return список ID всех клиентов
     */
    @Query("SELECT c.id FROM Client c WHERE c.active = true")
    List<Long> findAllIds();
}
