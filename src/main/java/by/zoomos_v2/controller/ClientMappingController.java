package by.zoomos_v2.controller;

import by.zoomos_v2.DTO.MappingFieldDTO;
import by.zoomos_v2.mapping.ClientMappingConfig;
import by.zoomos_v2.model.Client;
import by.zoomos_v2.repository.ClientMappingConfigRepository;
import by.zoomos_v2.service.ClientService;
import by.zoomos_v2.service.MappingConfigService;
import by.zoomos_v2.util.EntityRegistryService;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/shop/{clientName}/mapping")
public class ClientMappingController {

    private final MappingConfigService mappingConfigService;
    private final ClientMappingConfigRepository clientMappingConfigRepository;
    private final ClientService clientService;
    private final EntityRegistryService entityRegistryService;

    public ClientMappingController(MappingConfigService mappingConfigService,
                                   ClientMappingConfigRepository clientMappingConfigRepository,
                                   ClientService clientService,
                                   EntityRegistryService entityRegistryService) {
        this.mappingConfigService = mappingConfigService;
        this.clientMappingConfigRepository = clientMappingConfigRepository;
        this.clientService = clientService;
        this.entityRegistryService = entityRegistryService;
    }

    @PostMapping("/save")
    public String saveMapping(@PathVariable String clientName,
                              @RequestParam String configName,
                              @RequestParam Map<String, String> mappingHeaders,
                              RedirectAttributes redirectAttributes,
                              Model model) {
        try {
            Client client = getClientOrThrow(clientName);
            mappingHeaders.remove("configName");
            mappingConfigService.saveMappingConfig(client, configName, mappingHeaders);

            redirectAttributes.addFlashAttribute("success",
                    "Конфигурация маппинга успешно сохранена");
            return "redirect:/shop/{clientName}/upload";

        } catch (Exception e) {
            log.error("Error saving mapping configuration", e);
            model.addAttribute("error", e.getMessage());
            model.addAttribute("fieldDescriptions",
                    getMappingFields());
            return "/uploadMapping/add-mapping";
        }
    }

    @PostMapping("/add")
    public String addMappingConfig(@PathVariable String clientName,
                                   Model model) {
        try {
            Client client = getClientOrThrow(clientName);
            Map<String, String> fieldDescriptions = getMappingFields();

            model.addAttribute("client", client);
            model.addAttribute("fieldDescriptions", fieldDescriptions);
            return "/uploadMapping/add-mapping";

        } catch (Exception e) {
            log.error("Error preparing add mapping form", e);
            model.addAttribute("error", e.getMessage());
            return "error";
        }
    }

    @GetMapping("/edit/{configId}")
    public String editMappingConfig(@PathVariable String clientName,
                                    @PathVariable Long configId,
                                    Model model) {
        try {
            Client client = getClientOrThrow(clientName);
            ClientMappingConfig config = getMappingConfigOrThrow(configId);
            Map<String, String> mappingHeaders = mappingConfigService.parseMappingJson(config.getMappingData());
            Map<String, String> fieldDescriptions = getMappingFields();

            model.addAttribute("client", client);
            model.addAttribute("config", config);
            model.addAttribute("mappingHeaders", mappingHeaders);
            model.addAttribute("fieldDescriptions", fieldDescriptions);
            return "/uploadMapping/edit-mapping";

        } catch (Exception e) {
            log.error("Error preparing edit mapping form", e);
            model.addAttribute("error", e.getMessage());
            return "error";
        }
    }

    @PostMapping("/edit/{configId}")
    public String updateMappingConfig(@PathVariable Long configId,
                                      @RequestParam Map<String, String> mappingHeaders,
                                      RedirectAttributes redirectAttributes,
                                      Model model, @PathVariable String clientName) {
        try {
            ClientMappingConfig config = getMappingConfigOrThrow(configId);
            mappingHeaders.remove("configName");
            mappingConfigService.updateMappingConfig(configId, config.getName(), mappingHeaders);

            redirectAttributes.addFlashAttribute("success",
                    "Конфигурация маппинга успешно обновлена");
            return "redirect:/shop/{clientName}/upload";

        } catch (Exception e) {
            log.error("Error updating mapping configuration", e);
            model.addAttribute("error", e.getMessage());
            return "/uploadMapping/edit-mapping";
        }
    }

    @GetMapping("/delete/{configId}")
    public String deleteMappingConfig(@PathVariable String clientName,
                                      @PathVariable Long configId,
                                      RedirectAttributes redirectAttributes,
                                      Model model) {
        try {
            getClientOrThrow(clientName);
            mappingConfigService.deleteMappingConfig(configId);

            redirectAttributes.addFlashAttribute("success",
                    "Конфигурация маппинга успешно удалена");
            return "redirect:/shop/{clientName}/upload";

        } catch (Exception e) {
            log.error("Error deleting mapping configuration", e);
            model.addAttribute("error", e.getMessage());
            return "error";
        }
    }

    private Client getClientOrThrow(String clientName) {
        return clientService.getClientByName(clientName);
//                .orElseThrow(() -> new IllegalArgumentException("Клиент не найден: " + clientName));

    }

    private ClientMappingConfig getMappingConfigOrThrow(Long configId) {
        return clientMappingConfigRepository.findById(configId)
                .orElseThrow(() -> new IllegalArgumentException("Конфигурация маппинга не найдена"));
    }

    private Map<String, String> getMappingFields() {
        List<Class<?>> entityClasses = entityRegistryService.getEntityClasses();
        Map<String, MappingFieldDTO> fieldDTOs = mappingConfigService.getCombinedEntityFieldDescriptions(entityClasses);

        // Преобразуем Map<String, MappingFieldDTO> в Map<String, String>
        return fieldDTOs.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().getDescription(),
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));
    }

    // Альтернативный вариант - если нужно сохранить полную информацию о полях в представлении
    private Map<String, MappingFieldDTO> getDetailedMappingFields() {
        List<Class<?>> entityClasses = entityRegistryService.getEntityClasses();
        return mappingConfigService.getCombinedEntityFieldDescriptions(entityClasses);
    }

}
