package pl.tenfajnybartek.dropplugin.utils;

import java.io.*;
import java.util.*;

public final class MapUtils {
    private static final String PREFIX = "v2:";
    private MapUtils() {}

    public static String serializeList(Set<String> values) {
        return encode(out -> {
            for (String key : values) out.writeUTF(key);
        });
    }

    public static Set<String> deserializeList(String data) throws IOException {
        if (data == null || data.isEmpty()) return Set.of();
        Set<String> values = new HashSet<>();
        try (DataInputStream input = decode(data)) {
            while (input.available() > 0) values.add(input.readUTF());
        }
        return values;
    }

    public static String serializeMap(Map<String, Integer> values) {
        return encode(out -> {
            for (var entry : values.entrySet()) {
                out.writeUTF(entry.getKey());
                out.writeInt(entry.getValue());
            }
        });
    }

    public static Map<String, Integer> deserializeMap(String data) throws IOException {
        Map<String, Integer> values = new HashMap<>();
        if (data == null || data.isEmpty()) return values;
        try (DataInputStream input = decode(data)) {
            while (input.available() > 0) values.put(input.readUTF(), input.readInt());
        }
        return values;
    }

    public static Set<String> legacyList(String data) {
        if (data == null || data.isEmpty()) return Set.of();
        Set<String> values = new HashSet<>();
        for (String entry : data.split("@")) values.add("legacy:" + entry);
        return values;
    }

    public static Map<String, Integer> legacyMap(String data) {
        Map<String, Integer> values = new HashMap<>();
        if (data == null || data.isEmpty()) return values;
        for (String entry : data.split("@")) {
            int separator = entry.lastIndexOf('=');
            if (separator <= 0) continue;
            try {
                values.put("legacy:" + entry.substring(0, separator), Integer.parseInt(entry.substring(separator + 1)));
            } catch (NumberFormatException invalid) {
                // A malformed legacy counter cannot be reconstructed.
            }
        }
        return values;
    }

    private static DataInputStream decode(String data) throws IOException {
        if (!data.startsWith(PREFIX)) throw new IOException("Nieznany format profilu DropPlugin.");
        try {
            return new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(data.substring(PREFIX.length()))));
        } catch (IllegalArgumentException failure) {
            throw new IOException("Niepoprawny zapis profilu DropPlugin.", failure);
        }
    }

    private static String encode(Encoder encoder) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            encoder.write(output);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return PREFIX + Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    @FunctionalInterface
    private interface Encoder {
        void write(DataOutputStream out) throws IOException;
    }
}
