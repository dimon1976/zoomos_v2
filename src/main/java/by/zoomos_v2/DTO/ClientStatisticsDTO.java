package by.zoomos_v2.DTO;

import lombok.Data;

import java.util.List;

/**
 * DTO для передачи статистики клиента
 */
@Data
public class ClientStatisticsDTO {
    private long filesCount;
    private long totalSize;
    private int activeOperationsCount;
    private List<ClientOperationDTO> recentOperations;
}
