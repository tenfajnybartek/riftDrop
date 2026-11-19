package pl.tenfajnybartek.riftdrop.storage;

import org.bukkit.configuration.file.YamlConfiguration;
import pl.tenfajnybartek.riftdrop.profile.MiningProfile.Profile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeMap;

public final class ProfileExport {
    private ProfileExport() {}

    public static String write(Path dataFolder, Profile profile) throws IOException {
        var yaml = new YamlConfiguration();
        yaml.set("format", 1);
        yaml.set("uuid", profile.id().toString());
        yaml.set("last-name", profile.name());
        yaml.set("level", profile.level());
        yaml.set("points", profile.points());
        yaml.set("basic-drops", profile.cobble());
        yaml.set("drop-messages", profile.messages());
        yaml.set("turbo-drop-until", profile.turboDrop());
        yaml.set("turbo-exp-until", profile.turboExp());
        yaml.set("mined-drops", new TreeMap<>(profile.minedDrops()));
        yaml.set("disabled-drops", profile.disabledDrops().stream().sorted().toList());
        Path folder = Files.createDirectories(dataFolder.resolve("exports"));
        Path file = Files.createTempFile(folder, profile.id() + "-", ".yml");
        try {
            yaml.save(file.toFile());
        } catch (IOException failure) {
            Files.deleteIfExists(file);
            throw failure;
        }
        return "exports/" + file.getFileName();
    }
}
