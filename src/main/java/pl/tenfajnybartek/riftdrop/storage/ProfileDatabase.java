package pl.tenfajnybartek.riftdrop.storage;

import com.mysql.cj.jdbc.MysqlDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import pl.tenfajnybartek.riftdrop.profile.MiningProfile.Profile;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ProfileDatabase {
    public static final int TOP_LIMIT = 100;
    public record Rank(String name, int level) {}

    public record Settings(String type, String host, int port, String schema, String username, String password,
                           String sslMode, int poolSize, long connectionTimeoutMs, long idleTimeoutMs, long leakDetectionMs) {}

    private final Settings config;
    private final ProfileRecovery recovery;
    private final File file;
    private final Logger logger;
    private final boolean sqlite;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "riftDrop-database");
        thread.setDaemon(true);
        return thread;
    });
    // The main thread publishes snapshots. Only the worker commits them, in submission order.
    private final Map<UUID, Profile> pending = new ConcurrentHashMap<>();
    private volatile boolean saveQueued;
    private HikariDataSource pool;
    private long retryAfter;
    private volatile List<Rank> ranking = List.of();
    private volatile boolean refreshQueued;

    public ProfileDatabase(Settings config, File folder, Logger logger) {
        this.config = config;
        this.file = new File(folder, "database.db");
        this.logger = logger;
        this.sqlite = config.type().equals("sqlite");
        this.recovery = new ProfileRecovery(folder.toPath(), config, logger);
    }

    private HikariDataSource connect() throws SQLException {
        if (pool != null) return pool;
        if (retryAfter != 0 && System.nanoTime() - retryAfter < 0) throw new SQLException("Ponawianie połączenia z bazą za kilka sekund.");
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("riftDrop-" + config.type());
        hikari.setMaximumPoolSize(sqlite ? 1 : config.poolSize());
        hikari.setMinimumIdle(0);
        hikari.setConnectionTimeout(config.connectionTimeoutMs());
        hikari.setIdleTimeout(config.idleTimeoutMs());
        hikari.setLeakDetectionThreshold(config.leakDetectionMs());
        hikari.setInitializationFailTimeout(-1);
        if (sqlite) {
            SQLiteConfig options = new SQLiteConfig();
            options.setJournalMode(SQLiteConfig.JournalMode.WAL);
            options.setBusyTimeout((int) config.connectionTimeoutMs());
            SQLiteDataSource source = new SQLiteDataSource(options);
            source.setUrl("jdbc:sqlite:" + file.getAbsolutePath());
            hikari.setDataSource(source);
        } else {
            MysqlDataSource source = new MysqlDataSource();
            source.setServerName(config.host());
            source.setPort(config.port());
            source.setDatabaseName(config.schema());
            source.setUser(config.username());
            source.setPassword(config.password());
            source.setSslMode(config.sslMode());
            source.setConnectTimeout((int) config.connectionTimeoutMs());
            source.setSocketTimeout((int) config.connectionTimeoutMs());
            hikari.setDataSource(source);
        }

        HikariDataSource candidate = new HikariDataSource(hikari);
        String keyType = sqlite ? "TEXT" : "VARCHAR(36)";
        try (Connection connection = candidate.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS drop_users (
                        identifier %s NOT NULL PRIMARY KEY,
                        cobble BOOLEAN NOT NULL, messages BOOLEAN NOT NULL,
                        turboDrop BIGINT NOT NULL, turboExp BIGINT NOT NULL,
                        lvl INT NOT NULL, points INT NOT NULL,
                        minedDrops TEXT NOT NULL, disabledDrops TEXT NOT NULL, last_name VARCHAR(16), data_format SMALLINT NOT NULL DEFAULT 1
                    )
                    """.formatted(keyType));
            ensureColumn(connection, statement, "last_name", "VARCHAR(16)");
            ensureColumn(connection, statement, "data_format", "SMALLINT NOT NULL DEFAULT 1");
            ensureRankingIndex(connection, statement);
            // Verify existing tables have the expected schema before publishing readiness.
            try (ResultSet ignored = statement.executeQuery("""
                    SELECT identifier, cobble, messages, turboDrop, turboExp, lvl, points, minedDrops, disabledDrops, last_name, data_format
                    FROM drop_users WHERE 1 = 0
                    """)) {
                pool = candidate;
            }
            logger.info("Baza danych gotowa (" + config.type() + ").");
            return pool;
        } catch (SQLException | RuntimeException failure) {
            pool = null;
            candidate.close();
            retryAfter = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            throw failure;
        }
    }

    private static void ensureColumn(Connection connection, Statement statement, String name, String definition) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, "drop_users", name)) {
            if (columns.next()) return;
        }
        statement.executeUpdate("ALTER TABLE drop_users ADD COLUMN " + name + " " + definition);
    }

    private static void ensureRankingIndex(Connection connection, Statement statement) throws SQLException {
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(connection.getCatalog(), null, "drop_users", false, false)) {
            while (indexes.next()) {
                if ("drop_users_ranking".equalsIgnoreCase(indexes.getString("INDEX_NAME"))) return;
            }
        }
        statement.executeUpdate("CREATE INDEX drop_users_ranking ON drop_users (lvl DESC, points DESC, identifier)");
    }

    public Future<Profile> load(UUID id) {
        return worker.submit(() -> {
            recovery.restore(pending);
            Profile unsaved = pending.get(id);
            if (unsaved != null) return unsaved;
            try (Connection connection = connect().getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT * FROM drop_users WHERE identifier = ?")) {
                statement.setString(1, id.toString());
                try (ResultSet row = statement.executeQuery()) {
                    if (!row.next()) return Profile.initial(id);
                    boolean legacy = row.getInt("data_format") == 1;
                    String counters = row.getString("minedDrops");
                    String disabled = row.getString("disabledDrops");
                    return new Profile(id, row.getString("last_name"), row.getBoolean("cobble"), row.getBoolean("messages"),
                            row.getLong("turboDrop"), row.getLong("turboExp"), row.getInt("lvl"), row.getLong("points"),
                            legacy ? ProfileCodec.legacyMap(counters) : ProfileCodec.deserializeMap(counters),
                            legacy ? ProfileCodec.legacyList(disabled) : ProfileCodec.deserializeList(disabled));
                }
            }
        });
    }

    public void save(Collection<Profile> profiles) {
        profiles.forEach(profile -> pending.put(profile.id(), profile));
        if (saveQueued || (pending.isEmpty() && recovery.loaded())) return;
        saveQueued = true;
        worker.execute(() -> {
            // Clear before taking a snapshot so further changes can schedule a following commit.
            saveQueued = false;
            try {
                flush();
            } catch (IOException | SQLException | RuntimeException failure) {
                logger.log(Level.WARNING, "Zapis profili nie powiódł się. Oczekujące dane zostaną zapisane ponownie.", failure);
            }
        });
    }

    private void flush() throws SQLException, IOException {
        recovery.restore(pending);
        Map<UUID, Profile> submitted = Map.copyOf(pending);
        if (submitted.isEmpty()) return;
        String values = sqlite
                ? "ON CONFLICT(identifier) DO UPDATE SET cobble=excluded.cobble, messages=excluded.messages, turboDrop=excluded.turboDrop, turboExp=excluded.turboExp, lvl=excluded.lvl, points=excluded.points, minedDrops=excluded.minedDrops, disabledDrops=excluded.disabledDrops, last_name=excluded.last_name, data_format=excluded.data_format"
                : "ON DUPLICATE KEY UPDATE cobble=VALUES(cobble), messages=VALUES(messages), turboDrop=VALUES(turboDrop), turboExp=VALUES(turboExp), lvl=VALUES(lvl), points=VALUES(points), minedDrops=VALUES(minedDrops), disabledDrops=VALUES(disabledDrops), last_name=VALUES(last_name), data_format=VALUES(data_format)";
        try (Connection connection = connect().getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO drop_users (identifier, cobble, messages, turboDrop, turboExp, lvl, points, minedDrops, disabledDrops, last_name, data_format)
                     VALUES (?,?,?,?,?,?,?,?,?,?,2)
                     """ + values)) {
            connection.setAutoCommit(false);
            try {
                for (Profile profile : submitted.values()) {
                    statement.setString(1, profile.id().toString());
                    statement.setBoolean(2, profile.cobble());
                    statement.setBoolean(3, profile.messages());
                    statement.setLong(4, profile.turboDrop());
                    statement.setLong(5, profile.turboExp());
                    statement.setInt(6, profile.level());
                    statement.setLong(7, profile.points());
                    statement.setString(8, ProfileCodec.serializeMap(profile.minedDrops()));
                    statement.setString(9, ProfileCodec.serializeList(profile.disabledDrops()));
                    statement.setString(10, profile.name());
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
                submitted.forEach(pending::remove);
            } catch (SQLException | RuntimeException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
        }
        recovery.updateIfPresent(pending);
    }

    public List<Rank> ranking() {
        return ranking;
    }

    public void refreshRanking() {
        if (refreshQueued) return;
        refreshQueued = true;
        worker.execute(() -> {
            List<Rank> rows = new ArrayList<>();
            try (Connection connection = connect().getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT identifier, lvl, last_name FROM drop_users
                         ORDER BY lvl DESC, points DESC, identifier ASC LIMIT ?
                         """)) {
                statement.setInt(1, TOP_LIMIT);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        try {
                            UUID id = UUID.fromString(result.getString(1));
                            String name = result.getString(3);
                            rows.add(new Rank(name == null ? id.toString() : name, result.getInt(2)));
                        } catch (IllegalArgumentException invalid) {
                            logger.warning("Pominięto nieprawidłowy UUID w rankingu riftDrop.");
                        }
                    }
                }
                ranking = List.copyOf(rows);
            } catch (SQLException | RuntimeException failure) {
                logger.log(Level.WARNING, "Nie udało się odświeżyć rankingu riftDrop.", failure);
            } finally {
                refreshQueued = false;
            }
        });
    }

    public void close() {
        Future<?> shutdown = worker.submit(() -> {
            try {
                retryAfter = 0;
                flush();
            } catch (IOException | SQLException | RuntimeException failure) {
                logger.log(Level.SEVERE, "Nie udało się zapisać " + pending.size() + " profili przed zamknięciem.", failure);
            } finally {
                recovery.save(pending);
                if (pool != null) pool.close();
            }
        });
        worker.shutdown();
        try {
            shutdown.get(Math.min(config.connectionTimeoutMs() + 5000, 20_000L), TimeUnit.MILLISECONDS);
        } catch (InterruptedException failure) {
            recovery.save(pending);
            Thread.currentThread().interrupt();
            logger.log(Level.WARNING, "Przerwano oczekiwanie na zapis profili.", failure);
        } catch (ExecutionException | TimeoutException failure) {
            recovery.save(pending);
            logger.log(Level.SEVERE, "Zamykanie bazy riftDrop nie zakończyło się w wyznaczonym czasie.", failure);
        }
    }
}
