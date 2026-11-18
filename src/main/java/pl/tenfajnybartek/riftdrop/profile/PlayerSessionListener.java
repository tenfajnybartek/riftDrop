package pl.tenfajnybartek.riftdrop.profile;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerSessionListener implements Listener {
    private final PlayerProfiles users;

    public PlayerSessionListener(PlayerProfiles users) {
        this.users = users;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        users.getUser(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        users.quit(event.getPlayer().getUniqueId());
    }
}
