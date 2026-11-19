package pl.tenfajnybartek.riftdrop.command;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AdminHistory {
    private final Path file;
    private final Logger logger;

    public AdminHistory(Path folder, Logger logger) {
        this.file = folder.resolve("admin-history.tsv");
        this.logger = logger;
    }

    public synchronized void record(String actor, String target, String operation, String detail) {
        String line = String.join("\t", Instant.now().toString(), field(actor), field(target), field(operation), field(detail));
        try {
            Files.writeString(file, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException failure) {
            logger.log(Level.WARNING, "Nie zapisano historii administracyjnej. Operacja: " + line, failure);
        }
    }

    public synchronized List<String> recent(String target, String operationPrefix, int limit) throws IOException {
        if (!Files.exists(file)) return List.of();
        var rows = new ArrayDeque<String>();
        try (var reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split("\t", 5);
                if (fields.length != 5 || (target != null && !target.equals(fields[2]))
                        || !fields[3].startsWith(operationPrefix)) continue;
                if (rows.size() == limit) rows.removeFirst();
                rows.addLast(fields[0] + " | " + fields[1] + " | " + fields[2] + " | " + fields[3] + " | " + fields[4]);
            }
        }
        return new ArrayList<>(rows.reversed());
    }

    private static String field(String value) {
        return value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ').replace('§', ' ');
    }
}
