package by.zoomos_v2.model;

import by.zoomos_v2.config.UploadFileException;

import java.util.Arrays;

// Перечисление для поддерживаемых типов файлов
public enum FileType {
    XLS(".xls"),
    XLSX(".xlsx"),
    CSV(".csv"),
    TXT(".txt");

    private final String extension;

    FileType(String extension) {
        this.extension = extension;
    }

    public String getExtension() {
        return extension;
    }

    public static FileType fromFileName(String fileName) {
        String ext = fileName.substring(fileName.lastIndexOf(".")).toLowerCase();
        return Arrays.stream(FileType.values())
                .filter(type -> type.getExtension().equals(ext))
                .findFirst()
                .orElseThrow(() -> new UploadFileException("Неподдерживаемый тип файла: " + ext));
    }
}
