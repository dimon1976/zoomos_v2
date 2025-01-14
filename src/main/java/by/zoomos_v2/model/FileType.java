package by.zoomos_v2.model;

import by.zoomos_v2.config.UploadFileException;

import java.util.Arrays;

/**
 * Перечисление поддерживаемых типов файлов
 */
public enum FileType {
    CSV("text/csv"),
    EXCEL("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    XLS("application/vnd.ms-excel");

    private final String contentType;

    FileType(String contentType) {
        this.contentType = contentType;
    }

    public String getContentType() {
        return contentType;
    }

    public static FileType fromContentType(String contentType) {
        for (FileType type : values()) {
            if (type.contentType.equals(contentType)) {
                return type;
            }
        }
        return null;
    }

    public boolean matches(String contentType) {
        return this.contentType.equals(contentType);
    }
}
