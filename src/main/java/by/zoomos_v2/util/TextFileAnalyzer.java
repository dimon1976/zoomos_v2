package by.zoomos_v2.util;

import by.zoomos_v2.config.FileProcessingException;
import by.zoomos_v2.model.TextFileParameters;
import lombok.extern.slf4j.Slf4j;
import org.mozilla.universalchardet.UniversalDetector;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Утилитарный класс для определения параметров текстовых файлов.
 */
@Slf4j
public class TextFileAnalyzer {
    private static final int SAMPLE_SIZE = 4096;
    private static final char[] POSSIBLE_DELIMITERS = {',', ';', '\t', '|'};

    /**
     * Определяет параметры текстового файла.
     *
     * @param filePath путь к файлу
     * @return объект с параметрами файла
     */
    public static TextFileParameters analyzeFile(Path filePath) {
        log.debug("Начало анализа файла: {}", filePath);

        try {
            byte[] sampleBytes = readFileSample(filePath);
            String encoding = detectFileEncoding(filePath);
            String sampleContent = new String(sampleBytes, encoding);
            char delimiter = detectDelimiter(sampleContent);

            TextFileParameters parameters = new TextFileParameters(encoding, delimiter);
            log.info("Определены параметры файла {}: кодировка - {}, разделитель - '{}'",
                    filePath, encoding, delimiter);

            return parameters;
        } catch (Exception e) {
            log.error("Ошибка при анализе файла: {}", filePath, e);
            throw new FileProcessingException("Не удалось определить параметры файла", e);
        }
    }

    /**
     * Определяет кодировку файла используя UniversalDetector.
     *
     * @param filePath путь к файлу
     * @return определенная кодировка или UTF-8 по умолчанию
     */
    private static String detectFileEncoding(Path filePath) throws IOException {
        UniversalDetector detector = new UniversalDetector(null);
        try (InputStream fis = Files.newInputStream(filePath)) {
            byte[] buf = new byte[4096];
            int nread;
            while ((nread = fis.read(buf)) > 0 && !detector.isDone()) {
                detector.handleData(buf, 0, nread);
            }
        }

        detector.dataEnd();
        String encoding = detector.getDetectedCharset();
        detector.reset();

        if (encoding == null) {
            log.warn("Не удалось определить кодировку файла {}, будет использована UTF-8", filePath);
            return "UTF-8";
        }

        log.debug("Определена кодировка файла {}: {}", filePath, encoding);
        return encoding;
    }

    /**
     * Читает сэмпл файла для анализа.
     */
    private static byte[] readFileSample(Path filePath) throws IOException {
        try (InputStream is = Files.newInputStream(filePath)) {
            byte[] sample = new byte[SAMPLE_SIZE];
            int bytesRead = is.read(sample);
            if (bytesRead < 0) {
                throw new IOException("Файл пуст");
            }
            return Arrays.copyOf(sample, bytesRead);
        }
    }

    /**
     * Определяет разделитель в CSV файле.
     */
    private static char detectDelimiter(String sampleContent) {
        if (sampleContent.isEmpty()) {
            return ',';
        }

        Map<Character, Integer> delimiterCounts = new HashMap<>();
        for (char delimiter : POSSIBLE_DELIMITERS) {
            int count = countDelimiterInFirstLines(sampleContent, delimiter);
            delimiterCounts.put(delimiter, count);
        }

        return delimiterCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(',');
    }

    /**
     * Подсчитывает частоту разделителя в первых строках файла.
     */
    private static int countDelimiterInFirstLines(String content, char delimiter) {
        return (int) content.chars().filter(ch -> ch == delimiter).count();
    }
}
