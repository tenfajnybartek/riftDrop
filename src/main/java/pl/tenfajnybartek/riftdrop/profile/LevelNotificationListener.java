package pl.tenfajnybartek.riftdrop.profile;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import pl.tenfajnybartek.riftdrop.RiftDropPlugin;
import pl.tenfajnybartek.riftdrop.configuration.PluginConfiguration;
import pl.tenfajnybartek.riftdrop.text.Messages;
import pl.tenfajnybartek.riftdrop.event.UserLevelChangeEvent;

public class LevelNotificationListener implements Listener {
    private final RiftDropPlugin plugin;

    public LevelNotificationListener(RiftDropPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void userLevelChange(UserLevelChangeEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        
        int lvl = event.getLevel();
        PluginConfiguration config = plugin.configuration();

        if (lvl == config.getMaxLevel()) {
            String msgAll = config.getMaxLevelMessage()
                    .replace("{PLAYER}", player.getName())
                    .replace("{LVL}", String.valueOf(lvl));
            Bukkit.getOnlinePlayers().forEach(all -> Messages.sendMessage(all, msgAll));
        } else if (config.getChatLevels().contains(lvl)) {
            String msgAll = config.getLevelMessage()
                    .replace("{PLAYER}", player.getName())
                    .replace("{LVL}", String.valueOf(lvl));
            Bukkit.getOnlinePlayers().forEach(all -> Messages.sendMessage(all, msgAll));
        }

        String personal = config.getLvlUpMessage()
                .replace("{LVL}", String.valueOf(lvl))
                .replace("{POINTS}", String.valueOf(event.getUser().getPointsRequired()));
        Messages.sendMessage(player, personal);

        if (player.getLocation() != null && player.getWorld() != null) {
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 2.0f, 2.0f);
        }
    }
}
