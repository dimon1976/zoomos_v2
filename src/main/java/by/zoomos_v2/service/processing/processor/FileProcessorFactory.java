package by.zoomos_v2.service.processing.processor;

import by.zoomos_v2.exception.FileProcessingException;
import by.zoomos_v2.model.FileMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Фабрика для создания процессоров обработки файлов
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileProcessorFactory {

    private final List<FileProcessor> processors;

    /**
     * Возвращает подходящий процессор для обработки файла
     */
    public FileProcessor getProcessor(FileMetadata metadata) {
        log.debug("Поиск процессора для файла типа: {}", metadata.getFileType());

        return processors.stream()
                .filter(processor -> processor.supports(metadata))
                .findFirst()
                .orElseThrow(() -> {
                    log.error("Не найден процессор для типа файла: {}", metadata.getFileType());
                    return new FileProcessingException(
                            "Не найден обработчик для типа файла: " + metadata.getFileType());
                });
    }
}