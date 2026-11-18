package pl.tenfajnybartek.riftdrop.configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.stream.Stream;

/** Copies the previous installation before riftDrop initializes its default files. */
public final class LegacyDataMigration {
    private static final Set<String> DATA_FILES = Set.of(
            "config.yml", "gui.yml", "drops.yml", "database.db",
            "database.db-wal", "database.db-shm", "database.db-journal");

    private LegacyDataMigration() {}

    public static boolean copyIfNeeded(Path destination) throws IOException {
        Path previous = destination.resolveSibling("DropPlugin");
        if (!Files.isDirectory(previous)) return false;
        if (Files.exists(destination)) {
            if (!Files.isDirectory(destination, LinkOption.NOFOLLOW_LINKS)) return false;
            try (Stream<Path> files = Files.list(destination)) {
                if (files.findAny().isPresent()) return false;
            }
        }

        try (var source = Files.list(previous)) {
            var files = source.filter(Files::isRegularFile)
                    .filter(file -> DATA_FILES.contains(file.getFileName().toString())
                            || file.getFileName().toString().matches("pending-(?:sqlite|mysql-[0-9a-f]+)\\.properties"))
                    .toList();
            if (files.isEmpty()) return false;

            Path staging = Files.createTempDirectory(destination.getParent(), ".riftdrop-migration-");
            try {
                for (Path file : files) {
                    Files.copy(file, staging.resolve(file.getFileName()));
                }
                // The checked destination may have been created empty by the server.
                // This fails safely if a file appeared after the check.
                Files.deleteIfExists(destination);
                Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
                return true;
            } finally {
                if (Files.isDirectory(staging)) {
                    for (Path file : files) Files.deleteIfExists(staging.resolve(file.getFileName()));
                    Files.deleteIfExists(staging);
                }
            }
        }
    }
}
