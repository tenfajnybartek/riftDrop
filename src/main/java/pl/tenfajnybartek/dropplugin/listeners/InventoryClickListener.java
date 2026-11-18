package pl.tenfajnybartek.dropplugin.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import pl.tenfajnybartek.dropplugin.base.DropPlugin;
import pl.tenfajnybartek.dropplugin.utils.DropMenu;

public final class InventoryClickListener implements Listener {
    private final DropPlugin plugin;

    public InventoryClickListener(DropPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        var top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof DropMenu.Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != top) return;
        if (event.isShiftClick() || (!event.isLeftClick() && !event.isRightClick())) return;
        DropMenu current = plugin.getDropMenu();
        if (!holder.belongsTo(current)) {
            refresh(player, top);
            return;
        }
        var user = plugin.getUserManager().getUser(player);
        if (user != null && holder.apply(event.getRawSlot(), user)) refresh(player, top);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        var top = event.getView().getTopInventory();
        if (top.getHolder() instanceof DropMenu.Holder && event.getRawSlots().stream().anyMatch(slot -> slot < top.getSize())) {
            event.setCancelled(true);
        }
    }

    private void refresh(Player player, org.bukkit.inventory.Inventory oldInventory) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory() == oldInventory) {
                var user = plugin.getUserManager().getUser(player);
                if (user != null) player.openInventory(plugin.getDropMenu().createInventory(player, user));
            }
        });
    }
}
