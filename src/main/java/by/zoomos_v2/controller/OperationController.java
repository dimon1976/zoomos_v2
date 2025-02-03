package by.zoomos_v2.controller;

import by.zoomos_v2.DTO.operation.ExportOperationDTO;
import by.zoomos_v2.DTO.operation.ImportOperationDTO;
import by.zoomos_v2.DTO.operation.ImportStatsSummaryDTO;
import by.zoomos_v2.service.statistics.OperationStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/operations")
@RequiredArgsConstructor
@Slf4j
public class OperationController {
    private final OperationStatsService operationStatsService;

    @GetMapping("/import/stats")
    public String getImportStats(@RequestParam Long clientId, Model model) {
        ImportStatsSummaryDTO stats = operationStatsService.getImportStatsSummary(clientId);
        model.addAttribute("importStats", stats);
        return "operations/import-stats";
    }

    @GetMapping("/import/list")
    public String getImportOperations(@RequestParam Long clientId, Model model) {
        List<ImportOperationDTO> operations = operationStatsService.getClientImportOperations(clientId);
        model.addAttribute("importOperations", operations);
        return "operations/import-list";
    }

    @GetMapping("/export/list")
    public String getExportOperations(@RequestParam Long clientId, Model model) {
        List<ExportOperationDTO> operations = operationStatsService.getClientExportOperations(clientId);
        model.addAttribute("exportOperations", operations);
        return "operations/export-list";
    }
}
