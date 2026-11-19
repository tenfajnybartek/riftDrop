package pl.tenfajnybartek.riftdrop.boost;

import pl.tenfajnybartek.riftdrop.RiftDropPlugin;
import pl.tenfajnybartek.riftdrop.text.Messages;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class BoostService implements Runnable {
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm z");
    private final RiftDropPlugin plugin;
    private List<BoostSchedule.Window> windows = List.of();
    private Instant checkedAt = Instant.now();

    public BoostService(RiftDropPlugin plugin) { this.plugin = plugin; }

    public void reload() {
        checkedAt = Instant.now();
        windows = plugin.configuration().boostSchedule().windows(checkedAt);
    }

    public long manualExpiry(BoostType type) {
        var config = plugin.configuration();
        return type == BoostType.DROP ? config.getTurboDrop() : config.getTurboExp();
    }

    public long scheduledExpiry(BoostType type) {
        Instant now = Instant.now();
        long cutoff = plugin.configuration().scheduleCancelledAt(type);
        long until = 0;
        for (var window : windows) {
            if (window.entry().type() != type || window.start().isAfter(now)
                    || window.start().toEpochMilli() <= cutoff || !window.end().isAfter(now)) continue;
            until = Math.max(until, window.end().toEpochMilli());
        }
        return until;
    }

    public long globalExpiry(BoostType type) {
        return Math.max(manualExpiry(type), scheduledExpiry(type));
    }

    public String nextStart(BoostType type) {
        Instant now = Instant.now();
        return windows.stream().filter(window -> window.entry().type() == type && window.start().isAfter(now))
                .findFirst().map(window -> format(window.start())).orElse("brak");
    }

    private String format(Instant timestamp) {
        return DISPLAY_TIME.withZone(plugin.configuration().boostSchedule().zone()).format(timestamp);
    }

    @Override public void run() {
        Instant now = Instant.now();
        windows = plugin.configuration().boostSchedule().windows(now);
        for (var window : windows) {
            Instant announce = window.start().minus(Duration.ofMinutes(window.entry().announceBefore()));
            if (!announce.isAfter(checkedAt) || announce.isAfter(now) || !window.end().isAfter(now)
                    || window.start().toEpochMilli() <= plugin.configuration().scheduleCancelledAt(window.entry().type())) continue;
            String message = window.entry().message().replace("{TYPE}", window.entry().type().label())
                    .replace("{START}", format(window.start())).replace("{ID}", window.entry().id())
                    .replace("{MINUTES}", Integer.toString(window.entry().minutes()));
            plugin.getServer().getOnlinePlayers().forEach(player -> Messages.sendMessage(player, message));
        }
        if (now.isAfter(checkedAt)) checkedAt = now;
    }
}
