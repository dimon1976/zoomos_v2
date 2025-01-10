package by.zoomos_v2.controller;

import by.zoomos_v2.mapping.ClientMappingConfig;
import by.zoomos_v2.model.Client;
import by.zoomos_v2.model.ProductEntity;
import by.zoomos_v2.model.RegionDataEntity;
import by.zoomos_v2.model.SiteDataEntity;
import by.zoomos_v2.repository.ClientMappingConfigRepository;
import by.zoomos_v2.service.ClientService;
import by.zoomos_v2.service.MappingConfigService;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
@RequestMapping("/shop/{clientName}/mapping")
public class ClientMappingController {

    @Autowired
    private MappingConfigService mappingConfigService;
    @Autowired
    private ClientMappingConfigRepository clientMappingConfigRepository;
    @Autowired
    private ClientService clientService;


    // Сохранение нового маппинга для клиента
    @PostMapping("/save")
    public String saveMapping(@PathVariable String clientName,
                              @RequestParam String configName,
                              @RequestParam String entityType,
                              @RequestParam Map<String, String> mappingHeaders,
                              Model model) {
        Client client = clientService.getClientByName(clientName);
        if (client == null) {
            model.addAttribute("error", "Клиент не найден");
            return "error";
        }

        // Преобразуем Map в JSON
        String mappingJson = new Gson().toJson(mappingHeaders);

        try {
            mappingConfigService.saveMappingConfig(client, configName, entityType, mappingJson);
            model.addAttribute("success", "Mapping configuration saved successfully.");
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "/uploadMapping/add-mapping"; // Возвращаемся к форме с ошибкой
        }

        return "redirect:/shop/{clientName}/upload";
    }

    // Переход к редактированию нового маппинга (поле для ввода названия)
    @PostMapping("/add")
    public String addMappingConfig(@PathVariable String clientName, @RequestParam String entityType, Model model) {
        Client client = clientService.getClientByName(clientName);
        if (client == null) {
            model.addAttribute("error", "Client not found");
            return "error";  // Шаблон ошибки
        }
        // Определяем класс сущности по переданному типу
        Class<?> entityClass;
        switch (entityType) {
            case "Product":
                entityClass = ProductEntity.class;
                break;
            case "Region":
                entityClass = RegionDataEntity.class;
                break;
            case "Site":
                entityClass = SiteDataEntity.class;
                break;
            default:
                model.addAttribute("error", "Неизвестный тип сущности");
                return "error";
        }

        // Получаем поля сущности и их описания
        Map<String, String> fieldDescriptions = mappingConfigService.getEntityFieldDescriptions(entityClass);

        // Отправляем форму для ввода данных нового маппинга
        model.addAttribute("client", client);
        model.addAttribute("entityType", entityType);
        model.addAttribute("fieldDescriptions", fieldDescriptions);
        return "/uploadMapping/add-mapping";  // Шаблон для редактирования конфигурации
    }

    @GetMapping("/edit/{configId}")
    public String editMappingConfig(@PathVariable String clientName,
                                    @RequestParam String entityType,
                                    @PathVariable Long configId,
                                    Model model) {

        Client client = clientService.getClientByName(clientName);
        if (client == null) {
            model.addAttribute("error", "Client not found");
            return "error";
        }

        // Получаем конфигурацию по ID
        ClientMappingConfig config = clientMappingConfigRepository.findById(configId)
                .orElseThrow(() -> new IllegalArgumentException("Mapping config not found"));
        // Преобразуем строку JSON в Map
        Gson gson = new Gson();
        Map<String, String> mappingHeaders = gson.fromJson(config.getMappingData(), new TypeToken<Map<String, String>>() {
        }.getType());


        // Определяем класс сущности по переданному типу
        Class<?> entityClass;
        switch (entityType) {
            case "Product":
                entityClass = ProductEntity.class;
                break;
            case "Region":
                entityClass = RegionDataEntity.class;
                break;
            case "Site":
                entityClass = SiteDataEntity.class;
                break;
            default:
                model.addAttribute("error", "Unknown entity type");
                return "error";
        }

        // Получаем поля сущности и их описания
        Map<String, String> fieldDescriptions = mappingConfigService.getEntityFieldDescriptions(entityClass);

        // Отправляем форму для ввода данных нового маппинга
        model.addAttribute("client", client);
        model.addAttribute("entityType", entityType);
        model.addAttribute("config", config);  // передаем текущую конфигурацию
        model.addAttribute("mappingHeaders", mappingHeaders); // передаем данные для отображения
        model.addAttribute("fieldDescriptions", fieldDescriptions);
        return "/uploadMapping/edit-mapping";  // Шаблон для редактирования конфигурации
    }

    // Обновление маппинга по ID
    @PostMapping("/edit/{configId}")
    public String editMappingConfig(@PathVariable String clientName,
                                    @RequestParam String configName,
                                    @PathVariable Long configId,
                                    @RequestParam Map<String, String> mappingHeaders,
                                    Model model) {

        Client client = clientService.getClientByName(clientName);
        if (client == null) {
            model.addAttribute("error", "Client not found");
            return "error";
        }

        // Получаем конфигурацию по ID
        ClientMappingConfig config = clientMappingConfigRepository.findById(configId)
                .orElseThrow(() -> new IllegalArgumentException("Mapping config not found"));

        // Преобразуем Map в JSON
        String mappingJson = new Gson().toJson(mappingHeaders);

        // Обновляем конфигурацию маппинга
        config.setMappingData(mappingJson);

        // Сохраняем обновленную конфигурацию
        clientMappingConfigRepository.save(config);

        model.addAttribute("success", "Mapping configuration updated successfully.");
        return "redirect:/shop/{clientName}/upload";
    }

    // Удаление маппинга
    @GetMapping("/delete/{configId}")
    public String deleteMappingConfig(@PathVariable String clientName, @PathVariable Long configId, Model model) {
        Client client = clientService.getClientByName(clientName);  // Ищем клиента по имени

        if (client == null) {
            model.addAttribute("error", "Client not found");
            return "error";  // Шаблон ошибки
        }

        mappingConfigService.deleteMappingConfig(configId);
        model.addAttribute("success", "Mapping configuration deleted successfully.");
        return "redirect:/shop/{clientName}/upload";  // Перенаправление на страницу с маппингами
    }
}
