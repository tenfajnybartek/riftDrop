package pl.tenfajnybartek.dropplugin.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.apache.commons.lang3.StringUtils;
import pl.tenfajnybartek.dropplugin.managers.ConfigManager;
import pl.tenfajnybartek.dropplugin.managers.DropManager;
import pl.tenfajnybartek.dropplugin.objects.Drop;
import pl.tenfajnybartek.dropplugin.objects.User;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class DropMenu {
    public static final class Holder implements InventoryHolder {
        private final DropMenu menu;
        private final Map<Integer, Consumer<User>> actions = new HashMap<>();
        private final Inventory inventory;

        private Holder(DropMenu menu) {
            this.menu = menu;
            this.inventory = Bukkit.createInventory(this, menu.config.getGuiSize(), ChatUtils.toComponent(menu.config.getGuiName()));
        }

        @Override
        public Inventory getInventory() { return inventory; }

        public boolean belongsTo(DropMenu active) { return menu == active; }

        public boolean apply(int slot, User user) {
            var action = actions.get(slot);
            if (action == null) return false;
            action.accept(user);
            return true;
        }
    }

    private final DropManager dropManager;
    private final ConfigManager config;
    private final List<Integer> dropSlots;

    public DropMenu(ConfigManager config, DropManager dropManager) {
        this.config = config;
        this.dropManager = dropManager;
        Set<Integer> reserved = new HashSet<>();
        if (config.isCobbleStatus()) reserved.add(config.getCobbleSlot());
        if (config.isMessagesStatus()) reserved.add(config.getMessagesSlot());
        if (config.isTurboStatus()) reserved.add(config.getTurboSlot());
        if (config.isLevelStatus()) reserved.add(config.getLevelSlot());
        if (config.isEnableAllStatus()) reserved.add(config.getEnableAllSlot());
        if (config.isDisableAllStatus()) reserved.add(config.getDisableAllSlot());
        dropSlots = java.util.stream.IntStream.range(0, config.getGuiSize()).boxed()
                .filter(slot -> !reserved.contains(slot)).toList();
        if (dropManager.getDropList().size() > dropSlots.size()) {
            throw new IllegalArgumentException("gui.yml: menu nie ma wystarczającej liczby slotów na dropy.");
        }
    }

    public Inventory createInventory(Player player, User user) {
        Holder holder = new Holder(this);
        Inventory inventory = holder.getInventory();
        int size = inventory.getSize();

        ItemStack cobble = this.config.getCobbleItem()
                .setName(this.config.getGuiCobbleName())
                .setLore(this.config.getGuiCobbleLores().stream()
                        .map(entry -> this.replaced(user, entry, 1))
                        .collect(Collectors.toList()))
                .build();

        ItemStack messages = this.config.getMessagesItem()
                .setName(this.config.getGuiMessagesName())
                .setLore(this.config.getGuiMessagesLores().stream()
                        .map(entry -> this.replaced(user, entry, 2))
                        .collect(Collectors.toList()))
                .build();

        ItemStack turbos = this.config.getTurboItem()
                .setName(this.config.getGuiTurboName())
                .setLore(this.config.getGuiTurboLores().stream()
                        .map(entry -> this.replaced(user, entry, 3))
                        .collect(Collectors.toList()))
                .build();

        ItemStack level = this.config.getLevelItem()
                .setName(this.config.getGuiLevelName())
                .setLore(this.config.getGuiLevelLores().stream()
                        .map(entry -> this.replaced(user, entry, 4))
                        .collect(Collectors.toList()))
                .build();

        ItemStack enableAll = this.config.getEnableItemAll()
                .setName(this.config.getEnableAllName())
                .setLore(this.config.getEnableAllLores())
                .build();

        ItemStack disableAll = this.config.getDisableItemAll()
                .setName(this.config.getDisableAllName())
                .setLore(this.config.getDisableAllLores())
                .build();

        ItemStack filler = this.config.getFillerItem()
                .setName(this.config.getGuiFillerName())
                .build();

        double bonus = dropManager.permissionBonus(player);
        int index = 0;
        for (Drop drop : this.dropManager.getDropList()) {
            int slot = dropSlots.get(index++);
            int playerLevel = (user != null) ? user.getLvl() : 1;
            boolean isLocked = !drop.isUnlocked(playerLevel);
            
            ItemBuilder item;
            if (isLocked) {
                item = this.config.getLockedItem()
                        .setName(this.config.getGuiLockedName())
                        .setLore(this.config.getGuiLockedLores().stream()
                                .map(entry -> entry
                                        .replace("{DROP-NAME}", drop.getName())
                                        .replace("{NEEDED-LEVEL}", String.valueOf(drop.getNeededLevel()))
                                        .replace("{PLAYER-LEVEL}", String.valueOf(playerLevel)))
                                .collect(Collectors.toList()));
            } else {
                item = new ItemBuilder(drop.getItemStack())
                        .setName(StringUtils.replace(this.config.getGuiItemName(), "{NAME}", drop.getName()))
                        .setLore(this.config.getGuiItemLores().stream()
                                .map(entry -> this.replaced(user, drop, entry, bonus))
                                .collect(Collectors.toList()));

                if (this.config.isEnchanted()) {
                    boolean enabledForUser = !(user != null && user.isDisabled(drop));
                    if (enabledForUser) {
                        item.addEnchant(Enchantment.UNBREAKING, 1);
                        item.setFlag(ItemFlag.HIDE_ENCHANTS);
                    }
                }
            }

            inventory.setItem(slot, item.build());
            if (!isLocked) holder.actions.put(slot, target -> target.setDropEnabled(drop, target.isDisabled(drop)));
        }

        if (this.config.isFillerStatus()) {
            for (int x = 0; x < size; ++x) {
                if (inventory.getItem(x) != null) continue;
                inventory.setItem(x, filler);
            }
        }

        if (this.config.isEnableAllStatus()) {
            int slot = this.config.getEnableAllSlot();
            inventory.setItem(slot, enableAll);
            holder.actions.put(slot, target -> dropManager.getDropList().forEach(drop -> target.setDropEnabled(drop, true)));
        }

        if (this.config.isDisableAllStatus()) {
            int slot = this.config.getDisableAllSlot();
            inventory.setItem(slot, disableAll);
            holder.actions.put(slot, target -> dropManager.getDropList().forEach(drop -> target.setDropEnabled(drop, false)));
        }

        if (this.config.isTurboStatus()) {
            int slot = this.config.getTurboSlot();
            inventory.setItem(slot, turbos);
        }

        if (this.config.isMessagesStatus()) {
            int slot = this.config.getMessagesSlot();
            inventory.setItem(slot, messages);
            holder.actions.put(slot, target -> target.setMessages(!target.isMessages()));
        }

        if (this.config.isCobbleStatus()) {
            int slot = this.config.getCobbleSlot();
            inventory.setItem(slot, cobble);
            holder.actions.put(slot, target -> target.setCobble(!target.isCobble()));
        }

        if (this.config.isLevelStatus()) {
            int slot = this.config.getLevelSlot();
            inventory.setItem(slot, level);
        }

        return inventory;
    }

    private String replaced(User user, Drop drop, String entry, double bonus) {
        double sumChance = dropManager.effectiveChance(drop, bonus, user);
        entry = StringUtils.replace(entry, "{CHANCE}", String.format("%.2f", drop.getChance() * 100.0));
        entry = StringUtils.replace(entry, "{CHANCE-BONUS}", String.format("%.2f", bonus * 100.0));
        entry = StringUtils.replace(entry, "{CHANCE-SUM}", String.format("%.2f", sumChance * 100.0));
        entry = StringUtils.replace(entry, "{HEIGHT-MIN}", String.valueOf(drop.getHeight().getMin()));
        entry = StringUtils.replace(entry, "{HEIGHT-MAX}", String.valueOf(drop.getHeight().getMax()));
        entry = StringUtils.replace(entry, "{EXP}", String.valueOf(drop.getExp()));
        entry = StringUtils.replace(entry, "{AMOUNT-MIN}", String.valueOf(drop.getAmount().getMin()));
        entry = StringUtils.replace(entry, "{AMOUNT-MAX}", String.valueOf(drop.getAmount().getMax()));
        entry = StringUtils.replace(entry, "{POINTS-MIN}", String.valueOf(drop.getPoints().getMin()));
        entry = StringUtils.replace(entry, "{POINTS-MAX}", String.valueOf(drop.getPoints().getMax()));
        entry = StringUtils.replace(entry, "{STATUS}", (user != null && user.isDisabled(drop)) ? this.config.getGuiStatusOff() : this.config.getGuiStatusOn());
        entry = StringUtils.replace(entry, "{FORTUNE}", drop.isFortune() ? this.config.getGuiStatusOn() : this.config.getGuiStatusOff());
        entry = StringUtils.replace(entry, "{MINED}", Integer.toString(user != null ? user.getDrop(drop) : 0));
        return entry;
    }

    private String replaced(User user, String entry, int type) {
        switch (type) {
            case 1: {
                entry = StringUtils.replace(entry, "{STATUS}", (user != null && user.isCobble()) ? this.config.getGuiStatusOn() : this.config.getGuiStatusOff());
                return entry;
            }
            case 2: {
                entry = StringUtils.replace(entry, "{STATUS}", (user != null && user.isMessages()) ? this.config.getGuiStatusOn() : this.config.getGuiStatusOff());
                return entry;
            }
            case 3: {
                entry = StringUtils.replace(entry, "{DROP-STATUS}", this.config.isTurboDrop() ? this.config.getGuiStatusOn() : this.config.getGuiStatusOff());
                entry = StringUtils.replace(entry, "{DROP-EXPIRE}", this.config.isTurboDrop() ? DataUtils.durationToString(this.config.getTurboDrop()) : "0s.");
                entry = StringUtils.replace(entry, "{EXP-STATUS}", this.config.isTurboExp() ? this.config.getGuiStatusOn() : this.config.getGuiStatusOff());
                entry = StringUtils.replace(entry, "{EXP-EXPIRE}", this.config.isTurboExp() ? DataUtils.durationToString(this.config.getTurboExp()) : "0s.");
                entry = StringUtils.replace(entry, "{ME-DROP-STATUS}", (user != null && user.isTurboDrop()) ? this.config.getGuiStatusOn() : this.config.getGuiStatusOff());
                entry = StringUtils.replace(entry, "{ME-DROP-EXPIRE}", (user != null && user.isTurboDrop()) ? DataUtils.durationToString(user.getTurboDrop()) : "0s.");
                entry = StringUtils.replace(entry, "{ME-EXP-STATUS}", (user != null && user.isTurboExp()) ? this.config.getGuiStatusOn() : this.config.getGuiStatusOff());
                entry = StringUtils.replace(entry, "{ME-EXP-EXPIRE}", (user != null && user.isTurboExp()) ? DataUtils.durationToString(user.getTurboExp()) : "0s.");
                return entry;
            }
            case 4: {
                entry = StringUtils.replace(entry, "{LVL}", String.valueOf(user != null ? user.getLvl() : 0));
                entry = StringUtils.replace(entry, "{PKT}", String.valueOf(user != null ? user.getPoints() : 0));
                entry = StringUtils.replace(entry, "{POINTS-TO-LEVEL}", String.valueOf(user != null ? user.getPointsToNextLevel() : 0));
                entry = StringUtils.replace(entry, "{POINTS-REQUIRED}", String.valueOf(user.getPointsRequired()));
                return entry;
            }
        }
        return entry;
    }
}
