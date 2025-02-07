package by.zoomos_v2.controller;

import by.zoomos_v2.DTO.statistics.ProcessingStatsDTO;
import by.zoomos_v2.model.operation.ImportOperation;
import by.zoomos_v2.service.file.input.service.FileProcessingService;
import by.zoomos_v2.service.statistics.OperationStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/client/{clientId}/files")
@RequiredArgsConstructor
@Slf4j
public class FileStatusController {
    private final FileProcessingService fileProcessingService;
    private final OperationStatsService operationStatsService;

    @GetMapping("/{fileId}/status")
    public ProcessingStatsDTO getFileStatus(@PathVariable Long clientId, @PathVariable Long fileId) {
        return operationStatsService.findOperation(fileId)
                .map(operation -> {
                    Map<String, Object> currentProgress =
                            (Map<String, Object>) operation.getMetadata().getOrDefault("currentProgress", new HashMap<>());
                    String message = (String) currentProgress.getOrDefault("message", "Статус неизвестен");

                    return ProcessingStatsDTO.fromOperation(operation, message);
                })
                .orElse(ProcessingStatsDTO.builder()
                        .status("UNKNOWN")
                        .build());
    }
}

