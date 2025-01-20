package by.zoomos_v2.constant;
/**
 * Константы для размера BATCH пакетов обработки.
 */
public final class BatchSize {

    /**
     * Размер BATCH пакета обработки при сохранении в БД.
     */
    public static final int BATCH_SIZE_DATA_SAVE = 5000;

    /**
     * Размер BATCH пакета чтения строк из файла.
     */
    public static final int BATCH_SIZE_FILE_RECORD = 10000;

}
