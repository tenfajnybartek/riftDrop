package pl.tenfajnybartek.dropplugin.managers;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.tenfajnybartek.dropplugin.base.DropPlugin;

import java.io.File;
import java.io.IOException;

public final class DropConfigManager {
    private final YamlConfiguration dropConfig;

    public DropConfigManager(DropPlugin plugin) throws IOException, InvalidConfigurationException {
        if (!new File(plugin.getDataFolder(), "drops.yml").isFile()) plugin.saveResource("drops.yml", false);
        dropConfig = ConfigManager.load(plugin, "drops.yml");
    }

    public YamlConfiguration getDropConfig() {
        return dropConfig;
    }
}
