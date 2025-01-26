package by.zoomos_v2.util;

import by.zoomos_v2.exception.ValidationException;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class CsvValidationUtils {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final byte[] UTF8_BOM = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    public static boolean hasBOMMarker(CSVRecord record) {
        if (record == null || record.size() == 0) return false;
        String firstField = record.get(0);
        return firstField.startsWith(new String(UTF8_BOM, StandardCharsets.UTF_8));
    }


    public static boolean hasMultipleDelimiters(String content, char expectedDelimiter) {
        Map<Character, Integer> delimiterCounts = new HashMap<>();
        for (char potential : new char[]{',', ';', '\t', '|'}) {
            if (potential != expectedDelimiter) {
                int count = StringUtils.countMatches(content, String.valueOf(potential));
                if (count > 0) delimiterCounts.put(potential, count);
            }
        }
        return !delimiterCounts.isEmpty();
    }

    public static boolean containsProblematicCharacters(String value) {
        if (value == null) return false;
        return value.contains("\n") || value.contains("\r") ||
                Pattern.compile("[\\x00-\\x1F\\x7F]").matcher(value).find();
    }

    public static void validateDate(String value) throws ValidationException {
        if (!DATE_PATTERN.matcher(value).matches()) {
            throw new ValidationException("Неверный формат даты (ожидается YYYY-MM-DD)");
        }
        try {
            LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new ValidationException("Недопустимая дата");
        }
    }

    public static void validateNumber(String value) throws ValidationException {
        try {
            new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new ValidationException("Недопустимое числовое значение");
        }
    }

    public static void validateEmail(String value) throws ValidationException {
        if (!EMAIL_PATTERN.matcher(value).matches()) {
            throw new ValidationException("Недопустимый формат email");
        }
    }
}
