package pl.tenfajnybartek.dropplugin.managers;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import pl.tenfajnybartek.dropplugin.base.DropPlugin;
import pl.tenfajnybartek.dropplugin.objects.Count;
import pl.tenfajnybartek.dropplugin.objects.Drop;
import pl.tenfajnybartek.dropplugin.objects.User;
import pl.tenfajnybartek.dropplugin.utils.ChatUtils;
import pl.tenfajnybartek.dropplugin.utils.ItemUtils;
import pl.tenfajnybartek.dropplugin.utils.ParserUtils;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class DropManager {
    private static final Set<Material> STONE_LIKE = EnumSet.of(
            Material.STONE, Material.GRANITE, Material.DIORITE, Material.ANDESITE, Material.DEEPSLATE);

    private final DropPlugin plugin;
    private final ConfigManager config;
    private final List<Drop> dropList;
    private final int stoneExp;
    private final int obsidianExp;

    public DropManager(DropPlugin plugin, ConfigManager config, DropConfigManager dropConfig) {
        this.plugin = plugin;
        this.config = config;
        var yaml = dropConfig.getDropConfig();
        this.stoneExp = nonNegative(yaml, "exps.stone", 0);
        this.obsidianExp = nonNegative(yaml, "exps.obsidian", 0);
        var drops = yaml.getConfigurationSection("drops");
        if (drops == null) throw new IllegalArgumentException("drops.yml: brakuje sekcji drops.");
        List<Drop> parsed = new ArrayList<>();
        for (String id : drops.getKeys(false)) {
            try {
                var entry = drops.getConfigurationSection(id);
                if (entry == null) throw new IllegalArgumentException("Wpis musi być sekcją.");
                Count amount = Count.parse(entry.getString("amount", "1-2"));
                Count height = Count.parse(entry.getString("height", "0-90"));
                Count points = Count.parse(entry.getString("points", "3-7"));
                if (amount.getMin() < 1 || amount.getMax() > 4096 || points.getMin() < 0) {
                    throw new IllegalArgumentException("amount musi być w zakresie 1–4096, a points nieujemne.");
                }
                if (entry.contains("chance") && !(entry.get("chance") instanceof Number)) {
                    throw new IllegalArgumentException("chance musi być liczbą.");
                }
                if (entry.contains("fortune") && !entry.isBoolean("fortune")) {
                    throw new IllegalArgumentException("fortune musi mieć wartość true lub false.");
                }
                parsed.add(new Drop(id, entry.getString("name", id), entry.getBoolean("fortune", true),
                        ParserUtils.parseItemStack(entry.getString("item")), entry.getDouble("chance", 5.0),
                        height, amount, points, nonNegative(entry, "exp", 3), nonNegative(entry, "neededlvl", 0)));
            } catch (IllegalArgumentException failure) {
                throw new IllegalArgumentException("drops.yml: drops." + id + ": " + failure.getMessage(), failure);
            }
        }
        this.dropList = List.copyOf(parsed);
    }

    private static int nonNegative(ConfigurationSection section, String key, int fallback) {
        if (section.contains(key) && !section.isInt(key)) throw new IllegalArgumentException(key + " musi być liczbą całkowitą.");
        int value = section.getInt(key, fallback);
        if (value < 0) throw new IllegalArgumentException(key + " musi być nieujemne.");
        return value;
    }

    public double permissionBonus(Player player) {
        return config.getChances().values().stream()
                .filter(chance -> player.hasPermission(chance.getPerm()))
                .mapToDouble(chance -> chance.getChance() / 100.0)
                .sum();
    }

    public double effectiveChance(Drop drop, double bonus, User user) {
        int multiplier = config.isTurboDrop() || user.isTurboDrop() ? 2 : 1;
        return Math.clamp((drop.getChance() + bonus) * multiplier, 0.0, 1.0);
    }

    public void breakBlock(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material material = block.getType();
        boolean obsidian = material == Material.OBSIDIAN;
        if (!obsidian && !STONE_LIKE.contains(material)) return;
        Player player = event.getPlayer();
        if (!event.isDropItems() || player.getGameMode() != GameMode.SURVIVAL) return;
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!block.isPreferredTool(tool)) return;

        User user = plugin.getUserManager().getUser(player);
        if (user == null) return;
        int expMultiplier = config.isTurboExp() || user.isTurboExp() ? 2 : 1;
        long xpToDrop = event.getExpToDrop() + (long) (obsidian ? obsidianExp : stoneExp) * expMultiplier;
        event.setExpToDrop(saturate(xpToDrop));
        if (obsidian) return;

        var vanillaDrops = user.isCobble() ? block.getDrops(tool, player) : List.<ItemStack>of();
        var source = block.getLocation().add(0.5, 0.5, 0.5);
        int height = block.getY();
        int fortune = tool.getEnchantmentLevel(Enchantment.FORTUNE);
        event.setDropItems(false);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            // Later protection listeners may still have cancelled the original break.
            if (event.isCancelled() || event.isDropItems()) return;
            if (!player.isOnline() || plugin.getUserManager().getUser(player) != user) {
                vanillaDrops.forEach(item -> source.getWorld().dropItemNaturally(source, item));
                return;
            }

            vanillaDrops.forEach(item -> ItemUtils.giveItem(player, source, item, item.getAmount(), config));
            double bonus = permissionBonus(player);
            int levelAtBreak = user.getLvl();
            for (Drop drop : dropList) {
                if (user.isDisabled(drop) || !drop.isUnlocked(levelAtBreak)) continue;
                Count range = drop.getHeight();
                if (height < range.getMin() || height > range.getMax()) continue;
                if (ThreadLocalRandom.current().nextDouble() >= effectiveChance(drop, bonus, user)) continue;

                // Fortune follows the quantity rules documented in drops.yml.
                int amount = drop.getAmount().random() + (drop.isFortune() ? fortuneBonus(fortune) : 0);
                int xp = saturate((long) drop.getExp() * amount * expMultiplier);
                int points = config.isLvlStatus() ? saturate((long) drop.getPoints().random() * amount) : 0;
                ItemStack reward = drop.getItemStack();
                ItemUtils.giveItem(player, source, reward, amount, config);
                if (xp > 0) player.giveExp(xp);
                user.addDrop(drop, amount);
                int addedPoints = user.addPoints(points);
                if (user.isMessages()) {
                    ChatUtils.sendMessage(player, config.getDropMessage()
                            .replace("{PKT}", String.valueOf(addedPoints))
                            .replace("{EXP}", String.valueOf(xp))
                            .replace("{AMOUNT}", String.valueOf(amount))
                            .replace("{DROP}", drop.getName()));
                }
            }
        });
    }

    private static int fortuneBonus(int level) {
        if (level <= 0) return 0;
        var random = ThreadLocalRandom.current();
        return switch (level) {
            case 1 -> random.nextDouble() < 0.33 ? 1 : 0;
            case 2 -> random.nextDouble() < 0.50 ? random.nextInt(1, 3) : 0;
            default -> random.nextDouble() < 0.60 ? random.nextInt(1, 4) : 0;
        };
    }

    private static int saturate(long value) {
        return (int) Math.min(value, Integer.MAX_VALUE);
    }

    public List<Drop> getDropList() {
        return dropList;
    }
}
