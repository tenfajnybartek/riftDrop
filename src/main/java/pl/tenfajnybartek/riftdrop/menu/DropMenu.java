package pl.tenfajnybartek.riftdrop.menu;

import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import pl.tenfajnybartek.riftdrop.configuration.PluginConfiguration;
import pl.tenfajnybartek.riftdrop.mining.DropRule;
import pl.tenfajnybartek.riftdrop.mining.MiningService;
import pl.tenfajnybartek.riftdrop.profile.MiningProfile;
import pl.tenfajnybartek.riftdrop.text.BoostTime;
import pl.tenfajnybartek.riftdrop.text.Messages;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.IntStream;

public final class DropMenu {
    public static final class Holder implements InventoryHolder {
        private final DropMenu menu;
        private final Map<Integer, Consumer<MiningProfile>> actions = new HashMap<>();
        private final Inventory inventory;

        private Holder(DropMenu menu) {
            this.menu = menu;
            var layout = menu.layout;
            this.inventory = Bukkit.createInventory(this, layout.size(), Messages.toComponent(layout.title()));
        }

        @Override
        public Inventory getInventory() { return inventory; }

        public boolean belongsTo(DropMenu active) { return menu == active; }

        public boolean apply(int slot, MiningProfile profile) {
            var action = actions.get(slot);
            if (action == null) return false;
            action.accept(profile);
            return true;
        }
    }

    private final MiningService mining;
    private final PluginConfiguration config;
    private final MenuLayout layout;
    private final List<Integer> dropSlots;

    public DropMenu(PluginConfiguration config, MiningService mining) {
        this.config = config;
        this.layout = config.menu();
        this.mining = mining;
        Set<Integer> reserved = new HashSet<>();
        layout.buttons().values().forEach(button -> reserved.add(button.slot()));
        dropSlots = IntStream.range(0, layout.size()).filter(slot -> !reserved.contains(slot)).boxed().toList();
        if (mining.rules().size() > dropSlots.size()) {
            throw new IllegalArgumentException("gui.yml: menu nie ma wystarczającej liczby slotów na dropy.");
        }
    }

    public Inventory createInventory(Player player, MiningProfile profile) {
        Holder holder = new Holder(this);
        Inventory inventory = holder.getInventory();
        double bonus = mining.permissionBonus(player);
        int index = 0;
        for (DropRule drop : mining.rules()) {
            int slot = dropSlots.get(index++);
            if (drop.isUnlocked(profile.getLvl())) {
                var tokens = dropTokens(drop, profile, bonus);
                ItemStack icon = item(drop.getItemStack(), layout.dropName(), layout.dropLore(), tokens,
                        layout.glow() && !profile.isDisabled(drop));
                inventory.setItem(slot, icon);
                holder.actions.put(slot, target -> target.setDropEnabled(drop, target.isDisabled(drop)));
            } else {
                inventory.setItem(slot, tile(layout.locked(), Map.of(
                        "DROP-NAME", drop.getName(), "NEEDED-LEVEL", drop.getNeededLevel(), "PLAYER-LEVEL", profile.getLvl())));
            }
        }

        layout.buttons().forEach((control, button) -> {
            inventory.setItem(button.slot(), tile(button.tile(), controlTokens(control, profile)));
            Consumer<MiningProfile> action = switch (control) {
                case BASIC_DROP -> target -> target.setCobble(!target.isCobble());
                case MESSAGES -> target -> target.setMessages(!target.isMessages());
                case ENABLE_ALL -> target -> setAll(target, true);
                case DISABLE_ALL -> target -> setAll(target, false);
                case BOOSTS, LEVEL -> null;
            };
            if (action != null) holder.actions.put(button.slot(), action);
        });

        if (layout.fill()) {
            ItemStack filler = tile(layout.filler(), Map.of());
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                if (inventory.getItem(slot) == null) inventory.setItem(slot, filler.clone());
            }
        }
        return inventory;
    }

    private void setAll(MiningProfile profile, boolean enabled) {
        for (DropRule drop : mining.rules()) profile.setDropEnabled(drop, enabled);
    }

    private Map<String, ?> dropTokens(DropRule drop, MiningProfile profile, double bonus) {
        return Map.ofEntries(
                Map.entry("NAME", drop.getName()),
                Map.entry("CHANCE", percentage(drop.getChance())),
                Map.entry("CHANCE-BONUS", percentage(bonus)),
                Map.entry("CHANCE-SUM", percentage(mining.effectiveChance(drop, bonus, profile))),
                Map.entry("HEIGHT-MIN", drop.getHeight().getMin()), Map.entry("HEIGHT-MAX", drop.getHeight().getMax()),
                Map.entry("AMOUNT-MIN", drop.getAmount().getMin()), Map.entry("AMOUNT-MAX", drop.getAmount().getMax()),
                Map.entry("POINTS-MIN", drop.getPoints().getMin()), Map.entry("POINTS-MAX", drop.getPoints().getMax()),
                Map.entry("EXP", drop.getExp()), Map.entry("FORTUNE", status(drop.isFortune())),
                Map.entry("STATUS", status(!profile.isDisabled(drop))), Map.entry("MINED", profile.getDrop(drop)));
    }

    private Map<String, ?> controlTokens(MenuLayout.Control control, MiningProfile profile) {
        return switch (control) {
            case BASIC_DROP -> Map.of("STATUS", status(profile.isCobble()));
            case MESSAGES -> Map.of("STATUS", status(profile.isMessages()));
            case LEVEL -> Map.of("LVL", profile.getLvl(), "PKT", profile.getPoints(),
                    "POINTS-TO-LEVEL", profile.getPointsToNextLevel(), "POINTS-REQUIRED", profile.getPointsRequired());
            case BOOSTS -> {
                Map<String, String> tokens = new HashMap<>();
                addBoostTokens(tokens, "DROP", config.isTurboDrop(), config.getTurboDrop());
                addBoostTokens(tokens, "EXP", config.isTurboExp(), config.getTurboExp());
                addBoostTokens(tokens, "ME-DROP", profile.isTurboDrop(), profile.getTurboDrop());
                addBoostTokens(tokens, "ME-EXP", profile.isTurboExp(), profile.getTurboExp());
                yield tokens;
            }
            case ENABLE_ALL, DISABLE_ALL -> Map.of();
        };
    }

    private void addBoostTokens(Map<String, String> tokens, String key, boolean active, long expiry) {
        tokens.put(key + "-STATUS", status(active));
        tokens.put(key + "-EXPIRE", active ? BoostTime.durationToString(expiry) : "0s.");
    }

    private String status(boolean enabled) {
        return enabled ? layout.enabled() : layout.disabled();
    }

    private static String percentage(double value) {
        return String.format("%.2f", value * 100.0);
    }

    private static ItemStack tile(MenuLayout.Tile tile, Map<String, ?> tokens) {
        return item(new ItemStack(tile.material()), tile.name(), tile.lore(), tokens, false);
    }

    private static ItemStack item(ItemStack stack, String name, List<String> lore, Map<String, ?> tokens, boolean glow) {
        var meta = stack.getItemMeta();
        meta.displayName(Messages.toComponent(replace(name, tokens)));
        meta.lore(lore.stream().map(line -> Messages.toComponent(replace(line, tokens))).toList());
        if (glow) {
            if (!meta.hasEnchant(Enchantment.UNBREAKING)) meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private static String replace(String text, Map<String, ?> tokens) {
        var matcher = TOKEN.matcher(text);
        return matcher.replaceAll(match -> java.util.regex.Matcher.quoteReplacement(
                Objects.toString(tokens.get(match.group(1)), match.group())));
    }

    private static final java.util.regex.Pattern TOKEN = java.util.regex.Pattern.compile("\\{([A-Z-]+)}");
}
