package by.zoomos_v2.service;

import by.zoomos_v2.model.Client;
import by.zoomos_v2.repository.ClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ClientService {
    private static final Logger logger = LoggerFactory.getLogger(ClientService.class);

    private final ClientRepository clientRepository;

    public ClientService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    public Client addClient(String name) {
        if (clientRepository.findByName(name).isPresent()) {
            logger.warn("Attempt to add existing client: {}", name);
            throw new IllegalArgumentException("Client with name " + name + " already exists");
        }
        Client client = new Client(name, LocalDateTime.now());  // Убедитесь, что дата здесь устанавливается
        return clientRepository.save(client);
    }

    public List<Client> getAllClients() {
        return clientRepository.findAll();
    }

    public Client getClientByName(String name) {
        return clientRepository.findByName(name).orElseThrow(() -> new IllegalArgumentException("Client not found"));
    }

    public Long getClientIdByName(String name) {
        return clientRepository.findByName(name)
                .map(Client::getId)
                .orElseThrow(() -> new IllegalArgumentException("Client with name '" + name + "' not found"));
    }
}
