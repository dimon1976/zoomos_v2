package by.zoomos_v2.DTO.dashboard;

import by.zoomos_v2.DTO.operation.OperationStatsDTO;
import lombok.Builder;
import lombok.Data;

import java.util.List;
@Data
@Builder
public class DashboardOverviewDTO {
    // Общая статистика
    private ClientDashboardStatsDTO stats;
    // Последние операции
    private List<OperationStatsDTO> recentOperations;
}
