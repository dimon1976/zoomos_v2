package by.zoomos_v2.model;

import lombok.Data;

import java.util.List;

// Класс для определения параметров файла
@Data
public class FileMetadata {

    private FileType fileType;
    private String charset;
    private String delimiter;
    private List<String> headers;
    private int skipLines; // количество строк для пропуска
}
