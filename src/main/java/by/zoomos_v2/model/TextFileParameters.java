package by.zoomos_v2.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Класс, содержащий параметры текстового файла.
 */
@Data
@AllArgsConstructor
public class TextFileParameters {
    private String encoding;
    private char delimiter;
}
