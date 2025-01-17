package by.zoomos_v2.service;

import by.zoomos_v2.exception.ValidationException;
import by.zoomos_v2.model.FileMetadata;
import by.zoomos_v2.repository.FileMetadataRepository;
import by.zoomos_v2.repository.ProductRepository;
import by.zoomos_v2.service.processor.client.ClientDataProcessor;
import by.zoomos_v2.service.processor.client.ClientDataProcessorFactory;
import by.zoomos_v2.service.processor.client.ProcessingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Сервис для выгрузки данных.
 * Обеспечивает получение и обработку данных для экспорта.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataExportService {

    private final FileMetadataRepository fileMetadataRepository;
    private final ProductRepository productRepository;
    private final ClientDataProcessorFactory clientDataProcessorFactory;

    /**
     * Получает данные из последнего обработанного файла клиента
     *
     * @param clientId идентификатор клиента
     * @return обработанные данные для экспорта
     * @throws ValidationException если нет обработанных файлов
     */
    @Transactional(readOnly = true)
    public List<Map<String, String>> getDataFromLastFile(Long clientId) {
        log.debug("Получение данных из последнего файла для клиента: {}", clientId);

        FileMetadata lastFile = fileMetadataRepository.findFirstByClientIdAndStatusOrderByProcessingCompletedAtDesc(
                        clientId, "COMPLETED")
                .orElseThrow(() -> {
                    log.error("Не найдены обработанные файлы для клиента: {}", clientId);
                    return new ValidationException("Не найдены обработанные файлы");
                });

        return getDataByFile(lastFile.getId(), clientId);
    }

    /**
     * Получает данные из конкретного файла
     *
     * @param fileId идентификатор файла
     * @param clientId идентификатор клиента
     * @return обработанные данные для экспорта
     * @throws ValidationException если файл не найден или не принадлежит клиенту
     */
    @Transactional(readOnly = true)
    public List<Map<String, String>> getDataByFile(Long fileId, Long clientId) {
        log.debug("Получение данных из файла: {} для клиента: {}", fileId, clientId);

        FileMetadata file = fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> {
                    log.error("Файл не найден: {}", fileId);
                    return new ValidationException("Файл не найден");
                });

        if (!file.getClientId().equals(clientId)) {
            log.error("Файл {} не принадлежит клиенту {}", fileId, clientId);
            throw new ValidationException("Файл не принадлежит указанному клиенту");
        }

        // Получаем сырые данные из БД
        List<Map<String, String>> rawData = productRepository.findAllByFileId(fileId);
        log.debug("Получено {} записей из файла {}", rawData.size(), fileId);

        // Получаем процессор для клиента и обрабатываем данные
        ClientDataProcessor processor = clientDataProcessorFactory.getProcessor(clientId);
        ProcessingResult result = processor.processData(rawData, clientId);

        if (!result.isSuccess()) {
            log.error("Ошибка при обработке данных: {}", result.getErrors());
            throw new ValidationException("Ошибка при обработке данных: " +
                    String.join(", ", result.getErrors()));
        }

        log.info("Успешно обработано {} записей из файла {} для клиента {}",
                result.getProcessedData().size(), fileId, clientId);

        return result.getProcessedData();
    }
}
