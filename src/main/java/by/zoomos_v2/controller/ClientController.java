package by.zoomos_v2.controller;

import by.zoomos_v2.DTO.ClientDTO;
import by.zoomos_v2.DTO.dashboard.ClientDashboardStatsDTO;
import by.zoomos_v2.DTO.dashboard.DashboardOverviewDTO;
import by.zoomos_v2.aspect.LogExecution;
import by.zoomos_v2.exception.TabDataException;
import by.zoomos_v2.model.Client;
import by.zoomos_v2.model.enums.OperationStatus;
import by.zoomos_v2.service.client.ClientService;
import by.zoomos_v2.service.file.input.service.FileUploadService;
import by.zoomos_v2.service.file.metadata.FileMetadataService;
import by.zoomos_v2.service.mapping.ExportConfigService;
import by.zoomos_v2.service.mapping.MappingConfigService;
import by.zoomos_v2.service.statistics.OperationStatsService;
import by.zoomos_v2.service.statistics.dashboard.DashboardStatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Контроллер для управления клиентами.
 * Обеспечивает функционал просмотра, создания, редактирования и удаления клиентов,
 * а также работу с настройками и dashboard клиента.
 */
@Slf4j
@Controller
@RequestMapping
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;
    private final FileUploadService fileUploadService;
    private final MappingConfigService mappingConfigService;
    private final ExportConfigService exportConfigService;
    private final FileMetadataService fileMetadataService;
    private final DashboardStatisticsService dashboardStatisticsService;
    private final OperationStatsService operationStatsService;

    /**
     * Отображает список всех клиентов
     * @param model модель для передачи данных в представление
     * @return имя представления
     */
    @GetMapping("/clients")
    @LogExecution("Просмотр списка магазинов")
    public String showClients(Model model) {
        log.debug("Запрошен список клиентов");
        try {
            List<Client> clients = clientService.getAllClients();

            // Обогащаем клиентов статистикой
            List<ClientDTO> clientDTOs = clients.stream()
                    .map(client -> {
                        ClientDTO dto = ClientDTO.fromClient(client);

                        try {
                            // Добавляем статистику операций
                            Map<OperationStatus, Long> operationsByStatus = operationStatsService
                                    .getClientOperations(client.getId(), null)
                                    .stream()
                                    .collect(Collectors.groupingBy(
                                            op -> op.getStatus(),
                                            Collectors.counting()
                                    ));

                            dto.setActiveOperationsCount(
                                    operationsByStatus.getOrDefault(OperationStatus.IN_PROGRESS, 0L)
                            );

                            dto.setFailedOperationsCount(
                                    operationsByStatus.getOrDefault(OperationStatus.FAILED, 0L)
                            );

                            // Добавляем статистику файлов
                            long totalFiles = fileMetadataService.getFilesByClientId(client.getId()).size();
                            dto.setTotalFiles(totalFiles);

                            // Получаем общий размер данных
                            ClientDashboardStatsDTO dashboardStats = dashboardStatisticsService
                                    .getDashboardOverview(client.getId(), null, null)
                                    .getStats();

                            if (dashboardStats != null) {
                                dto.setFormattedTotalSize(dashboardStats.getFormattedTotalSize());
                            }
                        } catch (Exception e) {
                            log.warn("Не удалось получить статистику для клиента {}: {}", client.getName(), e.getMessage());
                        }

                        return dto;
                    })
                    .collect(Collectors.toList());

            model.addAttribute("clients", clientDTOs);
            return "client/clients";
        } catch (Exception e) {
            log.error("Ошибка при получении списка магазинов: {}", e.getMessage(), e);
            model.addAttribute("error", "Не удалось загрузить список магазинов");
            return "error";
        }
    }

    /**
     * Отображает dashboard клиента
     * @param clientName имя клиента
     * @param from дата начала периода
     * @param to дата окончания периода
     * @param model модель для передачи данных в представление
     * @return имя представления dashboard
     */
    @GetMapping("/client/{clientName}/dashboard")
    @LogExecution("Просмотр панели управления магазина")
    public String showDashboard(
            @PathVariable String clientName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            Model model
    ) {
        log.debug("Запрошен dashboard магазина с clientName: {}, период: {} - {}", clientName, from, to);
        try {
            // Получаем основные данные клиента
            Client client = clientService.getClientByName(clientName);
            model.addAttribute("client", client);

            // Данные для вкладки загрузки
            model.addAttribute("files", fileMetadataService.getFilesInfoByClientId(client.getId()));
            model.addAttribute("mappings", mappingConfigService.getMappingsForClient(client.getId()));

            // Данные для вкладки экспорта
            model.addAttribute("configs", exportConfigService.getConfigsByClientId(client.getId()));

            // Добавляем данные дашборда
            model.addAttribute("dashboardData", dashboardStatisticsService.getDashboardOverview(client.getId(), from, to));
            model.addAttribute("from", from);
            model.addAttribute("to", to);

            return "client/dashboard";
        } catch (Exception e) {
            log.error("Ошибка при загрузке dashboard магазина: {}", e.getMessage(), e);
            model.addAttribute("error", "Не удалось загрузить dashboard магазина");
            return "error";
        }
    }

    /**
     * AJAX endpoint для обновления данных дашборда
     */
    @GetMapping("/client/{clientName}/dashboard/data")
    @ResponseBody
    public DashboardOverviewDTO getDashboardData(
            @PathVariable String clientName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        log.debug("AJAX запрос данных дашборда для клиента {}, период: {} - {}", clientName, from, to);
        return dashboardStatisticsService.getDashboardOverview(clientService.getClientByName(clientName).getId(), from, to);
    }

    /**
     * Отображает форму настроек клиента
     * @param clientName имя клиента
     * @param model модель для передачи данных в представление
     * @return имя представления
     */
    @GetMapping("/client/{clientName}/settings")
    @LogExecution("Просмотр настроек магазина")
    public String showClientSettings(@PathVariable String clientName, Model model) {
        log.debug("Запрошены настройки магазина с clientName: {}", clientName);
        try {
            Client client = clientService.getClientByName(clientName);
            model.addAttribute("client", client);
            return "client/client-settings";
        } catch (Exception e) {
            log.error("Ошибка при получении настроек магазина: {}", e.getMessage(), e);
            model.addAttribute("error", "Не удалось загрузить настройки магазина");
            return "error";
        }
    }

    /**
     * Обрабатывает сохранение настроек клиента
     * @param client объект клиента с обновленными данными
     * @param active статус активности клиента
     * @param redirectAttributes атрибуты для передачи сообщений
     * @return редирект на соответствующую страницу
     */
    @PostMapping("/client/{clientName}/settings/save")
    @LogExecution("Сохранение настроек магазина")
    public String saveClientSettings(@PathVariable String clientName,
                                     @ModelAttribute("client") Client client,
                                     @RequestParam(value = "active", defaultValue = "false") boolean active,
                                     RedirectAttributes redirectAttributes) {
        log.debug("Сохранение настроек магазина с clientName {}: {}", clientName, client);
        try {
            client.setId(clientService.getClientByName(clientName).getId()); // Убеждаемся, что ID соответствует URL
            client.setActive(active);

            if (client.getId() == null) {
                clientService.createClient(client);
                redirectAttributes.addFlashAttribute("success", "Магазин успешно создан");
                return "redirect:/clients";
            } else {
                clientService.updateClient(client);
                redirectAttributes.addFlashAttribute("success", "Настройки магазина обновлены");
                return "redirect:/client/" + clientName + "/dashboard";
            }
        } catch (Exception e) {
            log.error("Ошибка при сохранении магазина: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка при сохранении магазина: " + e.getMessage());
            return "redirect:/client/" + clientName + "/settings";
        }
    }

    /**
     * Отображает форму создания нового клиента
     * @param model модель для передачи данных в представление
     * @return имя представления
     */
    @GetMapping("/client/new")
    @LogExecution("Создание нового магазина")
    public String showNewClientForm(Model model) {
        log.debug("Отображение формы создания нового магазина");
        Client client = new Client();
        client.setActive(true);
        model.addAttribute("client", client);
        return "client/client-settings";
    }

    /**
     * Обрабатывает создание нового клиента
     * @param client объект клиента с данными
     * @param redirectAttributes атрибуты для передачи сообщений
     * @return редирект на список клиентов
     */
    @PostMapping("/client/new")
    @LogExecution("Создание нового магазина")
    public String createClient(@ModelAttribute Client client,
                               @RequestParam(value = "active", defaultValue = "false") boolean active,
                               RedirectAttributes redirectAttributes) {
        log.debug("Создание нового магазина: {}", client);
        try {
            client.setActive(active);
            clientService.createClient(client);
            redirectAttributes.addFlashAttribute("success", "Магазин успешно создан");
            return "redirect:/clients";
        } catch (Exception e) {
            log.error("Ошибка при создании магазина: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Ошибка при создании магазина: " + e.getMessage());
            return "redirect:/client/new";
        }
    }

    /**
     * Обрабатывает удаление клиента
     * @param clientName имя клиента
     * @param redirectAttributes атрибуты для передачи сообщений
     * @return редирект на список клиентов
     */
    @PostMapping("/client/{clientName}/delete")
    @LogExecution("Удаление магазина")
    public String deleteClient(@PathVariable String clientName,
                               RedirectAttributes redirectAttributes) {
        log.debug("Запрос на удаление магазина с ID: {}", clientName);
        try {
            clientService.deleteClient(clientService.getClientByName(clientName).getId());
            redirectAttributes.addFlashAttribute("success", "Магазин успешно удален");
        } catch (Exception e) {
            log.error("Ошибка при удалении магазина: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка при удалении магазина: " + e.getMessage());
        }
        return "redirect:/clients";
    }

    /**
     * Загрузка данных для вкладки загрузки файлов
     */
    @GetMapping("/client/{clientName}/upload-data")
    @ResponseBody
    public Map<String, Object> getUploadTabData(@PathVariable String clientName) {
        log.debug("Загрузка данных для вкладки загрузки файлов клиента {}", clientName);
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("files", fileUploadService.getRecentFiles(clientService.getClientByName(clientName).getId()));
            data.put("mappings", mappingConfigService.getMappingsForClient(clientService.getClientByName(clientName).getId()));
            return data;
        } catch (Exception e) {
            log.error("Ошибка при загрузке данных для вкладки загрузки: {}", e.getMessage(), e);
            throw new TabDataException("Ошибка при загрузке данных вкладки", e);
        }
    }
}
