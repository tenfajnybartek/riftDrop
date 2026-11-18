package pl.tenfajnybartek.dropplugin.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import pl.tenfajnybartek.dropplugin.base.DropPlugin;

public final class BlockBreakListener implements Listener {
    private final DropPlugin plugin;

    public BlockBreakListener(DropPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        plugin.getDropManager().breakBlock(event);
    }
}
