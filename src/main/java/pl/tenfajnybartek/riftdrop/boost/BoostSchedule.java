package pl.tenfajnybartek.riftdrop.boost;

import org.bukkit.configuration.ConfigurationSection;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public record BoostSchedule(ZoneId zone, List<Entry> entries) {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    public record Entry(String id, BoostType type, Set<DayOfWeek> days, LocalTime time, int minutes,
                        int announceBefore, String message) {}
    public record Window(Entry entry, Instant start, Instant end) {}

    public static BoostSchedule read(ConfigurationSection yaml) {
        if (!yaml.getBoolean("turbo-schedule.enabled", false)) return new BoostSchedule(ZoneOffset.UTC, List.of());
        ZoneId zone = ZoneId.of(yaml.getString("turbo-schedule.zone", "Europe/Warsaw"));
        var section = yaml.getConfigurationSection("turbo-schedule.events");
        if (section == null) throw new IllegalArgumentException("Brakuje turbo-schedule.events.");
        List<Entry> entries = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            try {
                var event = section.getConfigurationSection(id);
                if (event == null) throw new IllegalArgumentException("Wymagana sekcja.");
                var days = EnumSet.noneOf(DayOfWeek.class);
                for (String day : event.getStringList("days")) days.add(DayOfWeek.valueOf(day.toUpperCase(Locale.ROOT)));
                int minutes = event.getInt("duration-minutes");
                int before = event.getInt("announce-minutes", 10);
                if (days.isEmpty() || minutes < 1 || minutes > 1440 || before < 0 || before > 1440) {
                    throw new IllegalArgumentException("Wymagane days, duration-minutes 1–1440 i announce-minutes 0–1440.");
                }
                entries.add(new Entry(id, BoostType.parse(Objects.requireNonNull(event.getString("type"))),
                        Set.copyOf(days), LocalTime.parse(Objects.requireNonNull(event.getString("time")), TIME),
                        minutes, before, event.getString("message", "&6{TYPE} rozpocznie się o &f{START}&6.")));
            } catch (RuntimeException failure) {
                throw new IllegalArgumentException("config.yml: turbo-schedule.events." + id + ": " + failure.getMessage(), failure);
            }
        }
        return new BoostSchedule(zone, List.copyOf(entries));
    }

    public List<Window> windows(Instant now) {
        LocalDate today = now.atZone(zone).toLocalDate();
        List<Window> result = new ArrayList<>();
        for (int offset = -2; offset <= 7; offset++) {
            LocalDate day = today.plusDays(offset);
            for (Entry entry : entries) {
                if (!entry.days.contains(day.getDayOfWeek())) continue;
                Instant start = day.atTime(entry.time).atZone(zone).toInstant();
                result.add(new Window(entry, start, start.plus(Duration.ofMinutes(entry.minutes))));
            }
        }
        result.sort(Comparator.comparing(Window::start));
        return result;
    }
}
