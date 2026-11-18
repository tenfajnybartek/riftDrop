package pl.tenfajnybartek.dropplugin.tasks;

import net.kyori.adventure.text.Component;
import pl.tenfajnybartek.dropplugin.base.DropPlugin;
import pl.tenfajnybartek.dropplugin.utils.ChatUtils;
import pl.tenfajnybartek.dropplugin.utils.DataUtils;

public final class ActionBarTask implements Runnable {
    private final DropPlugin plugin;

    public ActionBarTask(DropPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        var config = plugin.getPluginConfig();
        if (!config.isActionbarStatus()) return;
        long now = System.currentTimeMillis();
        var messages = new java.util.HashMap<Boosts, Component>();
        for (var player : plugin.getServer().getOnlinePlayers()) {
            var user = plugin.getUserManager().getUser(player);
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
        return ChatUtils.toComponent(template.replace("{TIME}", DataUtils.durationToString(expiry)));
    }

    private record Boosts(long drop, long exp) {}
}
