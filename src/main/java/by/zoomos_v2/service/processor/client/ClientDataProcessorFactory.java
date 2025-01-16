package by.zoomos_v2.service.processor.client;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Фабрика для создания процессоров данных клиентов
 */
@Component
@RequiredArgsConstructor
public class ClientDataProcessorFactory {

    private final Map<Long, ClientDataProcessor> processors;
    private final DefaultClientDataProcessor defaultProcessor;

    public ClientDataProcessor getProcessor(Long clientId) {
        return processors.getOrDefault(clientId, defaultProcessor);
    }
}
