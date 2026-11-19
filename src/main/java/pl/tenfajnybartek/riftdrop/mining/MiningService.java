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
import pl.tenfajnybartek.riftdrop.event.MiningRewardEvent;
import pl.tenfajnybartek.riftdrop.boost.BoostType;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class MiningService {
    private final RiftDropPlugin plugin;
    private final PluginConfiguration config;
    private final DropConfiguration drops;
    private final int stoneExp;
    private final int obsidianExp;

    public MiningService(RiftDropPlugin plugin, PluginConfiguration config, DropConfiguration dropConfig) {
        this.plugin = plugin;
        this.config = config;
        this.stoneExp = dropConfig.stoneExperience();
        this.obsidianExp = dropConfig.obsidianExperience();
        this.drops = dropConfig;
    }

    public double permissionBonus(Player player) {
        return config.getChances().values().stream()
                .filter(chance -> Permissions.has(player, chance.getPerm()))
                .mapToDouble(chance -> chance.getChance() / 100.0)
                .sum();
    }

    public double effectiveChance(DropRule drop, double bonus, MiningProfile user) {
        int multiplier = plugin.boosts().globalExpiry(BoostType.DROP) > System.currentTimeMillis() || user.isTurboDrop() ? 2 : 1;
        return Math.clamp((drop.getChance() + bonus) * multiplier, 0.0, 1.0);
    }

    public void breakBlock(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material material = block.getType();
        boolean obsidian = material == Material.OBSIDIAN;
        boolean stone = DropConfiguration.DEFAULT_SOURCES.contains(material);
        List<DropRule> candidates = drops.bySource().getOrDefault(material, List.of());
        if (!obsidian && !stone && candidates.isEmpty()) return;
        Player player = event.getPlayer();
        if (!event.isDropItems() || player.getGameMode() != GameMode.SURVIVAL) return;
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!block.isPreferredTool(tool)) return;

        MiningProfile user = plugin.profiles().getUser(player);
        if (user == null) return;
        int expMultiplier = plugin.boosts().globalExpiry(BoostType.EXP) > System.currentTimeMillis() || user.isTurboExp() ? 2 : 1;
        int baseExperience = obsidian ? obsidianExp : stone ? stoneExp : 0;
        long xpToDrop = event.getExpToDrop() + (long) baseExperience * expMultiplier;
        event.setExpToDrop(saturate(xpToDrop));
        if (obsidian && candidates.isEmpty()) return;

        var vanillaDrops = user.isCobble() ? block.getDrops(tool, player) : List.<ItemStack>of();
        var source = block.getLocation().add(0.5, 0.5, 0.5);
        int height = block.getY();
        int regionalMinimum = drops.regions().stream().filter(region -> region.contains(block))
                .mapToInt(region -> region.minimumLevel()).max().orElse(0);
        var origin = new MiningRewardEvent.Source(block.getWorld().getUID(), block.getX(), height, block.getZ(), material);
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
            for (DropRule drop : candidates) {
                int requiredLevel = Math.max(regionalMinimum, drop.getNeededLevel());
                if (user.isDisabled(drop) || levelAtBreak < requiredLevel) continue;
                IntRange range = drop.getHeight();
                if (height < range.getMin() || height > range.getMax()) continue;
                if (ThreadLocalRandom.current().nextDouble() >= effectiveChance(drop, bonus, user)) continue;

                // Fortune follows the quantity rules documented in drops.yml.
                int amount = drop.getAmount().random() + (drop.isFortune() ? fortuneBonus(fortune) : 0);
                int xp = saturate((long) drop.getExp() * amount * expMultiplier);
                int points = config.isLvlStatus() ? saturate((long) drop.getPoints().random() * amount) : 0;
                ItemStack reward = drop.getItemStack();
                var rewardEvent = new MiningRewardEvent(player.getUniqueId(), origin, drop.getId(), reward,
                        amount, xp, points, requiredLevel);
                plugin.getServer().getPluginManager().callEvent(rewardEvent);
                if (rewardEvent.isCancelled() || !player.isOnline() || plugin.profiles().getUser(player) != user) continue;
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
        return drops.rules();
    }
}
