package pl.tenfajnybartek.dropplugin.integrations;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pl.tenfajnybartek.dropplugin.base.DropPlugin;
import pl.tenfajnybartek.dropplugin.database.Database;
import pl.tenfajnybartek.dropplugin.objects.User;

import java.util.Locale;

public final class DropPluginExpansion extends PlaceholderExpansion {
    private final DropPlugin plugin;

    public DropPluginExpansion(DropPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() { return "dropplugin"; }

    @Override
    public @NotNull String getAuthor() { return "tenfajnybartek"; }

    @Override
    public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); }

    @Override
    public boolean persist() { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        String key = params.toLowerCase(Locale.ROOT);
        var config = plugin.getPluginConfig();
        if (key.startsWith("toplevel-")) {
            int position;
            try {
                position = Integer.parseInt(key.substring("toplevel-".length()));
            } catch (NumberFormatException invalid) {
                return config.getToplevelNoPlayer();
            }
            var ranking = plugin.getDatabase().ranking();
            if (position < 1 || position > Database.TOP_LIMIT || position > ranking.size()) return config.getToplevelNoPlayer();
            var rank = ranking.get(position - 1);
            return config.getToplevelFormat().replace("{LEVEL}", Integer.toString(rank.level()))
                    .replace("{NAME}", rank.name());
        }

        if (player == null) return null;
        var progress = plugin.getUserManager().getPlaceholderProfile(player.getUniqueId());
        int level = progress == null ? 0 : progress.level();
        int points = progress == null ? 0 : progress.points();
        int required = progress == null ? 0 : User.pointsRequired(level, config);
        return switch (key) {
            case "level" -> Integer.toString(level);
            case "points" -> Integer.toString(points);
            case "points_required" -> Integer.toString(required);
            case "points_to_next" -> Integer.toString(Math.max(0, required - points));
            default -> null;
        };
    }
}
