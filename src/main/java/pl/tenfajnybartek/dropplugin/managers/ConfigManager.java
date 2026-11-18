package pl.tenfajnybartek.dropplugin.managers;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import pl.tenfajnybartek.dropplugin.base.DropPlugin;
import pl.tenfajnybartek.dropplugin.database.Database;
import pl.tenfajnybartek.dropplugin.objects.Chance;
import pl.tenfajnybartek.dropplugin.utils.ItemBuilder;

import java.util.*;

public class ConfigManager {
    private final HashMap<String, Chance> chances;
    private final String dbType;
    private final String dbSslMode;
    private final String dbHost;
    private final String dbUser;
    private final String dbPass;
    private final String dbBase;
    private final String dropMessage;
    private final String lvlUpMessage;
    private final String turboGetMessage;
    private final String turboMessage;
    private final String guiFillerName;
    private final String guiName;
    private final String guiStatusOn;
    private final String guiStatusOff;
    private final String guiItemName;
    private final String guiLockedName;
    private final String guiCobbleName;
    private final String guiMessagesName;
    private final String guiTurboName;
    private final String guiLevelName;
    private final String enableAllName;
    private final String disableAllName;
    private final String messageInvFull;
    private final String maxLevelMessage;
    private final String levelMessage;
    private final String actionbarMessage;
    private final String actionbarExpMessage;
    private final String cmdDropNoPermission;
    private final String cmdDropOnlyPlayers;
    private final String cmdADropNoPermission;
    private final List<String> cmdADropUsage;
    private final String cmdADropReloading;
    private final String cmdADropReloaded;
    private final String cmdADropReloadError;
    private final String cmdADropInvalidTime;
    private final String cmdADropTurboEnabled;
    private final String cmdADropTurboEnabledFor;
    private final String cmdADropPlayerOffline;
    private final String cmdADropPlayerDataError;
    private final String cmdADropLevelMinMax;
    private final String cmdADropLevelNotNumber;
    private final String cmdADropPointsMinMax;
    private final String cmdADropPointsNotNumber;
    private final String cmdADropLevelChanged;
    private final String cmdLevelNoPermission;
    private final String cmdLevelNoALevelPermission;
    private final String cmdLevelOnlyPlayers;
    private final String cmdLevelPlayerDataError;
    private final String cmdLevelTargetPlayerDataError;
    private final String cmdLevelPlayerOffline;
    private final String cmdLevelUsage;
    private final String cmdLevelNoData;
    private final String cmdLevelHeader;
    private final String cmdLevelNick;
    private final String cmdLevelLevelPoints;
    private final String cmdLevelPointsToNext;
    private final String cmdLevelFooter;
    private final ItemBuilder cobbleItem;
    private final ItemBuilder messagesItem;
    private final ItemBuilder turboItem;
    private final ItemBuilder levelItem;
    private final ItemBuilder enableItemAll;
    private final ItemBuilder disableItemAll;
    private final ItemBuilder fillerItem;
    private final ItemBuilder lockedItem;
    private final List<String> guiItemLores;
    private final List<String> guiLockedLores;
    private final List<String> guiCobbleLores;
    private final List<String> guiMessagesLores;
    private final List<String> guiTurboLores;
    private final List<String> enableAllLores;
    private final List<String> disableAllLores;
    private final List<String> guiLevelLores;
    private final List<Integer> chatLevels;
    private final int dbPort;
    private final int dbMaxPool;
    private final long dbConnectionTimeoutMs;
    private final long dbIdleTimeoutMs;
    private final long dbLeakDetectionThresholdMs;
    private final int guiSize;
    private final int cobbleSlot;
    private final int messagesSlot;
    private final int turboSlot;
    private final int levelSlot;
    private final int enableAllSlot;
    private final int disableAllSlot;
    private final int pointsToLvlUp;
    private final int maxLevel;
    private final boolean enchanted;
    private final boolean toInv;
    private final boolean messageInv;
    private final boolean cobbleStatus;
    private final boolean messagesStatus;
    private final boolean turboStatus;
    private final boolean levelStatus;
    private final boolean enableAllStatus;
    private final boolean disableAllStatus;
    private final boolean fillerStatus;
    private final boolean lvlStatus;
    private final boolean actionbarStatus;
    private final String toplevelNoPlayer;
    private final String toplevelFormat;
    private long turboDrop;
    private long turboExp;
    private final DropPlugin plugin;

    public ConfigManager(DropPlugin plugin) throws IOException, InvalidConfigurationException {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        YamlConfiguration fc = load(plugin, "config.yml");
        File guiFile = new File(plugin.getDataFolder(), "gui.yml");
        YamlConfiguration gui = new YamlConfiguration();
        if (guiFile.exists()) {
            gui = load(plugin, "gui.yml");
        } else {
            ConfigurationSection legacy = fc.getConfigurationSection("gui.yml");
            if (legacy == null) legacy = fc.getConfigurationSection("gui");
            if (legacy == null) {
                plugin.saveResource("gui.yml", false);
                gui = load(plugin, "gui.yml");
            } else {
                for (String path : legacy.getKeys(true)) {
                    if (!legacy.isConfigurationSection(path)) gui.set("gui." + path, legacy.get(path));
                }
            }
        }
        gui.setDefaults(defaults(plugin, "gui.yml"));
        fc.setDefaults(defaults(plugin, "config.yml"));

        this.dbType = getStringSafe(fc, "database.type", "sqlite").toLowerCase(Locale.ROOT);
        this.dbSslMode = getStringSafe(fc, "database.sslMode", "PREFERRED").toUpperCase(Locale.ROOT);
        this.dbHost = getStringSafe(fc, "database.host", "localhost");
        this.dbUser = getStringSafe(fc, "database.user", "root");
        this.dbPass = getStringSafe(fc, "database.password", "");
        this.dbBase = getStringSafe(fc, "database.base", "minecraft");
        this.dbPort = getIntSafe(fc, "database.port", 3306);
        this.dbMaxPool = getIntSafe(fc, "database.maxPool", 10);
        this.dbConnectionTimeoutMs = getLongSafe(fc, "database.connectionTimeoutMs", 30000L);
        this.dbIdleTimeoutMs = getLongSafe(fc, "database.idleTimeoutMs", 600000L);
        this.dbLeakDetectionThresholdMs = getLongSafe(fc, "database.leakDetectionThresholdMs", 0L);

        this.toplevelNoPlayer = getStringSafe(fc, "placeholders.toplevel-no-player", "-");
        this.toplevelFormat = getStringSafe(fc, "placeholders.toplevel-format", "&f{NAME} &7[&e{LEVEL}&7]");

        this.dropMessage = getStringSafe(fc, "messages.drop", "");
        this.turboMessage = getStringSafe(fc, "messages.turbo.message", "");
        this.turboGetMessage = getStringSafe(fc, "messages.turbo.get", "");
        this.turboExp = getLongSafe(fc, "turbos.exp", 0L);
        this.turboDrop = getLongSafe(fc, "turbos.drop", 0L);
        this.guiName = getStringSafe(gui, "gui.name", "Drops");
        this.guiSize = getIntSafe(gui, "gui.size", 9);
        this.guiFillerName = getStringSafe(gui, "gui.filler.name", "");
        this.fillerStatus = getBooleanSafe(gui, "gui.filler.status", true);
        this.enchanted = getBooleanSafe(gui, "gui.item.enchanted", false);
        this.toInv = getBooleanSafe(fc, "settings.toinv.status", true);
        this.messageInv = getBooleanSafe(fc, "settings.toinv.message-status", true);
        this.messageInvFull = getStringSafe(fc, "settings.toinv.messageFullInv", "");
        this.actionbarStatus = getBooleanSafe(fc, "settings.actionbar.status", false);
        this.actionbarMessage = getStringSafe(fc, "settings.actionbar.message", "");
        this.actionbarExpMessage = getStringSafe(fc, "settings.actionbar.exp-message", "&6TurboExp dostępny jeszcze przez: &f{TIME}&6!");
        this.cmdDropNoPermission = getStringSafe(fc, "messages.commands.drop.no-permission", "&4Blad: &cNie masz uprawnien do tej komendy! &7(dropplugin.cmd.drop)");
        this.cmdDropOnlyPlayers = getStringSafe(fc, "messages.commands.drop.only-players", "&4Blad: &cTa komenda moze byc uzywana tylko przez graczy.");
        this.cmdADropNoPermission = getStringSafe(fc, "messages.commands.adrop.no-permission", "&4Blad: &cNie masz uprawnien do tej komendy! &7(dropplugin.cmd.adrop)");
        this.cmdADropUsage = List.copyOf(getStringListSafe(fc, "messages.commands.adrop.usage"));
        this.cmdADropReloading = getStringSafe(fc, "messages.commands.adrop.reloading", "&7Przeladowywanie...");
        this.cmdADropReloaded = getStringSafe(fc, "messages.commands.adrop.reloaded", "&eDrop &7przeladowano! ({TIME}s)");
        this.cmdADropReloadError = getStringSafe(fc, "messages.commands.adrop.reload-error", "&4Blad: &cNie udalo sie przeladowac konfiguracji.");
        this.cmdADropInvalidTime = getStringSafe(fc, "messages.commands.adrop.invalid-time", "&4Blad: &cNiepoprawny czas: {TIME}");
        this.cmdADropTurboEnabled = getStringSafe(fc, "messages.commands.adrop.turbo-enabled", "&aWlaczyles {TYPE} na: {TIME}");
        this.cmdADropTurboEnabledFor = getStringSafe(fc, "messages.commands.adrop.turbo-enabled-for", "&aWlaczyles {TYPE} na: {TIME} dla: {PLAYER}");
        this.cmdADropPlayerOffline = getStringSafe(fc, "messages.commands.adrop.player-offline", "&4Blad: &cGracz jest offline!");
        this.cmdADropPlayerDataError = getStringSafe(fc, "messages.commands.adrop.player-data-error", "&4Blad: &cNie mozna zaladowac danych gracza!");
        this.cmdADropLevelMinMax = getStringSafe(fc, "messages.commands.adrop.level-min-max", "&4Blad: &cPoziom musi byc miedzy {MIN} a {MAX}!");
        this.cmdADropLevelNotNumber = getStringSafe(fc, "messages.commands.adrop.level-not-number", "&4Blad: &cPodany poziom nie jest liczba!");
        this.cmdADropPointsMinMax = getStringSafe(fc, "messages.commands.adrop.points-min-max", "&4Blad: &cPunkty musza byc miedzy {MIN} a {MAX}!");
        this.cmdADropPointsNotNumber = getStringSafe(fc, "messages.commands.adrop.points-not-number", "&4Blad: &cPodane punkty nie sa liczba!");
        this.cmdADropLevelChanged = getStringSafe(fc, "messages.commands.adrop.level-changed", "&aZmieniono poziom gracza &7{PLAYER} &az &7{OLD_LEVEL} &ana &7{NEW_LEVEL} &ai ustawiono punkty na &7{POINTS}");
        this.cmdLevelNoPermission = getStringSafe(fc, "messages.commands.level.no-permission", "&4Blad: &cNie masz uprawnien do tej komendy! &7(dropplugin.cmd.level)");
        this.cmdLevelNoALevelPermission = getStringSafe(fc, "messages.commands.level.no-alevel-permission", "&4Blad: &cNie masz uprawnien do sprawdzania innych graczy! &7(dropplugin.cmd.alevel)");
        this.cmdLevelOnlyPlayers = getStringSafe(fc, "messages.commands.level.only-players", "&4Blad: &cTa komenda moze byc uzyta tylko przez graczy.");
        this.cmdLevelPlayerDataError = getStringSafe(fc, "messages.commands.level.player-data-error", "&4Blad: &cNie mozna zaladowac Twoich danych!");
        this.cmdLevelTargetPlayerDataError = getStringSafe(fc, "messages.commands.level.target-player-data-error", "&4Blad: &cNie mozna zaladowac danych podanego gracza!");
        this.cmdLevelPlayerOffline = getStringSafe(fc, "messages.commands.level.player-offline", "&4Blad: &cPodany gracz nie jest online!");
        this.cmdLevelUsage = getStringSafe(fc, "messages.commands.level.usage", "&cPoprawne uzycie: &7/level [nick_gracza]");
        this.cmdLevelNoData = getStringSafe(fc, "messages.commands.level.no-data", "&4Blad: &cBrak danych gracza.");
        this.cmdLevelHeader = getStringSafe(fc, "messages.commands.level.header", "&8&m-----------------&8[ &f&lPOZIOM GRACZA &8]&8&m-----------------");
        this.cmdLevelNick = getStringSafe(fc, "messages.commands.level.nick", " &8* &7Nick: &e{PLAYER}");
        this.cmdLevelLevelPoints = getStringSafe(fc, "messages.commands.level.level-points", " &8* &7Aktualny poziom kopania: &a{LEVEL} &7, punkty: &6{POINTS}");
        this.cmdLevelPointsToNext = getStringSafe(fc, "messages.commands.level.points-to-next", " &8* &7Do nastepnego poziomu brakuje: &c{POINTS_TO_NEXT}");
        this.cmdLevelFooter = getStringSafe(fc, "messages.commands.level.footer", "&8&m-----------------&8[ &f&lPOZIOM GRACZA &8]&8&m-----------------");
        this.guiStatusOn = getStringSafe(gui, "gui.status.enable", "ON");
        this.guiStatusOff = getStringSafe(gui, "gui.status.disable", "OFF");
        this.guiItemName = getStringSafe(gui, "gui.item.name", "");
        this.guiItemLores = List.copyOf(getStringListSafe(gui, "gui.item.lores"));
        this.guiLockedName = getStringSafe(gui, "gui.locked.name", "&c&lZABLOKOWANY DROP");
        this.guiLockedLores = List.copyOf(getStringListSafe(gui, "gui.locked.lores"));
        this.guiCobbleName = getStringSafe(gui, "gui.cobble.name", "");
        this.cobbleStatus = getBooleanSafe(gui, "gui.cobble.status", true);
        this.guiCobbleLores = List.copyOf(getStringListSafe(gui, "gui.cobble.lores"));
        this.guiMessagesName = getStringSafe(gui, "gui.messages.name", "");
        this.messagesStatus = getBooleanSafe(gui, "gui.messages.status", true);
        this.guiMessagesLores = List.copyOf(getStringListSafe(gui, "gui.messages.lores"));
        this.guiTurboName = getStringSafe(gui, "gui.turbos.name", "");
        this.turboStatus = getBooleanSafe(gui, "gui.turbos.status", true);
        this.guiTurboLores = List.copyOf(getStringListSafe(gui, "gui.turbos.lores"));
        this.guiLevelName = getStringSafe(gui, "gui.level.name", "");
        this.levelStatus = getBooleanSafe(gui, "gui.level.status", true);
        this.guiLevelLores = List.copyOf(getStringListSafe(gui, "gui.level.lore"));
        this.enableAllName = getStringSafe(gui, "gui.enable-all.name", "");
        this.enableAllLores = List.copyOf(getStringListSafe(gui, "gui.enable-all.lores"));
        this.enableAllStatus = getBooleanSafe(gui, "gui.enable-all.status", true);
        this.disableAllName = getStringSafe(gui, "gui.disable-all.name", "");
        this.disableAllLores = List.copyOf(getStringListSafe(gui, "gui.disable-all.lores"));
        this.disableAllStatus = getBooleanSafe(gui, "gui.disable-all.status", true);
        this.cobbleSlot = getIntSafe(gui, "gui.cobble.slot", 10);
        this.messagesSlot = getIntSafe(gui, "gui.messages.slot", 11);
        this.turboSlot = getIntSafe(gui, "gui.turbos.slot", 12);
        this.enableAllSlot = getIntSafe(gui, "gui.enable-all.slot", 13);
        this.disableAllSlot = getIntSafe(gui, "gui.disable-all.slot", 14);
        this.levelSlot = getIntSafe(gui, "gui.level.slot", 15);
        this.lvlStatus = getBooleanSafe(fc, "settings.lvling.status", true);
        this.pointsToLvlUp = getIntSafe(fc, "settings.lvling.pointsToLvlup", 100);
        this.maxLevel = getIntSafe(fc, "settings.lvling.maxLevel", 100);
        this.chatLevels = List.copyOf(fc.getIntegerList("settings.lvling.chatLevels"));
        this.levelMessage = getStringSafe(fc, "settings.lvling.chatLevelMessage", "");
        this.maxLevelMessage = getStringSafe(fc, "settings.lvling.chatLevelMaxMessage", "");
        this.lvlUpMessage = getStringSafe(fc, "settings.lvling.chatLevelUpMessage", "");
        this.cobbleItem = createItemBuilderSafe(gui.getString("gui.cobble.item"), "gui.cobble.item");
        this.messagesItem = createItemBuilderSafe(gui.getString("gui.messages.item"), "gui.messages.item");
        this.turboItem = createItemBuilderSafe(gui.getString("gui.turbos.item"), "gui.turbos.item");
        this.levelItem = createItemBuilderSafe(gui.getString("gui.level.item"), "gui.level.item");
        this.fillerItem = createItemBuilderSafe(gui.getString("gui.filler.item"), "gui.filler.item");
        this.enableItemAll = createItemBuilderSafe(gui.getString("gui.enable-all.item"), "gui.enable-all.item");
        this.disableItemAll = createItemBuilderSafe(gui.getString("gui.disable-all.item"), "gui.disable-all.item");
        this.lockedItem = createItemBuilderSafe(gui.getString("gui.locked.item"), "gui.locked.item");

        this.chances = new HashMap<>();

        Object bonuses = fc.get("settings.chances");
        if (bonuses instanceof ConfigurationSection section) {
            for (String key : section.getKeys(false)) {
                String path = "settings.chances." + key;
                double bonus = fc.getDouble(path + ".additionalchance", Double.NaN);
                addChance(key, fc.getString(path + ".permission"), bonus, path);
            }
        } else if (bonuses instanceof List<?>) {
            for (String entry : fc.getStringList("settings.chances")) {
                String[] pair = entry.split("@", -1);
                if (pair.length != 2) throw new IllegalArgumentException("config.yml: settings.chances: " + entry);
                addChance(pair[0], pair[0], Double.parseDouble(pair[1]), "settings.chances");
            }
        } else {
            throw new IllegalArgumentException("config.yml: settings.chances musi być sekcją lub listą.");
        }

        require(Set.of("sqlite", "mysql").contains(dbType), "config.yml: database.type");
        require(Set.of("DISABLED", "PREFERRED", "REQUIRED", "VERIFY_CA", "VERIFY_IDENTITY").contains(dbSslMode),
                "config.yml: database.sslMode");
        require(dbPort > 0 && dbPort <= 65535, "config.yml: database.port");
        require(dbMaxPool > 0 && dbMaxPool <= 100, "config.yml: database.maxPool");
        require(dbConnectionTimeoutMs >= 250 && dbConnectionTimeoutMs <= Integer.MAX_VALUE,
                "config.yml: database.connectionTimeoutMs");
        require(dbIdleTimeoutMs == 0 || dbIdleTimeoutMs >= 10_000, "config.yml: database.idleTimeoutMs");
        require(dbLeakDetectionThresholdMs == 0 || dbLeakDetectionThresholdMs >= 2000, "config.yml: database.leakDetectionThresholdMs");
        require(pointsToLvlUp > 0 && maxLevel > 0 && maxLevel <= 10_000, "config.yml: settings.lvling");
        require(guiSize >= 9 && guiSize <= 54 && guiSize % 9 == 0, "gui.yml: gui.size");
        Set<Integer> occupied = new HashSet<>();
        validateSlot(occupied, cobbleStatus, cobbleSlot, "cobble");
        validateSlot(occupied, messagesStatus, messagesSlot, "messages");
        validateSlot(occupied, turboStatus, turboSlot, "turbos");
        validateSlot(occupied, levelStatus, levelSlot, "level");
        validateSlot(occupied, enableAllStatus, enableAllSlot, "enable-all");
        validateSlot(occupied, disableAllStatus, disableAllSlot, "disable-all");

        // Write the merged legacy layout only after both configuration inputs validate.
        if (!guiFile.exists()) {
            gui.options().copyDefaults(true);
            saveAtomically(gui, guiFile);
        }
    }

    public static YamlConfiguration load(DropPlugin plugin, String name) throws IOException, InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(new File(plugin.getDataFolder(), name));
        } catch (InvalidConfigurationException invalid) {
            throw new InvalidConfigurationException(name + ": " + invalid.getMessage(), invalid);
        }
        return config;
    }

    private static YamlConfiguration defaults(DropPlugin plugin, String name) throws IOException, InvalidConfigurationException {
        try (var reader = new InputStreamReader(Objects.requireNonNull(plugin.getResource(name)), StandardCharsets.UTF_8)) {
            YamlConfiguration config = new YamlConfiguration();
            config.load(reader);
            return config;
        }
    }

    private static void saveAtomically(YamlConfiguration config, File target) throws IOException {
        var path = target.toPath();
        var temp = Files.createTempFile(path.getParent(), target.getName(), ".tmp");
        try {
            config.save(temp.toFile());
            try {
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private void validateSlot(Set<Integer> occupied, boolean enabled, int slot, String path) {
        require(!enabled || (slot >= 0 && slot < guiSize && occupied.add(slot)), "gui.yml: gui." + path + ".slot");
    }

    private void addChance(String key, String permission, double bonus, String path) {
        require(permission != null && !permission.isBlank() && Double.isFinite(bonus) && bonus >= 0 && bonus <= 100,
                "config.yml: " + path);
        chances.put(key, new Chance(key, permission, bonus));
    }

    private static void require(boolean valid, String path) {
        if (!valid) throw new IllegalArgumentException("Niepoprawna wartość w " + path);
    }

    private ItemBuilder createItemBuilderSafe(String materialName, String path) {
        Material material = materialName == null ? null : Material.matchMaterial(materialName);
        require(material != null && material.isItem() && !material.isAir(), "gui.yml: " + path);
        return new ItemBuilder(material);
    }

    private static String getStringSafe(YamlConfiguration yaml, String path, String fallback) {
        Object value = yaml.get(path);
        if (value == null) return fallback;
        if (value instanceof String text) return text;
        throw invalidType(yaml, path, "tekstu");
    }

    private static int getIntSafe(YamlConfiguration yaml, String path, int fallback) {
        Object value = yaml.get(path);
        if (value == null) return fallback;
        if (value instanceof Integer integer) return integer;
        throw invalidType(yaml, path, "liczby całkowitej");
    }

    private static long getLongSafe(YamlConfiguration yaml, String path, long fallback) {
        Object value = yaml.get(path);
        if (value == null) return fallback;
        if (value instanceof Integer || value instanceof Long) return ((Number) value).longValue();
        throw invalidType(yaml, path, "liczby całkowitej");
    }

    private static boolean getBooleanSafe(YamlConfiguration yaml, String path, boolean fallback) {
        Object value = yaml.get(path);
        if (value == null) return fallback;
        if (value instanceof Boolean flag) return flag;
        throw invalidType(yaml, path, "true lub false");
    }

    private static List<String> getStringListSafe(YamlConfiguration yaml, String path) {
        Object value = yaml.get(path);
        if (value == null) return List.of();
        if (value instanceof List<?> list && list.stream().allMatch(String.class::isInstance)) {
            return list.stream().map(String.class::cast).toList();
        }
        throw invalidType(yaml, path, "listy tekstów");
    }

    private static IllegalArgumentException invalidType(YamlConfiguration yaml, String path, String type) {
        String file = yaml.getConfigurationSection("gui") != null && yaml.getConfigurationSection("database") == null
                ? "gui.yml" : "config.yml";
        return new IllegalArgumentException(file + ": " + path + " wymaga " + type + ".");
    }

    public void saveTurbo(boolean drop, long expiry) throws IOException, InvalidConfigurationException {
        YamlConfiguration current = load(plugin, "config.yml");
        current.set(drop ? "turbos.drop" : "turbos.exp", expiry);
        saveAtomically(current, new File(plugin.getDataFolder(), "config.yml"));
        if (drop) turboDrop = expiry;
        else turboExp = expiry;
    }

    public Database.Settings databaseSettings() {
        return new Database.Settings(dbType, dbHost, dbPort, dbBase, dbUser, dbPass, dbSslMode, dbMaxPool,
                dbConnectionTimeoutMs, dbIdleTimeoutMs, dbLeakDetectionThresholdMs);
    }

    public long getTurboDrop() { return this.turboDrop; }
    public long getTurboExp() { return this.turboExp; }
    public boolean isTurboDrop() { return this.getTurboDrop() > System.currentTimeMillis(); }
    public boolean isTurboExp() { return this.getTurboExp() > System.currentTimeMillis(); }
    public String getMaxLevelMessage() { return this.maxLevelMessage; }
    public List<Integer> getChatLevels() { return this.chatLevels; }
    public String getLevelMessage() { return this.levelMessage; }
    public int getMaxLevel() { return this.maxLevel; }
    public String getDropMessage() { return this.dropMessage; }
    public String getLvlUpMessage() { return this.lvlUpMessage; }
    public String getTurboGetMessage() { return this.turboGetMessage; }
    public String getTurboMessage() { return this.turboMessage; }
    public String getGuiFillerName() { return this.guiFillerName; }
    public String getGuiName() { return this.guiName; }
    public String getGuiStatusOn() { return this.guiStatusOn; }
    public String getGuiStatusOff() { return this.guiStatusOff; }
    public String getGuiItemName() { return this.guiItemName; }
    public String getGuiLockedName() { return this.guiLockedName; }
    public String getGuiCobbleName() { return this.guiCobbleName; }
    public String getGuiMessagesName() { return this.guiMessagesName; }
    public String getGuiTurboName() { return this.guiTurboName; }
    public String getGuiLevelName() { return this.guiLevelName; }
    public String getEnableAllName() { return this.enableAllName; }
    public String getDisableAllName() { return this.disableAllName; }
    public String getMessageInvFull() { return this.messageInvFull; }
    public String getActionbarMessage() { return this.actionbarMessage; }
    public String getActionbarExpMessage() { return this.actionbarExpMessage; }
    public ItemBuilder getCobbleItem() { return this.cobbleItem; }
    public ItemBuilder getMessagesItem() { return this.messagesItem; }
    public ItemBuilder getTurboItem() { return this.turboItem; }
    public ItemBuilder getLevelItem() { return this.levelItem; }
    public ItemBuilder getEnableItemAll() { return this.enableItemAll; }
    public ItemBuilder getDisableItemAll() { return this.disableItemAll; }
    public ItemBuilder getFillerItem() { return this.fillerItem; }
    public ItemBuilder getLockedItem() { return this.lockedItem; }
    public Map<String, Chance> getChances() { return Collections.unmodifiableMap(this.chances); }
    public List<String> getGuiItemLores() { return this.guiItemLores; }
    public List<String> getGuiLockedLores() { return this.guiLockedLores; }
    public List<String> getGuiCobbleLores() { return this.guiCobbleLores; }
    public List<String> getGuiMessagesLores() { return this.guiMessagesLores; }
    public List<String> getGuiTurboLores() { return this.guiTurboLores; }
    public List<String> getEnableAllLores() { return this.enableAllLores; }
    public List<String> getDisableAllLores() { return this.disableAllLores; }
    public List<String> getGuiLevelLores() { return this.guiLevelLores; }
    public int getGuiSize() { return this.guiSize; }
    public int getCobbleSlot() { return this.cobbleSlot; }
    public int getMessagesSlot() { return this.messagesSlot; }
    public int getTurboSlot() { return this.turboSlot; }
    public int getLevelSlot() { return this.levelSlot; }
    public int getEnableAllSlot() { return this.enableAllSlot; }
    public int getDisableAllSlot() { return this.disableAllSlot; }
    public int getPointsToLvlUp() { return this.pointsToLvlUp; }
    public boolean isEnchanted() { return this.enchanted; }
    public boolean isToInv() { return this.toInv; }
    public boolean isMessageInv() { return this.messageInv; }
    public boolean isCobbleStatus() { return this.cobbleStatus; }
    public boolean isMessagesStatus() { return this.messagesStatus; }
    public boolean isTurboStatus() { return this.turboStatus; }
    public boolean isLevelStatus() { return this.levelStatus; }
    public boolean isEnableAllStatus() { return this.enableAllStatus; }
    public boolean isDisableAllStatus() { return this.disableAllStatus; }
    public boolean isFillerStatus() { return this.fillerStatus; }
    public boolean isLvlStatus() { return this.lvlStatus; }
    public boolean isActionbarStatus() { return this.actionbarStatus; }
    public String getCmdDropNoPermission() { return this.cmdDropNoPermission; }
    public String getCmdDropOnlyPlayers() { return this.cmdDropOnlyPlayers; }
    public String getCmdADropNoPermission() { return this.cmdADropNoPermission; }
    public List<String> getCmdADropUsage() { return this.cmdADropUsage; }
    public String getCmdADropReloading() { return this.cmdADropReloading; }
    public String getCmdADropReloaded() { return this.cmdADropReloaded; }
    public String getCmdADropReloadError() { return this.cmdADropReloadError; }
    public String getCmdADropInvalidTime() { return this.cmdADropInvalidTime; }
    public String getCmdADropTurboEnabled() { return this.cmdADropTurboEnabled; }
    public String getCmdADropTurboEnabledFor() { return this.cmdADropTurboEnabledFor; }
    public String getCmdADropPlayerOffline() { return this.cmdADropPlayerOffline; }
    public String getCmdADropPlayerDataError() { return this.cmdADropPlayerDataError; }
    public String getCmdADropLevelMinMax() { return this.cmdADropLevelMinMax; }
    public String getCmdADropLevelNotNumber() { return this.cmdADropLevelNotNumber; }
    public String getCmdADropPointsMinMax() { return this.cmdADropPointsMinMax; }
    public String getCmdADropPointsNotNumber() { return this.cmdADropPointsNotNumber; }
    public String getCmdADropLevelChanged() { return this.cmdADropLevelChanged; }
    public String getCmdLevelNoPermission() { return this.cmdLevelNoPermission; }
    public String getCmdLevelNoALevelPermission() { return this.cmdLevelNoALevelPermission; }
    public String getCmdLevelOnlyPlayers() { return this.cmdLevelOnlyPlayers; }
    public String getCmdLevelPlayerDataError() { return this.cmdLevelPlayerDataError; }
    public String getCmdLevelTargetPlayerDataError() { return this.cmdLevelTargetPlayerDataError; }
    public String getCmdLevelPlayerOffline() { return this.cmdLevelPlayerOffline; }
    public String getCmdLevelUsage() { return this.cmdLevelUsage; }
    public String getCmdLevelNoData() { return this.cmdLevelNoData; }
    public String getCmdLevelHeader() { return this.cmdLevelHeader; }
    public String getCmdLevelNick() { return this.cmdLevelNick; }
    public String getCmdLevelLevelPoints() { return this.cmdLevelLevelPoints; }
    public String getCmdLevelPointsToNext() { return this.cmdLevelPointsToNext; }
    public String getCmdLevelFooter() { return this.cmdLevelFooter; }
    public String getToplevelNoPlayer() { return this.toplevelNoPlayer; }
    public String getToplevelFormat() { return this.toplevelFormat; }
}
