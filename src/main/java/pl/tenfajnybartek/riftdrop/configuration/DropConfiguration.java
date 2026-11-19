package pl.tenfajnybartek.riftdrop.configuration;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import pl.tenfajnybartek.riftdrop.RiftDropPlugin;
import pl.tenfajnybartek.riftdrop.mining.DropRule;
import pl.tenfajnybartek.riftdrop.mining.IntRange;
import pl.tenfajnybartek.riftdrop.mining.ItemSpecParser;

import java.io.File;
import java.io.IOException;
import java.util.*;

public record DropConfiguration(int stoneExperience, int obsidianExperience, List<DropRule> rules,
                                Map<Material, List<DropRule>> bySource, List<MiningRegion> regions) {
    public static final Set<Material> DEFAULT_SOURCES = Set.of(
            Material.STONE, Material.GRANITE, Material.DIORITE, Material.ANDESITE, Material.DEEPSLATE);

    public static DropConfiguration load(RiftDropPlugin plugin) throws IOException, InvalidConfigurationException {
        if (!new File(plugin.getDataFolder(), "drops.yml").isFile()) plugin.saveResource("drops.yml", false);
        var yaml = PluginConfiguration.load(plugin, "drops.yml");
        int stoneExp = nonNegative(yaml, "exps.stone", 0);
        int obsidianExp = nonNegative(yaml, "exps.obsidian", 0);
        var drops = yaml.getConfigurationSection("drops");
        if (drops == null) throw new IllegalArgumentException("drops.yml: brakuje sekcji drops.");
        List<DropRule> parsed = new ArrayList<>();
        Map<Material, List<DropRule>> bySource = new EnumMap<>(Material.class);
        Map<String, Set<Material>> groups = new HashMap<>();
        if (yaml.contains("source-groups")) {
            var groupConfig = yaml.getConfigurationSection("source-groups");
            if (groupConfig == null) throw new IllegalArgumentException("source-groups musi być sekcją.");
            for (String name : groupConfig.getKeys(false)) {
                groups.put(name, sources(groupConfig.get(name), Map.of()));
            }
        }
        for (String id : drops.getKeys(false)) {
            try {
                var entry = drops.getConfigurationSection(id);
                if (entry == null) throw new IllegalArgumentException("Wpis musi być sekcją.");
                IntRange amount = IntRange.parse(entry.getString("amount", "1-2"));
                IntRange height = IntRange.parse(entry.getString("height", "0-90"));
                IntRange points = IntRange.parse(entry.getString("points", "3-7"));
                if (amount.getMin() < 1 || amount.getMax() > 4096 || points.getMin() < 0) {
                    throw new IllegalArgumentException("amount musi być w zakresie 1–4096, a points nieujemne.");
                }
                if (entry.contains("chance") && !(entry.get("chance") instanceof Number)) {
                    throw new IllegalArgumentException("chance musi być liczbą.");
                }
                if (entry.contains("fortune") && !entry.isBoolean("fortune")) {
                    throw new IllegalArgumentException("fortune musi mieć wartość true lub false.");
                }
                var rule = new DropRule(id, entry.getString("name", id), entry.getBoolean("fortune", true),
                        ItemSpecParser.parseItemStack(entry.getString("item")), entry.getDouble("chance", 5.0),
                        height, amount, points, nonNegative(entry, "exp", 3), nonNegative(entry, "neededlvl", 0));
                Set<Material> materials = entry.contains("sources") ? sources(entry.get("sources"), groups) : DEFAULT_SOURCES;
                for (Material material : materials) bySource.computeIfAbsent(material, key -> new ArrayList<>()).add(rule);
                parsed.add(rule);
            } catch (IllegalArgumentException failure) {
                throw new IllegalArgumentException("drops.yml: drops." + id + ": " + failure.getMessage(), failure);
            }
        }
        bySource.replaceAll((material, rules) -> List.copyOf(rules));
        return new DropConfiguration(stoneExp, obsidianExp, List.copyOf(parsed), Map.copyOf(bySource), MiningRegion.read(yaml));
    }

    static Set<Material> sources(Object value, Map<String, Set<Material>> groups) {
        List<?> entries = value instanceof List<?> list ? list : List.of(Objects.toString(value, ""));
        Set<Material> result = EnumSet.noneOf(Material.class);
        for (Object raw : entries) {
            if (!(raw instanceof String text) || text.isBlank()) throw new IllegalArgumentException("Niepoprawne sources.");
            if (text.startsWith("@")) {
                Set<Material> group = groups.get(text.substring(1));
                if (group == null) throw new IllegalArgumentException("Nieznana grupa źródeł " + text);
                result.addAll(group);
            } else {
                Material material = Material.matchMaterial(text);
                if (material == null || !material.isBlock() || material.isAir()) {
                    throw new IllegalArgumentException("Nieznany blok źródłowy: " + text);
                }
                result.add(material);
            }
        }
        if (result.isEmpty()) throw new IllegalArgumentException("Lista źródeł jest pusta.");
        return Set.copyOf(result);
    }

    private static int nonNegative(ConfigurationSection section, String key, int fallback) {
        if (section.contains(key) && !section.isInt(key)) throw new IllegalArgumentException(key + " musi być liczbą całkowitą.");
        int value = section.getInt(key, fallback);
        if (value < 0) throw new IllegalArgumentException(key + " musi być nieujemne.");
        return value;
    }
}
