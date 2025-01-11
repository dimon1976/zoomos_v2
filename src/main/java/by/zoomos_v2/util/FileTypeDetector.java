package by.zoomos_v2.util;

import java.util.HashMap;
import java.util.Map;

public class FileTypeDetector {

    // Карта для хранения типов файлов и их допустимых расширений
    private static final Map<String, String> FILE_TYPE_MAP = new HashMap<>();

    static {
        FILE_TYPE_MAP.put("csv", "text/csv");
        FILE_TYPE_MAP.put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        FILE_TYPE_MAP.put("xls", "application/vnd.ms-excel");
        FILE_TYPE_MAP.put("txt", "text/plain");
        // Добавьте другие типы по необходимости
    }

    /**
     * Определяет MIME-тип файла на основе его расширения.
     *
     * @param fileName Имя файла.
     * @return MIME-тип файла или null, если расширение неизвестно.
     */
    public static String detectFileType(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return null;
        }

        // Извлекаем расширение
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();

        // Возвращаем тип файла
        return FILE_TYPE_MAP.get(extension);
    }

    /**
     * Проверяет, является ли файл допустимого типа.
     *
     * @param fileName Имя файла.
     * @return true, если файл поддерживается, иначе false.
     */
    public static boolean isSupportedFileType(String fileName) {
        return detectFileType(fileName) != null;
    }
}
