package pl.tenfajnybartek.dropplugin.base;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import pl.tenfajnybartek.dropplugin.commands.ADropCommand;
import pl.tenfajnybartek.dropplugin.commands.DropCommand;
import pl.tenfajnybartek.dropplugin.commands.LevelCommand;
import pl.tenfajnybartek.dropplugin.database.Database;
import pl.tenfajnybartek.dropplugin.listeners.*;
import pl.tenfajnybartek.dropplugin.managers.ConfigManager;
import pl.tenfajnybartek.dropplugin.managers.DropConfigManager;
import pl.tenfajnybartek.dropplugin.managers.DropManager;
import pl.tenfajnybartek.dropplugin.managers.UserManager;
import pl.tenfajnybartek.dropplugin.tasks.ActionBarTask;
import pl.tenfajnybartek.dropplugin.tasks.SaverTask;
import pl.tenfajnybartek.dropplugin.utils.DropMenu;

import java.util.logging.Logger;

public class DropPlugin extends JavaPlugin {
    private Logger logger;

    private UserManager userManager;
    private DropManager dropManager;
    private Database database;
    private ConfigManager configManager;
    private DropConfigManager dropConfig;
    private SaverTask saverTask;
    private ActionBarTask actionBarTask;
    private DropMenu dropMenu;
    private boolean forceDisable = false;

    @Override
    public void onEnable() {
        this.logger = this.getLogger();

        try {
            long startTime = System.currentTimeMillis();
            this.logger.info("Initializacja pluginu tfbDrop...");

            this.configManager = new ConfigManager(this);
            this.dropConfig = new DropConfigManager(this);
            this.userManager = new UserManager();
            this.database = new Database(this);
            this.dropManager = new DropManager(this);
            this.dropMenu = new DropMenu(this);
            this.saverTask = new SaverTask(this);
            this.actionBarTask = new ActionBarTask(this);
            registerListeners();
            registerCommands();
            registerPlaceholderAPI();

            this.logger.info("Zaladowano plugin tfbDrop w " + (double)(System.currentTimeMillis() - startTime) / 1000.0 + "s");
        } catch (Throwable t) {
            this.forceDisable = true;
            logger.severe("Błąd podczas inicjalizacji pluginu tfbDrop: " + t.getMessage());
            t.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
public void onDisable() {
    if (forceDisable) {
        logger.info("Shutdown przerwany (forceDisable=true).");
        return;
    }

    logger.info("Wyłączanie pluginu tfbDrop...");

    // 1. Zatrzymaj taski NAJPIERW
    if (this.saverTask != null) {
        this.saverTask.cancel();
    }
    if (this.actionBarTask != null) {
        this.actionBarTask.cancel();
    }

    // 2. Zapisz graczy
    if (this.userManager != null) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                this.userManager.save(player.getUniqueId(), true);
            } catch (Exception e) {
                logger.warning("Błąd zapisu użytkownika " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    // 3. Zamknij bazę danych NA KOŃCU
    if (this.database != null) {
        try {
            this.database.disconnect(true);
        } catch (Exception e) {
            logger.warning("Błąd zamykania bazy: " + e.getMessage());
        }
    }

    logger.info("Plugin tfbDrop wyłączony pomyślnie.");
}

    private void registerListeners() {
    Listener[] listeners = {
        new BlockBreakListener(this),
        new InventoryClickListener(this),
        new PlayerJoinLeaveListener(this),
        new UserLevelChangeListener(this),
        new UserPointsChangeListener(this)
    };

    for (Listener listener : listeners) {
        Bukkit.getPluginManager().registerEvents(listener, this);
    }
}

    private void registerCommands() {
        new DropCommand(this);
        new ADropCommand(this);
        new LevelCommand(this);
    }
    
    private void registerPlaceholderAPI() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                new pl.tenfajnybartek.dropplugin.integrations.DropPluginExpansion(this).register();
                this.logger.info("Zarejestrowano PlaceholderAPI expansion!");
                this.logger.info("Dostępne placeholdery: %dropplugin_level%, %dropplugin_points%, %dropplugin_points_required%, %dropplugin_points_to_next%, %dropplugin_toplevel-x%");
            } catch (Exception e) {
                this.logger.warning("Nie udało się zarejestrować PlaceholderAPI expansion: " + e.getMessage());
            }
        } else {
            this.logger.info("PlaceholderAPI nie znalezione - placeholdery nie będą dostępne.");
        }
    }

    public UserManager getUserManager() {
        return this.userManager;
    }

    public DropManager getDropManager() {
        return this.dropManager;
    }

    public DropConfigManager getDropConfig() {
        return this.dropConfig;
    }

    public DropMenu getDropMenu() {
        return this.dropMenu;
    }

    public Database getDatabase() {
        return this.database;
    }

    public ConfigManager getConfigManager() {
        return this.configManager;
    }
    public void reloadConfigManager() {
        this.configManager = new ConfigManager(this);

        if (this.actionBarTask != null) {
            this.actionBarTask.cancel();
            this.actionBarTask = null;
        }
        this.actionBarTask = new ActionBarTask(this);
    }
    public ConfigManager getPluginConfig() {
        return this.configManager;
    }
}