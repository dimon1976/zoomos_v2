package by.zoomos_v2.DTO;

import by.zoomos_v2.model.Client;
import lombok.Data;

/**
 * DTO для отображения информации о клиенте со статистикой
 */
@Data
public class ClientDTO {
    private Long id;
    private String name;
    private String url;
    private String apiKey;
    private boolean active;

    // Статистика операций
    private Long activeOperationsCount = 0L;
    private Long failedOperationsCount = 0L;

    // Статистика файлов
    private Long totalFiles = 0L;
    private String formattedTotalSize = "0 MB";

    /**
     * Создает DTO на основе модели клиента
     */
    public static ClientDTO fromClient(Client client) {
        ClientDTO dto = new ClientDTO();
        dto.setId(client.getId());
        dto.setName(client.getName());
        dto.setUrl(client.getUrl());
        dto.setApiKey(client.getApiKey());
        dto.setActive(client.isActive());
        return dto;
    }
}