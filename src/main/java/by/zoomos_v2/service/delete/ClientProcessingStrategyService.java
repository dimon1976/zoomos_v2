package by.zoomos_v2.service.delete;

import by.zoomos_v2.model.Client;
import by.zoomos_v2.repository.ClientProcessingStrategyRepository;
import by.zoomos_v2.service.client.ClientService;
import by.zoomos_v2.model.ClientProcessingStrategy;
import by.zoomos_v2.service.file.export.strategy.ProcessingStrategyType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@Transactional
public class ClientProcessingStrategyService {
    private final ClientProcessingStrategyRepository clientProcessingStrategyRepository;
    private final ClientService clientService;
    private final ObjectMapper objectMapper;

    public ClientProcessingStrategyService(ClientProcessingStrategyRepository clientProcessingStrategyRepository, ClientService clientService, ObjectMapper objectMapper) {
        this.clientProcessingStrategyRepository = clientProcessingStrategyRepository;
        this.clientService = clientService;
        this.objectMapper = objectMapper;
    }


    /**
     * Получает список стратегий клиента
     */
    public List<ClientProcessingStrategy> getClientStrategies(Long clientId) {
        log.debug("Получение стратегий для клиента: {}", clientId);
        return clientProcessingStrategyRepository.findByClientIdAndIsActiveTrue(clientId);
    }

    /**
     * Назначает новую стратегию клиенту
     */
    @Transactional
    public void assignStrategyToClient(Long clientId, ProcessingStrategyType strategyType,
                                       Map<String, Object> parameters) {
        log.debug("Назначение стратегии {} клиенту {}", strategyType, clientId);

        Client client = clientService.getClientById(clientId);

        // Деактивируем существующую стратегию этого типа
        clientProcessingStrategyRepository
                .findByClientIdAndStrategyTypeAndIsActiveTrue(clientId, strategyType)
                .ifPresent(strategy -> {
                    strategy.setActive(false);
                    clientProcessingStrategyRepository.save(strategy);
                });

        // Создаем новую стратегию
        ClientProcessingStrategy newStrategy = ClientProcessingStrategy.builder()
                .client(client)
                .strategyType(strategyType)
                .isActive(true)
                .parameters(writeParametersAsJson(parameters))
                .build();

        clientProcessingStrategyRepository.save(newStrategy);
    }

    /**
     * Деактивирует стратегию
     */
    @Transactional
    public void deactivateStrategy(Long strategyId) {
        log.debug("Деактивация стратегии: {}", strategyId);

        ClientProcessingStrategy strategy = clientProcessingStrategyRepository
                .findById(strategyId)
                .orElseThrow(() -> new IllegalArgumentException("Strategy not found: " + strategyId));

        strategy.setActive(false);
        clientProcessingStrategyRepository.save(strategy);
    }

    /**
     * Получает активную стратегию определенного типа для клиента
     */
    public Optional<ClientProcessingStrategy> findActiveStrategy(Long clientId,
                                                                 ProcessingStrategyType strategyType) {
        return clientProcessingStrategyRepository
                .findByClientIdAndStrategyTypeAndIsActiveTrue(clientId, strategyType);
    }

    private String writeParametersAsJson(Map<String, Object> parameters) {
        try {
            return objectMapper.writeValueAsString(parameters);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize parameters to JSON", e);
        }
    }
}
