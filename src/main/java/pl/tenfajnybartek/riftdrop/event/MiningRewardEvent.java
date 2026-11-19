package pl.tenfajnybartek.riftdrop.event;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Synchronous event for a custom reward after a successful block break.
 * Cancellation skips this reward's item, XP and mining statistics.
 */
public final class MiningRewardEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    public record Source(UUID worldId, int x, int y, int z, Material material) {}

    private final UUID playerId;
    private final Source source;
    private final String dropId;
    private final ItemStack itemTemplate;
    private final int amount;
    private final int experience;
    private final int points;
    private final int requiredLevel;
    private boolean cancelled;

    public MiningRewardEvent(UUID playerId, Source source, String dropId, ItemStack itemTemplate,
                             int amount, int experience, int points, int requiredLevel) {
        this.playerId = playerId;
        this.source = source;
        this.dropId = dropId;
        this.itemTemplate = itemTemplate.clone();
        this.amount = amount;
        this.experience = experience;
        this.points = points;
        this.requiredLevel = requiredLevel;
    }

    public UUID getPlayerId() { return playerId; }
    public Source getSource() { return source; }
    public String getDropId() { return dropId; }
    public ItemStack getItemTemplate() { return itemTemplate.clone(); }
    public int getAmount() { return amount; }
    public int getExperience() { return experience; }
    public int getPoints() { return points; }
    public int getRequiredLevel() { return requiredLevel; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
