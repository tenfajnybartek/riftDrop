package pl.tenfajnybartek.riftdrop.configuration;

import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

public record MiningRegion(String id, String world, int minX, int minY, int minZ,
                           int maxX, int maxY, int maxZ, int minimumLevel) {
    public boolean contains(Block block) {
        return world.equals(block.getWorld().getName())
                && block.getX() >= minX && block.getX() <= maxX
                && block.getY() >= minY && block.getY() <= maxY
                && block.getZ() >= minZ && block.getZ() <= maxZ;
    }

    static List<MiningRegion> read(ConfigurationSection root) {
        if (!root.contains("regions")) return List.of();
        var section = root.getConfigurationSection("regions");
        if (section == null) throw new IllegalArgumentException("drops.yml: regions musi być sekcją.");
        var regions = new ArrayList<MiningRegion>();
        for (String id : section.getKeys(false)) {
            var region = section.getConfigurationSection(id);
            String path = "drops.yml: regions." + id;
            if (region == null || !region.isString("world") || region.getString("world").isBlank()
                    || !region.isInt("minimum-level") || region.getInt("minimum-level") < 0) {
                throw new IllegalArgumentException(path + " wymaga world i nieujemnego minimum-level.");
            }
            int[] from = point(region.getList("from"), path + ".from");
            int[] to = point(region.getList("to"), path + ".to");
            regions.add(new MiningRegion(id, region.getString("world"),
                    Math.min(from[0], to[0]), Math.min(from[1], to[1]), Math.min(from[2], to[2]),
                    Math.max(from[0], to[0]), Math.max(from[1], to[1]), Math.max(from[2], to[2]),
                    region.getInt("minimum-level")));
        }
        return List.copyOf(regions);
    }

    private static int[] point(List<?> values, String path) {
        if (values == null || values.size() != 3 || !values.stream().allMatch(Integer.class::isInstance)) {
            throw new IllegalArgumentException(path + " wymaga [x, y, z] jako liczb całkowitych.");
        }
        return values.stream().mapToInt(Integer.class::cast).toArray();
    }
}
