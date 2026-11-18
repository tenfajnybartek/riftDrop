package pl.tenfajnybartek.riftdrop.profile;

import net.kyori.adventure.text.Component;
import pl.tenfajnybartek.riftdrop.RiftDropPlugin;
import pl.tenfajnybartek.riftdrop.text.Messages;
import pl.tenfajnybartek.riftdrop.text.BoostTime;

public final class BoostActionBar implements Runnable {
    private final RiftDropPlugin plugin;

    public BoostActionBar(RiftDropPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        var config = plugin.configuration();
        if (!config.isActionbarStatus()) return;
        long now = System.currentTimeMillis();
        var messages = new java.util.HashMap<Boosts, Component>();
        for (var player : plugin.getServer().getOnlinePlayers()) {
            var user = plugin.profiles().getUser(player);
            long drop = Math.max(config.getTurboDrop(), user == null ? 0 : user.getTurboDrop());
            long exp = Math.max(config.getTurboExp(), user == null ? 0 : user.getTurboExp());
            if (drop <= now && exp <= now) continue;
            var boost = new Boosts(drop > now ? drop : 0, exp > now ? exp : 0);
            var message = messages.computeIfAbsent(boost, active -> {
                Component dropText = format(config.getActionbarMessage(), active.drop());
                Component expText = format(config.getActionbarExpMessage(), active.exp());
                if (dropText.equals(Component.empty())) return expText;
                if (expText.equals(Component.empty())) return dropText;
                return dropText.append(Component.text("  |  ")).append(expText);
            });
            if (!message.equals(Component.empty())) player.sendActionBar(message);
        }
    }

    private static Component format(String template, long expiry) {
        if (expiry == 0 || template.isBlank()) return Component.empty();
        return Messages.toComponent(template.replace("{TIME}", BoostTime.durationToString(expiry)));
    }

    private record Boosts(long drop, long exp) {}
}
