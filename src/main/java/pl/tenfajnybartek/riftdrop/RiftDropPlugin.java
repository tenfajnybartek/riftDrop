package pl.tenfajnybartek.riftdrop;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import pl.tenfajnybartek.riftdrop.command.AdminDropCommand;
import pl.tenfajnybartek.riftdrop.command.DropCommand;
import pl.tenfajnybartek.riftdrop.command.LevelCommand;
import pl.tenfajnybartek.riftdrop.storage.ProfileDatabase;
import pl.tenfajnybartek.riftdrop.mining.BlockBreakListener;
import pl.tenfajnybartek.riftdrop.menu.MenuListener;
import pl.tenfajnybartek.riftdrop.profile.PlayerSessionListener;
import pl.tenfajnybartek.riftdrop.profile.LevelNotificationListener;
import pl.tenfajnybartek.riftdrop.configuration.PluginConfiguration;
import pl.tenfajnybartek.riftdrop.configuration.DropConfiguration;
import pl.tenfajnybartek.riftdrop.mining.MiningService;
import pl.tenfajnybartek.riftdrop.profile.PlayerProfiles;
import pl.tenfajnybartek.riftdrop.profile.BoostActionBar;
import pl.tenfajnybartek.riftdrop.profile.ProfileMaintenance;
import pl.tenfajnybartek.riftdrop.menu.DropMenu;

import pl.tenfajnybartek.riftdrop.configuration.LegacyDataMigration;
import pl.tenfajnybartek.riftdrop.integration.RiftDropExpansion;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

public final class RiftDropPlugin extends JavaPlugin {
    private record Settings(PluginConfiguration config, MiningService drops, DropMenu menu) {}

    private volatile Settings settings;
    private ProfileDatabase.Settings databaseSettings;
    private ProfileDatabase database;
    private PlayerProfiles users;
    private BukkitTask saver;
    private BukkitTask actionBar;
    private final List<Runnable> unregisterExpansions = new ArrayList<>();

    @Override
    public void onEnable() {
        try {
            if (Arrays.stream(getServer().getPluginManager().getPlugins()).anyMatch(plugin -> plugin.getName().equalsIgnoreCase("DropPlugin"))) {
                throw new IllegalStateException("Usuń stary plik DropPlugin JAR przed uruchomieniem riftDrop.");
            }
            if (LegacyDataMigration.copyIfNeeded(getDataFolder().toPath())) {
                getLogger().info("Skopiowano dane z plugins/DropPlugin do plugins/riftDrop.");
            }
            reloadSettings();
            databaseSettings = configuration().databaseSettings();
            database = new ProfileDatabase(databaseSettings, getDataFolder(), getLogger());
            users = new PlayerProfiles(database, this::configuration, () -> mining().rules(), getLogger());
            var manager = getServer().getPluginManager();
            manager.registerEvents(new BlockBreakListener(this), this);
            manager.registerEvents(new MenuListener(this), this);
            manager.registerEvents(new PlayerSessionListener(users), this);
            manager.registerEvents(new LevelNotificationListener(this), this);
            new DropCommand(this);
            new AdminDropCommand(this);
            new LevelCommand(this);
            registerPlaceholderAPI();
            saver = getServer().getScheduler().runTaskTimer(this, new ProfileMaintenance(users, database), 1L, 20L);
            actionBar = getServer().getScheduler().runTaskTimer(this, new BoostActionBar(this), 20L, 20L);
        } catch (Exception | LinkageError failure) {
            getLogger().log(Level.SEVERE, "Nie udało się uruchomić riftDrop.", failure);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (saver != null) saver.cancel();
        if (actionBar != null) actionBar.cancel();
        for (Runnable unregister : unregisterExpansions) {
            try {
                unregister.run();
            } catch (Exception | LinkageError failure) {
                getLogger().log(Level.WARNING, "Nie udało się wyrejestrować integracji PlaceholderAPI.", failure);
            }
        }
        unregisterExpansions.clear();
        try {
            if (users != null) users.save();
        } finally {
            if (database != null) database.close();
        }
    }

    private void registerPlaceholderAPI() {
        if (!getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) return;
        for (String identifier : List.of("riftdrop", "dropplugin")) {
            try {
                var expansion = new RiftDropExpansion(this, identifier);
                if (expansion.register()) unregisterExpansions.add(expansion::unregister);
                else getLogger().warning("PlaceholderAPI odrzuciło rejestrację: " + identifier);
            } catch (Exception | LinkageError failure) {
                getLogger().log(Level.WARNING, "Nie udało się zarejestrować PlaceholderAPI dla " + identifier, failure);
            }
        }
    }

    public void reloadSettings() throws IOException, InvalidConfigurationException {
        PluginConfiguration config = new PluginConfiguration(this);
        MiningService drops = new MiningService(this, config, DropConfiguration.load(this));
        DropMenu menu = new DropMenu(config, drops);
        if (users != null) users.migrateOnlineDrops();
        settings = new Settings(config, drops, menu);
        if (users != null) users.migrateOnlineDrops();
        if (databaseSettings != null && !databaseSettings.equals(config.databaseSettings())) {
            getLogger().warning("Zmieniono ustawienia bazy. Zaczną obowiązywać po restarcie serwera.");
        }
    }

    public PlayerProfiles profiles() { return users; }
    public ProfileDatabase database() { return database; }
    public PluginConfiguration configuration() { return settings.config(); }
    public MiningService mining() { return settings.drops(); }
    public DropMenu menu() { return settings.menu(); }
}
