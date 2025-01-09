package by.zoomos_v2.service;

import by.zoomos_v2.model.FileMetaData;
import by.zoomos_v2.repository.FileMetaDataRepository;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.apache.commons.io.FilenameUtils.getExtension;

@Service
public class FileMetaDataService {
    private static final Logger logger = LoggerFactory.getLogger(FileMetaDataService.class);

    @Autowired
    private FileMetaDataRepository fileMetaDataRepository;

    public List<FileMetaData> saveFileMetaData(MultipartFile file) throws IOException {
        List<FileMetaData> fileMetaDataList = new ArrayList<>();
        String fileName = file.getOriginalFilename();
        String fileExtension = getExtension(fileName); // Метод для получения расширения файла
        Long fileSize = file.getSize();
        String fileHash;

        logger.info("Начата обработка файла: {}", fileName);

        try (InputStream inputStream = file.getInputStream()) {
            logger.debug("Получен InputStream для файла: {}", fileName);
            fileHash = calculateFileHash(inputStream);
            logger.debug("Хеш файла {} вычислен успешно: {}", fileName, fileHash);
        } catch (IOException e) {
            logger.error("Ошибка при чтении файла для вычисления хеша: {}", fileName, e);
            throw e;
        }

        logger.info("Вычислен хеш для файла {}: {}", fileName, fileHash);

        // Проверяем, не был ли файл уже загружен ранее
        Optional<FileMetaData> existingFile = fileMetaDataRepository.findByFileHash(fileHash);
        if (existingFile.isPresent()) {
            logger.warn("Попытка загрузить уже существующий файл: {}", fileName);
            throw new IllegalArgumentException("Файл с таким содержимым уже был загружен");
        }

        // Создаем объект FileData с базовой информацией
        FileMetaData fileMetaData = new FileMetaData(fileName, fileExtension != null ? fileExtension : "Неизвестный тип", "Файл: " + fileName, LocalDateTime.now(), fileSize, fileHash);
        fileMetaDataList.add(fileMetaData);

        try {
            // Сохраняем в базу данных
            fileMetaDataRepository.save(fileMetaData);
            logger.info("Файл успешно сохранен в базу данных: {}", fileName);
        } catch (Exception e) {
            logger.error("Ошибка при сохранении файла в базу данных: {}", fileName, e);
            throw new RuntimeException("Не удалось сохранить файл в базу данных", e);
        }

        logger.debug("Обработка файла {} завершена. Всего обработано файлов: {}", fileName, fileMetaDataList.size());
        return fileMetaDataList;
    }

    private String calculateFileHash(InputStream inputStream) throws IOException {
        logger.trace("Начало вычисления хеша файла");
        // Вычисляем MD5 хеш файла
        String hash = DigestUtils.md5Hex(inputStream);
        logger.trace("Хеш файла вычислен: {}", hash);
        return hash;
    }
}
