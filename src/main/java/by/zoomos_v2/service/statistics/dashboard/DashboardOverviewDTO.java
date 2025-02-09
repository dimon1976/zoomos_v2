package by.zoomos_v2.service.statistics.dashboard;

import by.zoomos_v2.DTO.operation.OperationStatsDTO;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DashboardOverviewDTO {
    private ClientDashboardStatsDTO stats;
    private List<OperationStatsDTO> recentOperations;
    private SystemResourcesDTO systemResources;
}
