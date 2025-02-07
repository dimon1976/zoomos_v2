package by.zoomos_v2.controller;

import by.zoomos_v2.aspect.LogExecution;
import by.zoomos_v2.exception.HomePageException;
import by.zoomos_v2.model.enums.OperationStatus;
import by.zoomos_v2.model.operation.BaseOperation;
import by.zoomos_v2.service.client.ClientService;
import by.zoomos_v2.service.statistics.OperationStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.service.OperationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Comparator;
import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class HomeController {
    private final ClientService clientService;
    private final OperationStatsService operationStatsService;

    @GetMapping("/")
    @LogExecution("Просмотр главной страницы")
    public String index(Model model) {
        try {
            long clientsCount = clientService.getAllClients().size();
            List<BaseOperation> recentOperations = operationStatsService.getClientOperations(null, null)
                    .stream()
                    .<BaseOperation>map(op -> op) // Явное указание типа для map
                    .sorted(Comparator.comparing(BaseOperation::getStartTime).reversed())
                    .limit(10)
                    .toList();

            model.addAttribute("clientsCount", clientsCount);
            model.addAttribute("recentOperations", recentOperations);
            model.addAttribute("activeOperationsCount", countActiveOperations(recentOperations));

            return "index";
        } catch (Exception e) {
            log.error("Ошибка при загрузке главной страницы: {}", e.getMessage(), e);
            throw new HomePageException("Ошибка загрузки главной страницы", e);
        }
    }

    private long countActiveOperations(List<BaseOperation> operations) {
        return operations.stream()
                .filter(op -> op.getStatus() == OperationStatus.IN_PROGRESS)
                .count();
    }
}
