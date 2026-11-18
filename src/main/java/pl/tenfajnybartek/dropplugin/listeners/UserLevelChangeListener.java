package pl.tenfajnybartek.dropplugin.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import pl.tenfajnybartek.dropplugin.base.DropPlugin;
import pl.tenfajnybartek.dropplugin.managers.ConfigManager;
import pl.tenfajnybartek.dropplugin.utils.ChatUtils;
import pl.tenfajnybartek.dropplugin.events.UserLevelChangeEvent;

public class UserLevelChangeListener implements Listener {
    private final DropPlugin plugin;

    public UserLevelChangeListener(DropPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void userLevelChange(UserLevelChangeEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        
        int lvl = event.getLevel();
        ConfigManager config = plugin.getPluginConfig();

        if (lvl == config.getMaxLevel()) {
            String msgAll = config.getMaxLevelMessage()
                    .replace("{PLAYER}", player.getName())
                    .replace("{LVL}", String.valueOf(lvl));
            Bukkit.getOnlinePlayers().forEach(all -> ChatUtils.sendMessage(all, msgAll));
        } else if (config.getChatLevels().contains(lvl)) {
            String msgAll = config.getLevelMessage()
                    .replace("{PLAYER}", player.getName())
                    .replace("{LVL}", String.valueOf(lvl));
            Bukkit.getOnlinePlayers().forEach(all -> ChatUtils.sendMessage(all, msgAll));
        }

        String personal = config.getLvlUpMessage()
                .replace("{LVL}", String.valueOf(lvl))
                .replace("{POINTS}", String.valueOf(event.getUser().getPointsRequired()));
        ChatUtils.sendMessage(player, personal);

        if (player.getLocation() != null && player.getWorld() != null) {
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 2.0f, 2.0f);
        }
    }
}
