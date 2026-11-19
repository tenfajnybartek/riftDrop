package pl.tenfajnybartek.riftdrop.boost;

import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BoostScheduleTest {
    @Test
    void saturdayInWarsawKeepsTheConfiguredLocalTimeAcrossDst() {
        var entry = new BoostSchedule.Entry("weekend", BoostType.DROP, Set.of(DayOfWeek.SATURDAY),
                LocalTime.of(18, 0), 120, 10, "");
        var schedule = new BoostSchedule(ZoneId.of("Europe/Warsaw"), List.of(entry));
        var winter = schedule.windows(Instant.parse("2026-03-28T17:30:00Z")).getFirst();
        var summer = schedule.windows(Instant.parse("2026-04-04T16:30:00Z")).getFirst();
        assertEquals(Instant.parse("2026-03-28T17:00:00Z"), winter.start());
        assertEquals(Instant.parse("2026-04-04T16:00:00Z"), summer.start());
        assertEquals(Duration.ofHours(2), Duration.between(summer.start(), summer.end()));
    }

    @Test
    void saturdayWindowContinuesPastMidnight() {
        var entry = new BoostSchedule.Entry("late", BoostType.EXP, Set.of(DayOfWeek.SATURDAY),
                LocalTime.of(23, 30), 90, 0, "");
        var schedule = new BoostSchedule(ZoneOffset.UTC, List.of(entry));
        var sunday = Instant.parse("2026-03-29T00:15:00Z");
        var active = schedule.windows(sunday).stream()
                .filter(window -> !window.start().isAfter(sunday) && window.end().isAfter(sunday))
                .findFirst().orElseThrow();
        assertEquals(Instant.parse("2026-03-29T01:00:00Z"), active.end());
    }
}
