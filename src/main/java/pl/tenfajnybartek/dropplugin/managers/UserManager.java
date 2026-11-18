package pl.tenfajnybartek.dropplugin.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pl.tenfajnybartek.dropplugin.database.Database;
import pl.tenfajnybartek.dropplugin.objects.Drop;
import pl.tenfajnybartek.dropplugin.objects.User;
import pl.tenfajnybartek.dropplugin.objects.User.Profile;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class UserManager {
    public record Progress(int level, int points) {}
    private final Database database;
    private final Supplier<ConfigManager> settings;
    private final Supplier<List<Drop>> drops;
    private final Logger logger;
    private final Map<UUID, User> users = new HashMap<>();
    private final Map<UUID, Future<Profile>> loading = new HashMap<>();
    private final Map<UUID, Long> retryAfter = new HashMap<>();
    private volatile Map<UUID, Progress> placeholderProfiles = Map.of();

    public UserManager(Database database, Supplier<ConfigManager> settings,
                       Supplier<List<Drop>> drops, Logger logger) {
        this.database = database;
        this.settings = settings;
        this.drops = drops;
        this.logger = logger;
    }

    public User getUser(Player player) {
        UUID id = player.getUniqueId();
        User user = users.get(id);
        if (user != null) return user;

        Future<Profile> load = loading.get(id);
        if (load == null && System.currentTimeMillis() >= retryAfter.getOrDefault(id, 0L)) {
            loading.put(id, database.load(id));
        } else if (load != null && load.isDone()) {
            loading.remove(id);
            try {
                user = new User(load.get(), settings);
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
        for (User user : users.values()) user.migrateDrops(definitions);
    }

    public void quit(UUID id) {
        Future<Profile> load = loading.remove(id);
        if (load != null) load.cancel(false);
        retryAfter.remove(id);
        User user = users.remove(id);
        if (user != null) database.save(List.of(user.snapshot()));
    }

    public void save() {
        List<Profile> changed = new ArrayList<>();
        for (User user : users.values()) {
            Profile snapshot = user.takeChangedSnapshot();
            if (snapshot != null) changed.add(snapshot);
        }
        database.save(changed);
    }
}
