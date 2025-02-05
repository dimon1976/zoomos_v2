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

@RestController
@RequestMapping("/api/client/{clientId}/files")
@RequiredArgsConstructor
@Slf4j
public class FileStatusController {
    private final FileProcessingService fileProcessingService;
    private final OperationStatsService operationStatsService;

    @GetMapping("/{fileId}/status")
//    public ProcessingStatsDTO getFileStatus(@PathVariable Long clientId,
//                                            @PathVariable Long fileId) {
//        var currentStatus = fileProcessingService.getProcessingStatus(fileId);
//        return operationStatsService.findOperation(fileId)
//                .map(operation -> ProcessingStatsDTO.fromOperation(operation, currentStatus))
//                .orElse(ProcessingStatsDTO.builder()
//                        .progress(currentStatus.getProgress())
//                        .message(currentStatus.getMessage())
//                        .status("UNKNOWN")
//                        .build());
//    }
    public ProcessingStatsDTO getFileStatus(@PathVariable Long clientId, @PathVariable Long fileId) {
        var currentStatus = fileProcessingService.getProcessingStatus(fileId);
        return operationStatsService.findOperation(fileId)
                .map(operation -> {
                    ProcessingStatsDTO dto = ProcessingStatsDTO.fromOperation(operation, currentStatus);
                    // Добавляем данные из операции
                    dto.setProcessedRecords(operation.getProcessedRecords());
                    dto.setTotalRecords(operation.getTotalRecords());
                    dto.setProcessingSpeed(calculateSpeed((ImportOperation) operation)); // Расчет скорости
                    return dto;
                })
                .orElse(ProcessingStatsDTO.builder()
                        .status("UNKNOWN")
                        .build());


//        return operationStatsService.findOperation(fileId)
//                .map(operation -> {
//                    // Приоритет у данных из операции
//                    int progress = operation.getProgress(); // Добавьте поле progress в ImportOperation
//                    String status = operation.getStatus().name();
//
//                    return ProcessingStatsDTO.builder()
//                            .progress(progress)
//                            .status(status)
//                            .message(operation.getStatus().name())
//                            .build();
//                })
//                .orElseGet(() -> {
//                    // Резервный вариант из processingStatuses
//                    FileProcessingService.ProcessingStatus status = fileProcessingService.getProcessingStatus(fileId);
//                    return ProcessingStatsDTO.fromStatus(status);
//                });
    }

    private double calculateSpeed(ImportOperation operation) {
        if (operation.getStartTime() == null || operation.getEndTime() == null) return 0.0;
        long seconds = ChronoUnit.SECONDS.between(operation.getStartTime(), operation.getEndTime());
        return seconds > 0 ? (double) operation.getProcessedRecords() / seconds : 0.0;
    }
}

