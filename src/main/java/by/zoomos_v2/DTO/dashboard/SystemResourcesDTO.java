package by.zoomos_v2.DTO.dashboard;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
@Data
@Builder
public class SystemResourcesDTO {
    private String peakMemoryUsage;
    private String currentMemoryUsage;
    private int memoryUsagePercentage;
    private int diskUsagePercentage;
    private String uptime;
    private LocalDateTime lastUpdated;
}
