package pl.tenfajnybartek.riftdrop.profile;

import pl.tenfajnybartek.riftdrop.mining.DropRule;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pl.tenfajnybartek.riftdrop.event.UserLevelChangeEvent;
import pl.tenfajnybartek.riftdrop.event.UserPointsChangeEvent;
import pl.tenfajnybartek.riftdrop.configuration.PluginConfiguration;

import java.util.*;
import java.util.function.Supplier;

public final class MiningProfile {
    public record Profile(UUID id, String name, boolean cobble, boolean messages, long turboDrop, long turboExp,
                          int level, long points, Map<String, Integer> minedDrops, Set<String> disabledDrops) {
        public Profile {
            minedDrops = Map.copyOf(minedDrops);
            disabledDrops = Set.copyOf(disabledDrops);
        }

        public static Profile initial(UUID id) {
            return new Profile(id, null, true, true, 0, 0, 1, 0, Map.of(), Set.of());
        }
    }

    private final UUID identifier;
    private String name;
    private final Supplier<PluginConfiguration> settings;
    private final Map<String, Integer> minedDrops;
    private final Set<String> disabledDrops;
    private boolean cobble;
    private boolean messages;
    private long turboDrop;
    private long turboExp;
    private int points;
    private int level;
    private boolean dirty;

    public MiningProfile(Profile profile, Supplier<PluginConfiguration> settings) {
        this.identifier = profile.id();
        this.name = profile.name();
        this.settings = settings;
        this.minedDrops = new HashMap<>(profile.minedDrops());
        this.disabledDrops = new HashSet<>(profile.disabledDrops());
        this.cobble = profile.cobble();
        this.messages = profile.messages();
        this.turboDrop = profile.turboDrop();
        this.turboExp = profile.turboExp();
        this.level = Math.max(1, profile.level());
        this.points = (int) Math.clamp(profile.points(), 0, Integer.MAX_VALUE);
    }

    public Profile snapshot() {
        return new Profile(identifier, name, cobble, messages, turboDrop, turboExp, level, points, minedDrops, disabledDrops);
    }

    public void rememberName(String name) {
        if (!Objects.equals(this.name, name)) {
            this.name = name;
            dirty = true;
        }
    }

    public Profile takeChangedSnapshot() {
        if (!dirty) return null;
        dirty = false;
        return snapshot();
    }

    public void migrateDrops(List<DropRule> drops) {
        Map<String, Long> counts = new HashMap<>();
        for (DropRule drop : drops) counts.merge(drop.getName(), 1L, Long::sum);
        for (DropRule drop : drops) {
            String name = drop.getName();
            if (counts.get(name) != 1) continue;
            String oldKey = "legacy:" + name;
            if (disabledDrops.remove(oldKey)) {
                disabledDrops.add(drop.getStorageKey());
                dirty = true;
            }
            if (minedDrops.containsKey(oldKey) && !minedDrops.containsKey(drop.getStorageKey())) {
                minedDrops.put(drop.getStorageKey(), minedDrops.remove(oldKey));
                dirty = true;
            }
        }
    }

    public UUID getIdentifier() { return identifier; }
    public boolean isCobble() { return cobble; }
    public boolean isMessages() { return messages; }
    public int getLvl() { return level; }
    public int getPoints() { return points; }
    public long getTurboDrop() { return turboDrop; }
    public long getTurboExp() { return turboExp; }
    public boolean isTurboDrop() { return turboDrop > System.currentTimeMillis(); }
    public boolean isTurboExp() { return turboExp > System.currentTimeMillis(); }
    public Player getPlayer() { return Bukkit.getPlayer(identifier); }

    public void setMessages(boolean enabled) {
        messages = enabled;
        dirty = true;
    }

    public void setCobble(boolean enabled) {
        cobble = enabled;
        dirty = true;
    }

    public void addTurboExp(long expiry) {
        turboExp = expiry;
        dirty = true;
    }

    public void addTurboDrop(long expiry) {
        turboDrop = expiry;
        dirty = true;
    }

    public void setProgress(int level, int points) {
        this.level = level;
        this.points = points;
        this.dirty = true;
    }

    public int getPointsRequired() {
        PluginConfiguration config = settings.get();
        return pointsRequired(level, config);
    }

    public static int pointsRequired(int level, PluginConfiguration config) {
        if (!config.isLvlStatus() || level >= config.getMaxLevel()) return 0;
        return (int) Math.min((long) level * config.getPointsToLvlUp(), Integer.MAX_VALUE);
    }

    public int getPointsToNextLevel() {
        return Math.max(0, getPointsRequired() - points);
    }

    public int addPoints(int earned) {
        PluginConfiguration config = settings.get();
        if (!config.isLvlStatus() || level >= config.getMaxLevel() || earned <= 0) return 0;
        long remaining = (long) points + earned;
        int previous = level;
        while (level < config.getMaxLevel() && remaining >= pointsRequired(level, config)) {
            remaining -= pointsRequired(level, config);
            level++;
        }
        points = level >= config.getMaxLevel() ? 0 : (int) remaining;
        dirty = true;
        Bukkit.getPluginManager().callEvent(new UserPointsChangeEvent(this));
        if (level != previous) Bukkit.getPluginManager().callEvent(new UserLevelChangeEvent(this));
        return level < config.getMaxLevel() ? earned : (int) Math.max(0, earned - remaining);
    }

    public int getDrop(DropRule drop) {
        return minedDrops.getOrDefault(drop.getStorageKey(), 0);
    }

    public void addDrop(DropRule drop, int amount) {
        minedDrops.put(drop.getStorageKey(), (int) Math.min((long) getDrop(drop) + amount, Integer.MAX_VALUE));
        dirty = true;
    }

    public boolean isDisabled(DropRule drop) {
        return disabledDrops.contains(drop.getStorageKey());
    }

    public void setDropEnabled(DropRule drop, boolean enabled) {
        boolean changed = enabled ? disabledDrops.remove(drop.getStorageKey()) : disabledDrops.add(drop.getStorageKey());
        dirty |= changed;
    }
}
