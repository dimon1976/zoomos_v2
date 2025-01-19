package by.zoomos_v2.service.processing.client;

import by.zoomos_v2.model.Client;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Фабрика для создания процессоров данных клиентов
 */
@Component
@RequiredArgsConstructor
public class ClientDataProcessorFactory {

    private final List<ClientDataProcessor> processors;
    private final DefaultClientDataProcessor defaultProcessor;

    /**
     * Возвращает процессор для указанного клиента
     */
    public ClientDataProcessor getProcessor(Client client) {
        return processors.stream()
                .filter(processor -> processor.supports(client))
                .findFirst()
                .orElse(defaultProcessor);
    }
}
