package by.zoomos_v2.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class TimeUtils {
    public static String formatDuration(Long seconds) {
        if (seconds == null) {
            return "0 сек.";
        }

        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder result = new StringBuilder();

        if (hours > 0) {
            result.append(hours).append(" ч. ");
        }
        if (minutes > 0) {
            result.append(minutes).append(" мин. ");
        }
        if (secs > 0 || result.length() == 0) {
            result.append(secs).append(" сек.");
        }

        return result.toString().trim();
    }

}
