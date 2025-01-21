package by.zoomos_v2.service.file.input.service;

import by.zoomos_v2.constant.FileStatus;
import by.zoomos_v2.exception.FileProcessingException;
import by.zoomos_v2.model.FileMetadata;
import by.zoomos_v2.model.FileType;
import by.zoomos_v2.model.TextFileParameters;
import by.zoomos_v2.repository.FileMetadataRepository;
import by.zoomos_v2.util.FileTypeDetector;
import by.zoomos_v2.util.FileUtils;
import by.zoomos_v2.util.PathResolver;
import by.zoomos_v2.util.TextFileAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Сервис для управления загрузкой файлов.
 * Обеспечивает функционал загрузки файлов, сохранения метаданных
 * и управления загруженными файлами.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadService {

    private final FileMetadataRepository fileMetadataRepository;
    private final FileTypeDetector fileTypeDetector;
    private final FileValidationService fileValidationService;
    private final FileUtils fileUtils;
    private final PathResolver pathResolver;

    /**
     * Загружает файл и создает метаданные.
     * Процесс включает:
     * 1. Валидацию файла
     * 2. Определение типа файла
     * 3. Сохранение файла на диск
     * 4. Создание и сохранение метаданных
     *
     * @param file загружаемый файл
     * @param clientId идентификатор магазина
     * @param mappingId идентификатор конфигурации маппинга (опционально)
     * @return метаданные загруженного файла
     * @throws FileProcessingException при ошибках обработки файла
     */
    @Transactional
    public FileMetadata uploadFile(MultipartFile file, Long clientId, Long mappingId) {
        log.debug("Начало загрузки файла {} для магазина {}", file.getOriginalFilename(), clientId);

        try {
            // Валидация файла
            fileValidationService.validateFile(file);

            // Определение типа файла
            FileType fileType = fileTypeDetector.detectFileType(file);
            if (fileType == null) {
                throw new FileProcessingException("Неподдерживаемый тип файла");
            }

            // Создание директории для магазина если не существует
            fileUtils.createDirectoryIfNotExists(pathResolver.getClientDirectory(clientId));

            // Сохранение файла
            String storedFilename = fileUtils.saveFile(file, pathResolver.getClientDirectory(clientId));

            // Создание метаданных
            FileMetadata metadata = new FileMetadata();
            metadata.setClientId(clientId);
            metadata.setOriginalFilename(file.getOriginalFilename());
            metadata.setStoredFilename(storedFilename);
            metadata.setFileType(fileType);
            metadata.setSize(file.getSize());
            metadata.setContentType(file.getContentType());
            metadata.setMappingConfigId(mappingId);
            metadata.setStatus(FileStatus.PENDING);
            // Анализируем параметры текстового файла
            if (isTextFile(fileType)) {
                TextFileParameters parameters = TextFileAnalyzer.analyzeFile(pathResolver.getFilePath(clientId, metadata.getStoredFilename()));
                metadata.updateTextParameters(parameters);
                log.info("Определены параметры текстового файла {}: кодировка - {}, разделитель - {}",
                         parameters.getEncoding(), parameters.getDelimiter());
            }
            metadata.updateProcessingStatistics(0, 0, 0);
            // Сохранение метаданных
            metadata = fileMetadataRepository.save(metadata);
            log.info("Файл {} успешно загружен и сохранен с ID: {}",
                    file.getOriginalFilename(), metadata.getId());

            return metadata;

        } catch (Exception e) {
            log.error("Ошибка при загрузке файла: {}", e.getMessage(), e);
            throw new FileProcessingException("Ошибка при загрузке файла: " + e.getMessage(), e);
        }
    }

    private boolean isTextFile(FileType fileType) {
        return FileType.CSV.equals(fileType);
    }

    /**
     * Получает метаданные файла по ID
     *
     * @param fileId идентификатор файла
     * @return метаданные файла
     * @throws FileProcessingException если файл не найден
     */
    @Transactional(readOnly = true)
    public FileMetadata getFileMetadata(Long fileId) {
        return fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> {
                    log.error("Файл с ID {} не найден", fileId);
                    return new FileProcessingException("Файл не найден");
                });
    }

    /**
     * Получает список недавно загруженных файлов для магазина
     *
     * @param clientId идентификатор магазина
     * @return список метаданных файлов
     */
    @Transactional(readOnly = true)
    public List<FileMetadata> getRecentFiles(Long clientId) {
        return fileMetadataRepository.findByClientIdOrderByUploadedAtDesc(clientId);
    }

    /**
     * Удаляет файл и его метаданные
     *
     * @param fileId идентификатор файла
     * @param clientId идентификатор магазина
     * @throws FileProcessingException при ошибках удаления или если файл не принадлежит магазину
     */
    @Transactional
    public void deleteFile(Long fileId, Long clientId) {
        log.debug("Удаление файла {} для магазина {}", fileId, clientId);

        FileMetadata metadata = getFileMetadata(fileId);
        if (!metadata.getClientId().equals(clientId)) {
            throw new FileProcessingException("Файл не принадлежит указанному магазину");
        }

        try {
            // Удаляем физический файл
            fileUtils.deleteFile(pathResolver.getFilePath(clientId, metadata.getStoredFilename()));

            // Удаляем метаданные
            fileMetadataRepository.delete(metadata);

            log.info("Файл {} успешно удален", metadata.getOriginalFilename());
        } catch (Exception e) {
            log.error("Ошибка при удалении файла: {}", e.getMessage(), e);
            throw new FileProcessingException("Ошибка при удалении файла", e);
        }
    }
}