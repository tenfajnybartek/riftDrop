package pl.tenfajnybartek.riftdrop.boost;

import java.util.Locale;

public enum BoostType {
    DROP("TurboDrop"), EXP("TurboExp");

    private final String label;

    BoostType(String label) { this.label = label; }
    public String label() { return label; }
    public String key() { return name().toLowerCase(Locale.ROOT); }

    public static BoostType parse(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
