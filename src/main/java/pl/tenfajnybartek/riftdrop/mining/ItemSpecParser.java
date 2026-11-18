package pl.tenfajnybartek.riftdrop.mining;

import org.bukkit.Material;
import pl.tenfajnybartek.riftdrop.text.Messages;
import org.bukkit.NamespacedKey;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.Arrays;
import java.util.Locale;

public final class ItemSpecParser {
    private ItemSpecParser() {}

    public static ItemStack parseItemStack(String input) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("Brakuje item.");
        String[] options = input.trim().split("\\s+");
        String materialName = Arrays.stream(options)
                .filter(option -> option.regionMatches(true, 0, "material:", 0, 9))
                .map(option -> option.substring(9)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Brakuje material w item."));
        Material material = Material.matchMaterial(materialName);
        if (material == null || material.isAir() || !material.isItem()) {
            throw new IllegalArgumentException("Nieznany lub niedozwolony materiał: " + materialName);
        }
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        for (String option : options) {
            String[] pair = option.split(":", 2);
            if (pair.length != 2) throw new IllegalArgumentException("Nieprawidłowa opcja item: " + option);
            String value = pair[1];
            switch (pair[0].toLowerCase(Locale.ROOT)) {
                case "material" -> { }
                case "amount" -> {
                    if (Integer.parseInt(value) < 1) throw new IllegalArgumentException("item amount musi być dodatnie.");
                    // Quantity is determined by the drop's amount range.
                }
                case "name" -> meta.displayName(Messages.toComponent(value.replace('_', ' ')));
                case "lore" -> meta.lore(Arrays.stream(value.split("@nl", -1))
                        .map(line -> Messages.toComponent(line.replace('_', ' '))).toList());
                case "data", "durability" -> {
                    if (!(meta instanceof Damageable damageable) || material.getMaxDurability() <= 0) {
                        throw new IllegalArgumentException("Materiał nie obsługuje wytrzymałości.");
                    }
                    int damage = Integer.parseInt(value);
                    if (damage < 0 || damage >= material.getMaxDurability()) {
                        throw new IllegalArgumentException("Nieprawidłowa wartość uszkodzeń itemu: " + damage);
                    }
                    damageable.setDamage(damage);
                }
                case "enchants" -> {
                    for (String token : value.split("@nl")) {
                        String[] enchant = token.split(";", 2);
                        if (enchant.length != 2) throw new IllegalArgumentException("Nieprawidłowy enchant: " + token);
                        NamespacedKey key = NamespacedKey.fromString(enchant[0].toLowerCase(Locale.ROOT));
                        Enchantment type = key == null ? null
                                : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key);
                        if (type == null) throw new IllegalArgumentException("Nieznany enchant: " + enchant[0]);
                        int level = Integer.parseInt(enchant[1]);
                        if (level < 1 || level > 255 || !meta.addEnchant(type, level, true)) {
                            throw new IllegalArgumentException("Nieprawidłowy lub powtórzony enchant: " + token);
                        }
                    }
                }
                default -> throw new IllegalArgumentException("Nieznane pole item: " + pair[0]);
            }
        }
        item.setItemMeta(meta);
        return item;
    }
}
