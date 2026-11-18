package pl.tenfajnybartek.riftdrop.menu;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.*;

public record MenuLayout(String title, int size, String enabled, String disabled, boolean glow,
                         String dropName, List<String> dropLore, Tile locked, Tile filler, boolean fill,
                         Map<Control, Button> buttons) {
    public enum Control {
        BASIC_DROP("cobble"), MESSAGES("messages"), BOOSTS("turbos"),
        LEVEL("level"), ENABLE_ALL("enable-all"), DISABLE_ALL("disable-all");

        private final String key;
        Control(String key) { this.key = key; }
    }

    public record Tile(Material material, String name, List<String> lore) {}
    public record Button(int slot, Tile tile) {}

    public static MenuLayout read(YamlConfiguration yaml) {
        String base = "gui.";
        int size = read(yaml, base + "size", Integer.class);
        if (size < 9 || size > 54 || size % 9 != 0) throw invalid(base + "size");
        var buttons = new EnumMap<Control, Button>(Control.class);
        Set<Integer> reserved = new HashSet<>();
        for (Control control : Control.values()) {
            String path = base + control.key;
            if (!read(yaml, path + ".status", Boolean.class)) continue;
            int slot = read(yaml, path + ".slot", Integer.class);
            if (slot < 0 || slot >= size || !reserved.add(slot)) throw invalid(path + ".slot");
            String loreKey = control == Control.LEVEL ? "lore" : "lores";
            buttons.put(control, new Button(slot, tile(yaml, path, lore(yaml, path + "." + loreKey))));
        }
        return new MenuLayout(read(yaml, base + "name", String.class), size,
                read(yaml, base + "status.enable", String.class), read(yaml, base + "status.disable", String.class),
                read(yaml, base + "item.enchanted", Boolean.class), read(yaml, base + "item.name", String.class),
                lore(yaml, base + "item.lores"), tile(yaml, base + "locked", lore(yaml, base + "locked.lores")),
                tile(yaml, base + "filler", List.of()), read(yaml, base + "filler.status", Boolean.class),
                Collections.unmodifiableMap(buttons));
    }

    private static Tile tile(YamlConfiguration yaml, String path, List<String> lore) {
        Material material = Material.matchMaterial(read(yaml, path + ".item", String.class));
        if (material == null || !material.isItem() || material.isAir()) throw invalid(path + ".item");
        return new Tile(material, read(yaml, path + ".name", String.class), lore);
    }

    private static List<String> lore(YamlConfiguration yaml, String path) {
        var values = read(yaml, path, List.class);
        List<String> lines = new ArrayList<>();
        for (Object value : values) {
            if (!(value instanceof String line)) throw invalid(path);
            lines.add(line);
        }
        return List.copyOf(lines);
    }

    private static <T> T read(YamlConfiguration yaml, String path, Class<T> type) {
        Object value = yaml.get(path);
        if (!type.isInstance(value)) throw invalid(path);
        return type.cast(value);
    }

    private static IllegalArgumentException invalid(String path) {
        return new IllegalArgumentException("Niepoprawna wartość w gui.yml: " + path);
    }
}
