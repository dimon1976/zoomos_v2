package by.zoomos_v2.controller;
import by.zoomos_v2.model.ExportConfig;
import by.zoomos_v2.model.ExportResult;
import by.zoomos_v2.service.file.export.service.FileExportService;
import by.zoomos_v2.service.mapping.ExportConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Контроллер для экспорта данных
 */
@Slf4j
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    private final FileExportService fileExportService;
    private final ExportConfigService exportConfigService;

    /**
     * Получает список доступных конфигураций экспорта для клиента
     *
     * @param clientId идентификатор клиента
     * @return список конфигураций экспорта
     */
    @GetMapping("/configs/{clientId}")
    public List<ExportConfig> getExportConfigs(@PathVariable Long clientId) {
        return exportConfigService.getConfigsByClientId(clientId);
    }

    /**
     * Экспортирует данные из файла
     *
     * @param fileId идентификатор файла
     * @param configId идентификатор конфигурации экспорта
     * @param fileType тип файла для экспорта (CSV/XLSX)
     * @return файл с экспортированными данными
     */
    @GetMapping("/file/{fileId}")
    public ResponseEntity<Resource> exportFile(
            @PathVariable Long fileId,
            @RequestParam Long configId,
            @RequestParam String fileType) {

        log.debug("Запрос на экспорт файла. FileId: {}, ConfigId: {}, FileType: {}",
                fileId, configId, fileType);

        // Получаем конфигурацию экспорта
        ExportConfig exportConfig = exportConfigService.getConfigById(configId);

        // Выполняем экспорт
        ExportResult exportResult = fileExportService.exportFileData(fileId, exportConfig, fileType);

        if (!exportResult.isSuccess()) {
            log.error("Ошибка при экспорте файла: {}", exportResult.getErrorMessage());
            return ResponseEntity.badRequest().build();
        }

        // Определяем тип контента
        String contentType = switch (fileType.toLowerCase()) {
            case "csv" -> "text/csv";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default -> "application/octet-stream";
        };

        // Подготавливаем имя файла для загрузки
        String filename = exportResult.getFileName();
        String encodedFilename = new String(filename.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.ISO_8859_1);

        // Формируем заголовки ответа
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentDispositionFormData("attachment", encodedFilename);

        // Создаем ресурс из результата экспорта
        ByteArrayResource resource = new ByteArrayResource(exportResult.getFileContent());

        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(exportResult.getFileContent().length)
                .body(resource);
    }

    /**
     * Получает список поддерживаемых форматов экспорта
     *
     * @return список форматов файлов для экспорта
     */
    @GetMapping("/formats")
    public List<String> getSupportedFormats() {
        return List.of("CSV", "XLSX");
    }

    /**
     * Проверяет статус выполнения экспорта
     *
     * @param exportId идентификатор экспорта
     * @return информация о статусе экспорта
     */
    @GetMapping("/status/{exportId}")
    public ExportResult getExportStatus(@PathVariable String exportId) {
        // TODO: Реализовать проверку статуса для асинхронного экспорта
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
