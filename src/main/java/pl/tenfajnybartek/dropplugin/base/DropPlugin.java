package pl.tenfajnybartek.dropplugin.base;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import pl.tenfajnybartek.dropplugin.commands.ADropCommand;
import pl.tenfajnybartek.dropplugin.commands.DropCommand;
import pl.tenfajnybartek.dropplugin.commands.LevelCommand;
import pl.tenfajnybartek.dropplugin.database.Database;
import pl.tenfajnybartek.dropplugin.listeners.BlockBreakListener;
import pl.tenfajnybartek.dropplugin.listeners.InventoryClickListener;
import pl.tenfajnybartek.dropplugin.listeners.PlayerJoinLeaveListener;
import pl.tenfajnybartek.dropplugin.listeners.UserLevelChangeListener;
import pl.tenfajnybartek.dropplugin.managers.ConfigManager;
import pl.tenfajnybartek.dropplugin.managers.DropConfigManager;
import pl.tenfajnybartek.dropplugin.managers.DropManager;
import pl.tenfajnybartek.dropplugin.managers.UserManager;
import pl.tenfajnybartek.dropplugin.tasks.ActionBarTask;
import pl.tenfajnybartek.dropplugin.tasks.SaverTask;
import pl.tenfajnybartek.dropplugin.utils.DropMenu;

import java.io.IOException;
import java.util.logging.Level;

public final class DropPlugin extends JavaPlugin {
    private record Settings(ConfigManager config, DropManager drops, DropMenu menu) {}

    private volatile Settings settings;
    private Database.Settings databaseSettings;
    private Database database;
    private UserManager users;
    private BukkitTask saver;
    private BukkitTask actionBar;
    private Runnable unregisterExpansion;

    @Override
    public void onEnable() {
        try {
            reloadConfigManager();
            databaseSettings = getPluginConfig().databaseSettings();
            database = new Database(databaseSettings, getDataFolder(), getLogger());
            users = new UserManager(database, this::getPluginConfig, () -> getDropManager().getDropList(), getLogger());
            var manager = getServer().getPluginManager();
            manager.registerEvents(new BlockBreakListener(this), this);
            manager.registerEvents(new InventoryClickListener(this), this);
            manager.registerEvents(new PlayerJoinLeaveListener(users), this);
            manager.registerEvents(new UserLevelChangeListener(this), this);
            new DropCommand(this);
            new ADropCommand(this);
            new LevelCommand(this);
            registerPlaceholderAPI();
            saver = getServer().getScheduler().runTaskTimer(this, new SaverTask(users, database), 1L, 20L);
            actionBar = getServer().getScheduler().runTaskTimer(this, new ActionBarTask(this), 20L, 20L);
        } catch (Exception | LinkageError failure) {
            getLogger().log(Level.SEVERE, "Nie udało się uruchomić DropPlugin.", failure);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (saver != null) saver.cancel();
        if (actionBar != null) actionBar.cancel();
        try {
            if (unregisterExpansion != null) unregisterExpansion.run();
        } catch (Exception | LinkageError failure) {
            getLogger().log(Level.WARNING, "Nie udało się wyrejestrować integracji PlaceholderAPI.", failure);
        }
        try {
            if (users != null) users.save();
        } finally {
            if (database != null) database.close();
        }
    }

    private void registerPlaceholderAPI() {
        if (!getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) return;
        try {
            var expansion = new pl.tenfajnybartek.dropplugin.integrations.DropPluginExpansion(this);
            if (expansion.register()) unregisterExpansion = () -> expansion.unregister();
            else getLogger().warning("PlaceholderAPI odrzuciło rejestrację DropPlugin.");
        } catch (Exception | LinkageError failure) {
            getLogger().log(Level.WARNING, "Integracja PlaceholderAPI jest niedostępna.", failure);
        }
    }

    public void reloadConfigManager() throws IOException, InvalidConfigurationException {
        ConfigManager config = new ConfigManager(this);
        DropManager drops = new DropManager(this, config, new DropConfigManager(this));
        DropMenu menu = new DropMenu(config, drops);
        if (users != null) users.migrateOnlineDrops();
        settings = new Settings(config, drops, menu);
        if (users != null) users.migrateOnlineDrops();
        if (databaseSettings != null && !databaseSettings.equals(config.databaseSettings())) {
            getLogger().warning("Zmieniono ustawienia bazy. Zaczną obowiązywać po restarcie serwera.");
        }
    }

    public UserManager getUserManager() { return users; }
    public Database getDatabase() { return database; }
    public ConfigManager getPluginConfig() { return settings.config(); }
    public DropManager getDropManager() { return settings.drops(); }
    public DropMenu getDropMenu() { return settings.menu(); }
}
