package pl.tenfajnybartek.riftdrop.text;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BoostTime {
    private static final Pattern TIME = Pattern.compile(
            "(?:(\\d+)\\s*y(?:ears?)?[,\\s]*)?"
                    + "(?:(\\d+)\\s*mo(?:nths?)?[,\\s]*)?"
                    + "(?:(\\d+)\\s*w(?:eeks?)?[,\\s]*)?"
                    + "(?:(\\d+)\\s*d(?:ays?)?[,\\s]*)?"
                    + "(?:(\\d+)\\s*h(?:ours?)?[,\\s]*)?"
                    + "(?:(\\d+)\\s*m(?:in(?:utes?)?)?[,\\s]*)?"
                    + "(?:(\\d+)\\s*(?:s(?:ec(?:onds?)?)?)?)?", Pattern.CASE_INSENSITIVE);

    private BoostTime() {}

    public static String durationToString(long time) {
        long diff = time - System.currentTimeMillis();
        if (diff <= 0L) {
            return "0s";
        }
        StringBuilder sb = new StringBuilder();
        long days = diff / 86_400_000L;
        long hours = (diff / 3_600_000L) % 24L;
        long minutes = (diff / 60_000L) % 60L;
        long seconds = (diff / 1000L) % 60L;
        if (days > 0L) {
            sb.append(days).append("d");
        }
        if (hours > 0L) {
            sb.append(hours).append("h");
        }
        if (minutes > 0L) {
            sb.append(minutes).append("min");
        }
        if (seconds > 0L) {
            sb.append(seconds).append("s");
        }
        return sb.isEmpty() ? "1s" : sb.toString();
    }

    public static long parseDateDiff(String time, boolean future) {
        if (time == null || time.isBlank()) return -1L;
        try {
            Matcher matcher = TIME.matcher(time.trim());
            if (!matcher.matches()) return -1L;
            int[] amounts = new int[7];
            boolean nonZero = false;
            for (int index = 0; index < amounts.length; index++) {
                String value = matcher.group(index + 1);
                if (value != null) {
                    amounts[index] = Integer.parseInt(value);
                    nonZero |= amounts[index] > 0;
                }
            }
            if (!nonZero) return -1L;
            int[] units = {Calendar.YEAR, Calendar.MONTH, Calendar.WEEK_OF_YEAR, Calendar.DAY_OF_MONTH,
                    Calendar.HOUR_OF_DAY, Calendar.MINUTE, Calendar.SECOND};

            GregorianCalendar c = new GregorianCalendar();
            int sign = future ? 1 : -1;
            for (int index = 0; index < amounts.length; index++) {
                if (amounts[index] > 0) c.add(units[index], amounts[index] * sign);
            }

            GregorianCalendar max = new GregorianCalendar();
            max.add(Calendar.YEAR, 10);
            if (future && c.after(max)) {
                return max.getTimeInMillis();
            }
            return c.getTimeInMillis();
        } catch (IllegalArgumentException invalid) {
            return -1L;
        }
    }
}
