package by.zoomos_v2.controller;

import by.zoomos_v2.DTO.statistics.ProcessingStatsDTO;
import by.zoomos_v2.model.operation.BaseOperation;
import by.zoomos_v2.model.operation.ImportOperation;
import by.zoomos_v2.service.statistics.OperationStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/client/{clientId}/files")
@RequiredArgsConstructor
@Slf4j
public class FileStatusController {
    private final OperationStatsService operationStatsService;

    @GetMapping("/{fileId}/status")
//    public ProcessingStatsDTO getFileStatus(@PathVariable Long clientId, @PathVariable Long fileId) {
//        log.debug("Запрос статуса файла. Client ID: {}, File ID: {}", clientId, fileId);
//        return operationStatsService.findOperationByFileId(fileId)
//                .map(operation -> {
//                    Map<String, Object> currentProgress =
//                            (Map<String, Object>) operation.getMetadata().getOrDefault("currentProgress", new HashMap<>());
//                    String message = (String) currentProgress.getOrDefault("message", "Статус неизвестен");
//
//                    return ProcessingStatsDTO.fromOperation(operation, message);
//                })
//                .orElse(ProcessingStatsDTO.builder()
//                        .status("UNKNOWN")
//                        .build());
//    }

    public ProcessingStatsDTO getFileStatus(@PathVariable Long clientId, @PathVariable Long fileId) {
        log.debug("Запрос статуса файла. Client ID: {}, File ID: {}", clientId, fileId);

        Optional<? extends BaseOperation> optionalOperation = operationStatsService.findOperationByFileId(fileId);

        if (optionalOperation.isPresent()) {
            BaseOperation operation = optionalOperation.get();
            log.debug("Операция найдена. ID: {}, Status: {}", operation.getId(), operation.getStatus());

            Map<String, Object> metadata = operation.getMetadata();
            Map<String, Object> currentProgress = metadata.containsKey("currentProgress")
                    ? (Map<String, Object>) metadata.get("currentProgress")
                    : new HashMap<>();

            String message = currentProgress.containsKey("message")
                    ? (String) currentProgress.get("message")
                    : "Статус неизвестен";

            log.debug("Текущий прогресс: {}", message);

            return ProcessingStatsDTO.fromOperation(operation, message);
        }

        log.debug("Операция для файла ID {} не найдена. Возвращаем статус UNKNOWN.", fileId);
        return ProcessingStatsDTO.builder()
                .status("UNKNOWN")
                .build();
    }
}

