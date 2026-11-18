package pl.tenfajnybartek.dropplugin.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pl.tenfajnybartek.dropplugin.managers.UserManager;

public final class PlayerJoinLeaveListener implements Listener {
    private final UserManager users;

    public PlayerJoinLeaveListener(UserManager users) {
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
