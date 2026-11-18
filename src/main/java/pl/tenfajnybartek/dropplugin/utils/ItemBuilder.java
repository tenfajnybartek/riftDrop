package pl.tenfajnybartek.dropplugin.utils;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.stream.Collectors;

public final class ItemBuilder {
    private final ItemStack itemStack;
    private final ItemMeta itemMeta;

    public ItemBuilder(Material material) {
        if (material == null) throw new IllegalArgumentException("Material cannot be null");
        this.itemStack = new ItemStack(material);
        this.itemMeta = this.itemStack.getItemMeta();
    }

    public ItemBuilder(ItemStack itemStack) {
        if (itemStack == null) throw new IllegalArgumentException("ItemStack cannot be null");
        this.itemStack = itemStack;
        this.itemMeta = itemStack.getItemMeta();
    }

    private void refreshMeta() {
        this.itemStack.setItemMeta(this.itemMeta);
    }

    public ItemBuilder setName(String name) {
        if (name == null) return this;
        Component comp = ChatUtils.toComponent(name);
        this.itemMeta.displayName(comp);
        this.refreshMeta();
        return this;
    }

    public ItemBuilder setLore(List<String> lore) {
        if (lore == null) return this;
        List<Component> components = lore.stream()
                .map(ChatUtils::toComponent)
                .collect(Collectors.toList());
        this.itemMeta.lore(components);
        this.refreshMeta();
        return this;
    }

    public ItemBuilder addEnchant(Enchantment enchant, int level) {
        if (enchant == null) return this;
        this.itemMeta.addEnchant(enchant, level, true);
        this.refreshMeta();
        return this;
    }

    public ItemBuilder setFlag(ItemFlag flag) {
        if (flag == null) return this;
        this.itemMeta.addItemFlags(flag);
        this.refreshMeta();
        return this;
    }

    public ItemStack build() {
        this.itemStack.setItemMeta(this.itemMeta);
        return this.itemStack.clone();
    }
}
