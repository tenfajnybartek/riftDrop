package pl.tenfajnybartek.riftdrop.mining;

import pl.tenfajnybartek.riftdrop.text.Messages;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pl.tenfajnybartek.riftdrop.configuration.PluginConfiguration;

public final class RewardDelivery {
    private RewardDelivery() {}

    public static void giveItem(Player player, Location origin, ItemStack item, int amount, PluginConfiguration config) {
        boolean overflow = false;
        int remaining = amount;
        while (remaining > 0) {
            ItemStack stack = item.clone();
            int stackSize = Math.min(remaining, stack.getMaxStackSize());
            stack.setAmount(stackSize);
            remaining -= stackSize;
            if (config.isToInv()) {
                var leftovers = player.getInventory().addItem(stack);
                if (!leftovers.isEmpty()) {
                    overflow = true;
                    leftovers.values().forEach(left -> origin.getWorld().dropItemNaturally(origin, left));
                }
            } else {
                origin.getWorld().dropItemNaturally(origin, stack);
            }
        }
        if (overflow && config.isMessageInv()) Messages.sendMessage(player, config.getMessageInvFull());
    }
}
