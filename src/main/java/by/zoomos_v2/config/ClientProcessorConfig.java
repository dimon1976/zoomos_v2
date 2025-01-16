package by.zoomos_v2.config;
import by.zoomos_v2.service.processor.client.ClientDataProcessor;
import by.zoomos_v2.service.processor.client.Client1DataProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Конфигурация процессоров для клиентов
 */
@Configuration
public class ClientProcessorConfig {

    @Bean
    public Map<Long, ClientDataProcessor> clientProcessors(
            Client1DataProcessor client1Processor
            // Здесь можно добавлять другие процессоры клиентов
    ) {
        Map<Long, ClientDataProcessor> processors = new HashMap<>();
        processors.put(1L, client1Processor); // для клиента с ID = 1
        // processors.put(2L, client2Processor); // для следующих клиентов
        return processors;
    }

    @Bean
    public List<String> excludedSites() {
        return Arrays.asList("site1.com", "site2.com"); // список исключенных сайтов
    }
}
