package pl.tenfajnybartek.riftdrop.mining;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import pl.tenfajnybartek.riftdrop.RiftDropPlugin;

public final class BlockBreakListener implements Listener {
    private final RiftDropPlugin plugin;

    public BlockBreakListener(RiftDropPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        plugin.mining().breakBlock(event);
    }
}
