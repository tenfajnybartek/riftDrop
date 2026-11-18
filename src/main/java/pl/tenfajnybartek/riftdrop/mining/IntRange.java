package pl.tenfajnybartek.riftdrop.mining;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

public final class IntRange {
    private static final Pattern RANGE = Pattern.compile("(-?\\d+)(?:\\s*-\\s*(-?\\d+))?");
    private final int min;
    private final int max;

    public IntRange(int min, int max) {
        if (max < min) throw new IllegalArgumentException("Koniec zakresu jest mniejszy od początku.");
        this.min = min;
        this.max = max;
    }

    public int getMin() {
        return this.min;
    }

    public int getMax() {
        return this.max;
    }

    public int random() {
        if (this.min == this.max) return this.min;
        return (int) ThreadLocalRandom.current().nextLong(min, (long) max + 1);
    }

    @Override
    public String toString() {
        return this.min + "-" + this.max;
    }

    public static IntRange parse(String text) {
        if (text == null) throw new IllegalArgumentException("Brakuje zakresu.");
        var range = RANGE.matcher(text.trim());
        if (!range.matches()) throw new IllegalArgumentException("Nieprawidłowy zakres: " + text);
        int min = Integer.parseInt(range.group(1));
        return new IntRange(min, range.group(2) == null ? min : Integer.parseInt(range.group(2)));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IntRange)) return false;
        IntRange count = (IntRange) o;
        return min == count.min && max == count.max;
    }

    @Override
    public int hashCode() {
        return Objects.hash(min, max);
    }
}
