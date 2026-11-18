package pl.tenfajnybartek.riftdrop.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LegacyDataMigrationTest {
    @TempDir
    Path plugins;

    @Test
    void copiesConfigurationAndRecoveryFilesOnce() throws Exception {
        Path legacy = Files.createDirectory(plugins.resolve("DropPlugin"));
        Path target = Files.createDirectory(plugins.resolve("riftDrop"));
        Files.writeString(legacy.resolve("config.yml"), "custom: true");
        Files.writeString(legacy.resolve("database.db"), "previous database");
        Files.writeString(legacy.resolve("database.db-wal"), "pending SQLite transaction");
        Files.writeString(legacy.resolve("pending-mysql-0123456789abcdef.properties"), "pending profile");
        Files.writeString(legacy.resolve("unrelated.jar"), "not data");

        assertTrue(LegacyDataMigration.copyIfNeeded(target));
        assertEquals("custom: true", Files.readString(target.resolve("config.yml")));
        assertEquals("previous database", Files.readString(target.resolve("database.db")));
        assertEquals("pending SQLite transaction", Files.readString(target.resolve("database.db-wal")));
        assertTrue(Files.exists(target.resolve("pending-mysql-0123456789abcdef.properties")));
        assertFalse(Files.exists(target.resolve("unrelated.jar")));
        assertTrue(Files.exists(legacy.resolve("database.db")));

        Files.writeString(target.resolve("database.db"), "updated database");
        assertFalse(LegacyDataMigration.copyIfNeeded(target));
        assertEquals("updated database", Files.readString(target.resolve("database.db")));
    }

    @Test
    void leavesFreshInstallAndExistingTargetAlone() throws Exception {
        Path target = plugins.resolve("riftDrop");
        assertFalse(LegacyDataMigration.copyIfNeeded(target));
        assertFalse(Files.exists(target));

        Files.createDirectory(target);
        Files.writeString(target.resolve("config.yml"), "new data");
        Path legacy = Files.createDirectory(plugins.resolve("DropPlugin"));
        Files.writeString(legacy.resolve("config.yml"), "old data");
        assertFalse(LegacyDataMigration.copyIfNeeded(target));
        assertEquals("new data", Files.readString(target.resolve("config.yml")));
    }
}
