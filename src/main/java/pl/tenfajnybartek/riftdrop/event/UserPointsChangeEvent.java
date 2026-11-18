package pl.tenfajnybartek.riftdrop.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import pl.tenfajnybartek.riftdrop.profile.MiningProfile;

public class UserPointsChangeEvent extends Event {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final int level;
    private final int levelPoints;
    private final Player player;
    private final MiningProfile user;

    public UserPointsChangeEvent(MiningProfile user) {
        this.user = user;
        this.level = user.getLvl();
        this.levelPoints = user.getPoints();
        this.player = user.getPlayer();
    }

    public int getLevel() {
        return this.level;
    }

    public int getLevelPoints() {
        return this.levelPoints;
    }

    public Player getPlayer() {
        return this.player;
    }

    public MiningProfile getUser() {
        return this.user;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
