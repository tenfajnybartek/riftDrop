package pl.tenfajnybartek.riftdrop.mining;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import pl.tenfajnybartek.riftdrop.RiftDropPlugin;
import pl.tenfajnybartek.riftdrop.command.Permissions;
import pl.tenfajnybartek.riftdrop.configuration.DropConfiguration;
import pl.tenfajnybartek.riftdrop.configuration.PluginConfiguration;
import pl.tenfajnybartek.riftdrop.profile.MiningProfile;
import pl.tenfajnybartek.riftdrop.text.Messages;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class MiningService {
    private static final Set<Material> STONE_LIKE = EnumSet.of(
            Material.STONE, Material.GRANITE, Material.DIORITE, Material.ANDESITE, Material.DEEPSLATE);

    private final RiftDropPlugin plugin;
    private final PluginConfiguration config;
    private final List<DropRule> dropList;
    private final int stoneExp;
    private final int obsidianExp;

    public MiningService(RiftDropPlugin plugin, PluginConfiguration config, DropConfiguration dropConfig) {
        this.plugin = plugin;
        this.config = config;
        this.stoneExp = dropConfig.stoneExperience();
        this.obsidianExp = dropConfig.obsidianExperience();
        this.dropList = dropConfig.rules();
    }

    public double permissionBonus(Player player) {
        return config.getChances().values().stream()
                .filter(chance -> Permissions.has(player, chance.getPerm()))
                .mapToDouble(chance -> chance.getChance() / 100.0)
                .sum();
    }

    public double effectiveChance(DropRule drop, double bonus, MiningProfile user) {
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

        MiningProfile user = plugin.profiles().getUser(player);
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
            if (!player.isOnline() || plugin.profiles().getUser(player) != user) {
                vanillaDrops.forEach(item -> source.getWorld().dropItemNaturally(source, item));
                return;
            }

            vanillaDrops.forEach(item -> RewardDelivery.giveItem(player, source, item, item.getAmount(), config));
            double bonus = permissionBonus(player);
            int levelAtBreak = user.getLvl();
            for (DropRule drop : dropList) {
                if (user.isDisabled(drop) || !drop.isUnlocked(levelAtBreak)) continue;
                IntRange range = drop.getHeight();
                if (height < range.getMin() || height > range.getMax()) continue;
                if (ThreadLocalRandom.current().nextDouble() >= effectiveChance(drop, bonus, user)) continue;

                // Fortune follows the quantity rules documented in drops.yml.
                int amount = drop.getAmount().random() + (drop.isFortune() ? fortuneBonus(fortune) : 0);
                int xp = saturate((long) drop.getExp() * amount * expMultiplier);
                int points = config.isLvlStatus() ? saturate((long) drop.getPoints().random() * amount) : 0;
                ItemStack reward = drop.getItemStack();
                RewardDelivery.giveItem(player, source, reward, amount, config);
                if (xp > 0) player.giveExp(xp);
                user.addDrop(drop, amount);
                int addedPoints = user.addPoints(points);
                if (user.isMessages()) {
                    Messages.sendMessage(player, config.getDropMessage()
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

    public List<DropRule> rules() {
        return dropList;
    }
}
