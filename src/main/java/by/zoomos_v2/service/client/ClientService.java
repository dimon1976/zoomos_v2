package by.zoomos_v2.service.client;

import by.zoomos_v2.model.Client;
import by.zoomos_v2.repository.ClientRepository;
import by.zoomos_v2.aspect.LogExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;

/**
 * Сервис для работы с клиентами.
 * Реализует бизнес-логику управления данными клиентов.
 */
@Slf4j
@Service
public class ClientService {

    private final ClientRepository clientRepository;

    public ClientService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    /**
     * Получает список всех клиентов
     * @return список клиентов
     */
    @Transactional(readOnly = true)
    public List<Client> getAllClients() {
        log.debug("Получение списка всех клиентов");
        return clientRepository.findAll();
    }

    /**
     * Получает клиента по идентификатору
     * @param id идентификатор клиента
     * @return объект клиента
     * @throws EntityNotFoundException если клиент не найден
     */
    @Transactional(readOnly = true)
    public Client getClientById(Long id) {
        log.debug("Получение клиента по ID: {}", id);
        return clientRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Клиент с ID {} не найден", id);
                    return new EntityNotFoundException("Клиент не найден");
                });
    }

    /**
     * Обновляет данные клиента
     * @param client объект клиента с обновленными данными
     * @return обновленный объект клиента
     */
    @Transactional
    @LogExecution("Обновление данных клиента")
    public Client updateClient(Client client) {
        log.debug("Обновление данных клиента с ID: {}", client.getId());
        if (client.getId() == null) {
            log.warn("Попытка обновить клиента без ID");
            throw new IllegalArgumentException("ID клиента не может быть null");
        }
        return clientRepository.save(client);
    }

    /**
     * Создает нового клиента
     * @param client объект нового клиента
     * @return созданный объект клиента
     */
    @Transactional
    @LogExecution("Создание нового клиента")
    public Client createClient(Client client) {
        log.debug("Создание нового клиента: {}", client);
        client.setId(null); // Убеждаемся, что создается новый клиент
        return clientRepository.save(client);
    }

    /**
     * Удаляет клиента
     * @param id идентификатор клиента
     * @throws EntityNotFoundException если клиент не найден
     */
    @Transactional
    @LogExecution("Удаление клиента")
    public void deleteClient(Long id) {
        log.debug("Удаление клиента с ID: {}", id);
        if (!clientRepository.existsById(id)) {
            log.error("Попытка удалить несуществующего клиента с ID: {}", id);
            throw new EntityNotFoundException("Клиент не найден");
        }
        clientRepository.deleteById(id);
        log.info("Клиент с ID: {} успешно удален", id);
    }
}
