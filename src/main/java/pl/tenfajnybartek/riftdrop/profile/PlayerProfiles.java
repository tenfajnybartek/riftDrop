package pl.tenfajnybartek.riftdrop.profile;

import pl.tenfajnybartek.riftdrop.configuration.PluginConfiguration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pl.tenfajnybartek.riftdrop.storage.ProfileDatabase;
import pl.tenfajnybartek.riftdrop.mining.DropRule;
import pl.tenfajnybartek.riftdrop.profile.MiningProfile.Profile;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PlayerProfiles {
    public record Progress(int level, int points) {}
    private final ProfileDatabase database;
    private final Supplier<PluginConfiguration> settings;
    private final Supplier<List<DropRule>> drops;
    private final Logger logger;
    private final Map<UUID, MiningProfile> users = new HashMap<>();
    private final Map<UUID, Future<Profile>> loading = new HashMap<>();
    private final Map<UUID, Long> retryAfter = new HashMap<>();
    private volatile Map<UUID, Progress> placeholderProfiles = Map.of();

    public PlayerProfiles(ProfileDatabase database, Supplier<PluginConfiguration> settings,
                       Supplier<List<DropRule>> drops, Logger logger) {
        this.database = database;
        this.settings = settings;
        this.drops = drops;
        this.logger = logger;
    }

    public MiningProfile getUser(Player player) {
        UUID id = player.getUniqueId();
        MiningProfile user = users.get(id);
        if (user != null) return user;

        Future<Profile> load = loading.get(id);
        if (load == null && System.currentTimeMillis() >= retryAfter.getOrDefault(id, 0L)) {
            loading.put(id, database.load(id));
        } else if (load != null && load.isDone()) {
            loading.remove(id);
            try {
                user = new MiningProfile(load.get(), settings);
                user.migrateDrops(drops.get());
                user.rememberName(player.getName());
                users.put(id, user);
                retryAfter.remove(id);
                return user;
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException failure) {
                retryAfter.put(id, System.currentTimeMillis() + 10_000L);
                logger.log(Level.WARNING, "Nie udało się wczytać profilu " + id, failure.getCause());
            }
        }
        return null;
    }

    public Progress getPlaceholderProfile(UUID id) {
        return placeholderProfiles.get(id);
    }

    public void refreshOnlineProfiles() {
        for (Player player : Bukkit.getOnlinePlayers()) getUser(player);
        Map<UUID, Progress> snapshots = new HashMap<>();
        users.forEach((id, user) -> snapshots.put(id, new Progress(user.getLvl(), user.getPoints())));
        placeholderProfiles = Map.copyOf(snapshots);
    }

    public void migrateOnlineDrops() {
        var definitions = drops.get();
        for (MiningProfile user : users.values()) user.migrateDrops(definitions);
    }

    public void quit(UUID id) {
        Future<Profile> load = loading.remove(id);
        if (load != null) load.cancel(false);
        retryAfter.remove(id);
        MiningProfile user = users.remove(id);
        if (user != null) database.save(List.of(user.snapshot()));
    }

    public void save() {
        List<Profile> changed = new ArrayList<>();
        for (MiningProfile user : users.values()) {
            Profile snapshot = user.takeChangedSnapshot();
            if (snapshot != null) changed.add(snapshot);
        }
        database.save(changed);
    }
}
