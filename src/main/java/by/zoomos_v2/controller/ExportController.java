package by.zoomos_v2.controller;
import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.model.ExportResult;
import by.zoomos_v2.service.export.DataExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Контроллер для работы с экспортом данных через веб-формы
 */
@Slf4j
@Controller
@RequestMapping("/export")
@RequiredArgsConstructor
public class ExportController {

    private final DataExportService exportService;

    /**
     * Страница настройки экспорта
     */
    @GetMapping
    public String showExportPage(Model model) {
        // Добавляем список поддерживаемых форматов
        model.addAttribute("formats", exportService.getSupportedFormats());
        // TODO: добавить список доступных конфигураций
        return "files/export-form";
    }

    /**
     * Обработка экспорта данных
     */
    @PostMapping("/download")
    public ResponseEntity<?> exportData(@RequestParam String fileType,
                                        @RequestParam Long configId,
                                        @SessionAttribute("exportData") List<Map<String, Object>> data) {

        // Здесь должна быть загрузка конфигурации по configId
        ExportConfig exportConfig = getExportConfig(configId);

        // Проверяем поддержку формата
        if (!exportService.isFormatSupported(fileType)) {
            // Можно добавить RedirectAttributes для сообщения об ошибке
            return ResponseEntity.badRequest()
                    .body("Unsupported file format: " + fileType);
        }

        // Выполняем экспорт
        ExportResult result = exportService.exportData(data, exportConfig, fileType);

        if (!result.isSuccess()) {
            return ResponseEntity.internalServerError()
                    .body(result.getErrorMessage());
        }

        // Формируем ответ с файлом для скачивания
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + result.getFileName() + "\"")
                .body(new ByteArrayResource(result.getFileContent()));
    }

    // TODO: Заменить на реальную загрузку конфигурации
    private ExportConfig getExportConfig(Long configId) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
