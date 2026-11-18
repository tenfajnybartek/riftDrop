package pl.tenfajnybartek.riftdrop.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import pl.tenfajnybartek.riftdrop.profile.MiningProfile.Profile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class ProfileDatabaseTest {
    private static final ProfileDatabase.Settings SQLITE =
            new ProfileDatabase.Settings("sqlite", "", 0, "", "", "", "", 1, 500, 0, 0);

    @TempDir
    Path folder;

    private ProfileDatabase open() {
        return new ProfileDatabase(SQLITE, folder.toFile(), Logger.getLogger("DropPluginTest"));
    }

    private Connection jdbc() throws Exception {
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + folder.resolve("database.db"));
        return source.getConnection();
    }

    @Test
    void existingProfileSurvivesOrderedWritesReconnectAndRestart() throws Exception {
        UUID id = UUID.randomUUID();
        try (var connection = jdbc(); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE drop_users (
                        identifier TEXT PRIMARY KEY, cobble INTEGER, messages INTEGER,
                        turboDrop INTEGER, turboExp INTEGER, lvl INTEGER, points INTEGER,
                        minedDrops TEXT, disabledDrops TEXT
                    )
                    """);
            try (var insert = connection.prepareStatement("INSERT INTO drop_users VALUES (?,0,1,8,9,17,42,'Diament=33','Diament')")) {
                insert.setString(1, id.toString());
                insert.executeUpdate();
            }
        }

        ProfileDatabase database = open();
        Profile latest = new Profile(id, "TestMiner", false, true, 8, 9, 18, 6,
                Map.of("id:diamond", 34, "legacy:Name=Part", 11), Set.of("id:diamond"));
        try {
            var legacy = database.load(id).get(5, TimeUnit.SECONDS);
            assertEquals(17, legacy.level());
            assertEquals(42, legacy.points());
            assertEquals(Map.of("legacy:Diament", 33), legacy.minedDrops());
            assertEquals(Set.of("legacy:Diament"), legacy.disabledDrops());
            database.save(List.of(legacy));
            database.save(List.of(latest));
            assertEquals(latest, database.load(id).get(5, TimeUnit.SECONDS));
            assertEquals(Profile.initial(new UUID(0, 0)), database.load(new UUID(0, 0)).get(5, TimeUnit.SECONDS));
        } finally {
            database.close();
        }

        ProfileDatabase restarted = open();
        try {
            assertEquals(latest, restarted.load(id).get(5, TimeUnit.SECONDS));
            restarted.refreshRanking();
            // Wait for the single database worker to process the ranking refresh.
            restarted.load(id).get(5, TimeUnit.SECONDS);
            assertEquals(List.of(new ProfileDatabase.Rank("TestMiner", 18)), restarted.ranking());
        } finally {
            restarted.close();
        }
    }

    @Test
    void failedShutdownWriteIsRecoveredAtNextStart() throws Exception {
        ProfileDatabase database = open();
        UUID id = UUID.randomUUID();
        Profile profile = new Profile(id, "TestMiner", false, true, 8, 9, 18, 6,
                Map.of("id:diamond", 34), Set.of("id:diamond"));
        database.load(id).get(5, TimeUnit.SECONDS);
        try (var connection = jdbc(); var statement = connection.createStatement()) {
            statement.execute("BEGIN IMMEDIATE");
            try {
                database.save(List.of(profile));
                database.close();
                assertTrue(Files.isRegularFile(folder.resolve("pending-sqlite.properties")));
            } finally {
                statement.execute("ROLLBACK");
            }
        }

        ProfileDatabase restarted = open();
        try {
            assertEquals(profile, restarted.load(id).get(5, TimeUnit.SECONDS));
        } finally {
            restarted.close();
        }
        assertFalse(Files.exists(folder.resolve("pending-sqlite.properties")));

        ProfileDatabase reader = open();
        try {
            assertEquals(profile, reader.load(id).get(5, TimeUnit.SECONDS));
        } finally {
            reader.close();
        }
    }
}
