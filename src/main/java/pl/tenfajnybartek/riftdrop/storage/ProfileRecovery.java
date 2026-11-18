package pl.tenfajnybartek.riftdrop.storage;

import pl.tenfajnybartek.riftdrop.profile.MiningProfile.Profile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

final class ProfileRecovery {
    private static final Pattern FIELD_SEPARATOR = Pattern.compile(Pattern.quote("|"));

    private final Path recovery;
    private final Logger logger;
    private volatile boolean loaded;

    ProfileRecovery(Path folder, ProfileDatabase.Settings config, Logger logger) {
        this.logger = logger;
        this.recovery = folder.resolve(recoveryName(config));
    }

    boolean loaded() { return loaded; }

    void updateIfPresent(Map<UUID, Profile> pending) throws IOException {
        if (Files.exists(recovery)) write(pending);
    }

    private static String recoveryName(ProfileDatabase.Settings config) {
        if (config.type().equals("sqlite")) return "pending-sqlite.properties";
        String destination = config.host() + ":" + config.port() + "/" + config.schema();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(destination.getBytes(StandardCharsets.UTF_8));
            return "pending-mysql-" + HexFormat.of().formatHex(digest, 0, 8) + ".properties";
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    void restore(Map<UUID, Profile> pending) throws IOException {
        if (loaded) return;
        if (Files.exists(recovery)) {
            Properties properties = new Properties();
            try (var input = Files.newInputStream(recovery)) {
                properties.load(input);
            }
            Map<UUID, Profile> restored = new HashMap<>();
            for (String key : properties.stringPropertyNames()) {
                UUID id = UUID.fromString(key);
                String[] fields = FIELD_SEPARATOR.split(properties.getProperty(key), -1);
                if (fields.length != 9) throw new IOException("Uszkodzony profil w " + recovery);
                Profile profile = new Profile(id, fields[0].isEmpty() ? null : fields[0],
                        Boolean.parseBoolean(fields[1]), Boolean.parseBoolean(fields[2]),
                        Long.parseLong(fields[3]), Long.parseLong(fields[4]), Integer.parseInt(fields[5]),
                        Long.parseLong(fields[6]), ProfileCodec.deserializeMap(fields[7]), ProfileCodec.deserializeList(fields[8]));
                restored.put(id, profile);
            }
            restored.forEach(pending::putIfAbsent);
            logger.info("Odzyskano " + restored.size() + " oczekujących profili riftDrop.");
        }
        loaded = true;
    }

    private synchronized void write(Map<UUID, Profile> pending) throws IOException {
        Map<UUID, Profile> remaining = Map.copyOf(pending);
        if (remaining.isEmpty()) {
            if (loaded) Files.deleteIfExists(recovery);
            return;
        }
        // Never replace an unreadable recovery file with a partial snapshot.
        if (!loaded && Files.exists(recovery)) return;
        Properties properties = new Properties();
        remaining.forEach((id, profile) -> properties.setProperty(id.toString(), String.join("|",
                Objects.toString(profile.name(), ""), Boolean.toString(profile.cobble()),
                Boolean.toString(profile.messages()), Long.toString(profile.turboDrop()),
                Long.toString(profile.turboExp()), Integer.toString(profile.level()), Long.toString(profile.points()),
                ProfileCodec.serializeMap(profile.minedDrops()), ProfileCodec.serializeList(profile.disabledDrops()))));
        Path temporary = Files.createTempFile(recovery.getParent(), recovery.getFileName().toString(), ".tmp");
        try {
            try (var output = Files.newOutputStream(temporary)) {
                properties.store(output, "riftDrop pending profiles");
            }
            try {
                Files.move(temporary, recovery, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, recovery, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    void save(Map<UUID, Profile> pending) {
        try {
            write(pending);
        } catch (IOException | RuntimeException failure) {
            logger.log(Level.SEVERE, "Nie udało się zabezpieczyć niezapisanych profili w " + recovery, failure);
        }
    }
}
