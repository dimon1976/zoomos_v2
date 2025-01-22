package by.zoomos_v2.controller;

import by.zoomos_v2.service.file.metadata.FileMetadataService;
import by.zoomos_v2.service.mapping.ExportConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/export")
@RequiredArgsConstructor
public class ExportViewController {
    private final FileMetadataService fileMetadataService;
    private final ExportConfigService exportConfigService;

    @GetMapping
    public String showExportPage(Model model) {
        // TODO: получать clientId из сессии
        Long clientId = 5L;

        model.addAttribute("files", fileMetadataService.getFilesByClientId(clientId));
        model.addAttribute("configs", exportConfigService.getConfigsByClientId(clientId));

        return "export/index";
    }
}
