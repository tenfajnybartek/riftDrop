# 🔧 Lista poprawek dla tfbDrop Plugin

## Spis treści
1. [build.gradle](#buildgradle)
2. [DropPlugin.java](#dropplugin-główna-klasa)
3. [Database.java](#database-obsługa-bazy-danych)
4. [SaverTask.java](#savertask-auto-save)
5. [UserManager.java](#usermanager-zarządzanie-użytkownikami)
6. [Podsumowanie priorytetów](#podsumowanie-priorytetów)

---

## build.gradle

### ⚠️ Do poprawy:

#### 1. MySQL Connector jest deprecated
**Problem:**
```gradle
implementation 'mysql:mysql-connector-java:8.0.33'
```

**Fix:**
```gradle
implementation 'com.mysql:mysql-connector-j:8.3.0'
```

#### 2. Apache Commons Lang3 - duplikacja
**Problem:** Paper API już zawiera commons-lang3

**Fix:**
```gradle
compileOnly 'org.apache.commons:commons-lang3:3.18.0'  // zamiast implementation
```

#### 3. PlaceholderAPI - starsza wersja
**Problem:**
```gradle
compileOnly 'me.clip:placeholderapi:2.11.6'
```

**Fix:**
```gradle
compileOnly 'me.clip:placeholderapi:2.11.7'
```

**Ocena:** 8/10 → 9/10 po poprawkach

---

## DropPlugin (główna klasa)

### ⚠️ Do poprawy:

#### 1. Duplikacja getterów
**Problem:**
```java
public ConfigManager getConfigManager() {
    return this.configManager;
}
public ConfigManager getPluginConfig() {  // ← DUPLIKAT!
    return this.configManager;
}
```

**Fix:** Usuń `getPluginConfig()` lub zostaw tylko jeden

---

#### 2. Niepotrzebne pole Logger
**Problem:**
```java
private Logger logger;

@Override
public void onEnable() {
    this.logger = this.getLogger();
}
```

**Fix:** Usuń pole, używaj `getLogger()` bezpośrednio
```java
// Zamiast this.logger.info()
getLogger().info("...");
```

---

#### 3. RegisterListeners - niepotrzebna tablica Object[]
**Problem:**
```java
Object[] listeners = new Object[] {
    new BlockBreakListener(this),
    // ...
};

for (Object obj : listeners) {
    if (obj instanceof Listener) {  // ← zawsze true
        Bukkit.getPluginManager().registerEvents((Listener) obj, this);
    }
}
```

**Fix:**
```java
private void registerListeners() {
    registerListener(new BlockBreakListener(this));
    registerListener(new InventoryClickListener(this));
    registerListener(new PlayerJoinLeaveListener(this));
    registerListener(new UserLevelChangeListener(this));
    registerListener(new UserPointsChangeListener(this));
}

private void registerListener(Listener listener) {
    getServer().getPluginManager().registerEvents(listener, this);
}
```

---

#### 4. OnDisable - zła kolejność shutdown
**Problem:** SaverTask może próbować zapisać dane podczas gdy zapisujesz graczy ręcznie

**Fix:**
```java
@Override
public void onDisable() {
    if (forceDisable) {
        getLogger().info("Shutdown przerwany (forceDisable=true).");
        return;
    }

    getLogger().info("Wyłączanie pluginu tfbDrop...");
    
    // 1. Zatrzymaj taski NAJPIERW
    if (this.saverTask != null) {
        this.saverTask.cancel();
    }
    if (this.actionBarTask != null) {
        this.actionBarTask.cancel();
    }
    
    // 2. Zapisz graczy
    if (this.userManager != null) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                this.userManager.save(player.getUniqueId(), true);
            } catch (Exception e) {
                getLogger().warning("Błąd zapisu gracza " + player.getName() + ": " + e.getMessage());
            }
        }
    }
    
    // 3. Zamknij bazę danych NA KOŃCU
    if (this.database != null) {
        try {
            this.database.disconnect(true);
        } catch (Exception e) {
            getLogger().warning("Błąd zamykania bazy: " + e.getMessage());
        }
    }
    
    getLogger().info("Plugin tfbDrop wyłączony pomyślnie.");
}
```

---

#### 5. ReloadConfigManager - potencjalny memory leak
**Problem:**
```java
public void reloadConfigManager() {
    this.configManager = new ConfigManager(this);
    
    if (this.actionBarTask != null) {
        this.actionBarTask.cancel();
        this.actionBarTask = null;
    }
    this.actionBarTask = new ActionBarTask(this);  // ← co jeśli rzuci exception?
}
```

**Fix:**
```java
public void reloadConfigManager() {
    this.configManager = new ConfigManager(this);
    reloadActionBar();
}

private void reloadActionBar() {
    if (this.actionBarTask != null) {
        this.actionBarTask.cancel();
    }
    this.actionBarTask = new ActionBarTask(this);
}
```

**Ocena:** 7.5/10 → 9/10 po poprawkach

---

## Database (obsługa bazy danych)

### 🔴 KRYTYCZNE problemy:

#### 1. RACE CONDITION - Największy problem!
**Problem:**
```java
public Database(DropPlugin plugin) {
    this.plugin = plugin;
    this.logger = plugin.getLogger();
    database = this;  // ← STATIC już ustawiony

    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
        // ...
        ds = new HikariDataSource(hikariConfig);  // ← ds inicjalizowany ASYNC!
    });
}
```

**Skutek:**
- Konstruktor kończy się NATYCHMIAST
- `ds` jest NULL przez kilka ticków
- SaverTask może wywołać `saveUser()` ZANIM ds się zainicjalizuje
- Możliwa utrata danych!

**Fix:**
```java
public Database(DropPlugin plugin) {
    this.plugin = plugin;
    this.logger = plugin.getLogger();
    
    // Synchronicznie inicjalizuj połączenie
    initializeConnection();
    
    // Async ładuj użytkowników
    loadUsersAsync();
}

private void initializeConnection() {
    ConfigManager config = plugin.getConfigManager();
    this.dbType = config.getDbType();
    
    logger.info("Łączenie z bazą danych (" + dbType.toUpperCase() + ")...");
    
    HikariConfig hikariConfig = new HikariConfig();
    // ... konfiguracja
    
    try {
        ds = new HikariDataSource(hikariConfig);
        logger.info("Połączono z bazą danych!");
        createTables();
    } catch (Exception e) {
        logger.severe("Nie można połączyć z bazą danych!");
        e.printStackTrace();
        throw new RuntimeException("Database initialization failed", e);
    }
}

private void loadUsersAsync() {
    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
        int loadedUsers = 0;
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM drop_users");
             ResultSet rs = ps.executeQuery()) {
            
            ps.setFetchSize(100);  // ← optymalizacja
            
            while (rs.next()) {
                try {
                    User user = new User(rs);
                    if (plugin.getUserManager().loadUser(user)) {
                        loadedUsers++;
                    }
                } catch (Exception e) {
                    logger.warning("Błąd ładowania użytkownika: " + e.getMessage());
                }
            }
            logger.info("Załadowano " + loadedUsers + " graczy z bazy!");
        } catch (SQLException e) {
            logger.warning("Nie można załadować graczy!");
            e.printStackTrace();
        }
    });
}
```

---

#### 2. Niekompatybilne typy kolumn SQLite vs MySQL
**Problem:**
```java
if ("sqlite".equalsIgnoreCase(dbType)) {
    "cobble INTEGER NOT NULL, "      // ← INTEGER w SQLite
} else {
    "cobble BOOLEAN NOT NULL, "      // ← BOOLEAN w MySQL
}
```

**Fix:** Użyj konsystentnych typów
```java
if ("sqlite".equalsIgnoreCase(dbType)) {
    createTableSQL = """
        CREATE TABLE IF NOT EXISTS drop_users (
            identifier TEXT PRIMARY KEY NOT NULL,
            cobble INTEGER NOT NULL,
            messages INTEGER NOT NULL,
            turboDrop INTEGER NOT NULL,
            turboExp INTEGER NOT NULL,
            lvl INTEGER NOT NULL,
            points INTEGER NOT NULL,
            minedDrops TEXT NOT NULL,
            disabledDrops TEXT NOT NULL,
            lastKnownName TEXT
        )""";
} else {
    createTableSQL = """
        CREATE TABLE IF NOT EXISTS `drop_users` (
            identifier VARCHAR(36) NOT NULL,
            cobble TINYINT(1) NOT NULL,
            messages TINYINT(1) NOT NULL,
            turboDrop BIGINT NOT NULL,
            turboExp BIGINT NOT NULL,
            lvl INT NOT NULL,
            points INT NOT NULL,
            minedDrops TEXT NOT NULL,
            disabledDrops TEXT NOT NULL,
            lastKnownName VARCHAR(16),
            PRIMARY KEY(identifier),
            INDEX idx_lvl_points (lvl DESC, points DESC)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""";
}
```

---

#### 3. Brak Connection Validation
**Problem:** Brak sprawdzania czy połączenie jest żywe

**Fix:**
```java
if ("sqlite".equalsIgnoreCase(dbType)) {
    hikariConfig.setConnectionTestQuery("SELECT 1");
} else {
    hikariConfig.setConnectionTestQuery("SELECT 1");
}
```

---

#### 4. getTopLevelPlayerData() - Bukkit.getOfflinePlayer() blokuje thread
**Problem:**
```java
org.bukkit.OfflinePlayer offlinePlayer = org.bukkit.Bukkit.getOfflinePlayer(uuid);
String name = offlinePlayer.getName();  // ← MOŻE BLOKOWAĆ
```

**Fix:** Dodaj kolumnę `lastKnownName` do tabeli i cache nazwy
```java
// W saveUser()
OfflinePlayer player = Bukkit.getOfflinePlayer(user.getIdentifier());
ps.setString(10, player.getName());

// W getTopLevelPlayerData()
String sql = "SELECT identifier, lvl, lastKnownName FROM drop_users ORDER BY lvl DESC, points DESC LIMIT 1 OFFSET ?";
// ...
String cachedName = rs.getString("lastKnownName");
String playerName = (cachedName != null && !cachedName.isEmpty()) ? cachedName : uuidStr;
```

---

#### 5. sendEmptyUpdate() jest bezużyteczna
**Problem:** HikariCP już robi keepalive automatycznie

**Fix:** **USUŃ TĘ METODĘ**

---

#### 6. Singleton pattern - Database.getInstance()
**Problem:**
```java
private static Database database;

public static Database getInstance() {
    return database;
}
```

**Fix:** **USUŃ** static instance, używaj tylko `plugin.getDatabase()`

---

#### 7. Brak indeksów na lvl i points
**Problem:** Wolne zapytania dla `getTopLevelPlayerData()`

**Fix:**
```java
// Po utworzeniu tabeli
st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_lvl_points ON drop_users(lvl DESC, points DESC)");
```

---

#### 8. Logger.fine() nie będzie działać
**Problem:** Bukkit Logger domyślnie nie pokazuje FINE level

**Fix:**
```java
// Zamiast logger.fine()
if (plugin.getConfig().getBoolean("debug", false)) {
    logger.info("DEBUG: ...");
}
```

**Ocena:** 6/10 → 9/10 po poprawkach

---

## SaverTask (auto-save)

### ⚠️ Do poprawy:

#### 1. sendEmptyUpdate() - USUŃ TO
**Problem:**
```java
if (Bukkit.getOnlinePlayers().isEmpty()) {
    this.database.sendEmptyUpdate();  // ← PO CO?
}
```

**Fix:**
```java
if (players.isEmpty()) {
    return;  // ← nic nie rób
}
```

---

#### 2. Try-catch w złym miejscu
**Problem:**
```java
try {
    for (Player player : Bukkit.getOnlinePlayers()) {
        this.userManager.save(player.getUniqueId());
    }
} catch (Exception e) {
    // ← Jeśli jeden gracz rzuci exception, CAŁA pętla się kończy
}
```

**Fix:** Try-catch WEWNĄTRZ pętli
```java
int saved = 0;
int failed = 0;

for (Player player : players) {
    try {
        userManager.save(player.getUniqueId());
        saved++;
    } catch (Exception e) {
        failed++;
        plugin.getLogger().warning("Błąd zapisu gracza " + player.getName() + ": " + e.getMessage());
    }
}

if (debug) {
    logger.info(String.format("Auto-save: zapisano %d/%d graczy", saved, saved + failed));
}
```

---

#### 3. 20L delay (1 sekunda) - za krótkie
**Problem:**
```java
this.runTaskTimer(plugin, 20L, 6000L);
//                        ↑ 1 sekunda po starcie
```

**Fix:**
```java
this.runTaskTimerAsynchronously(plugin, 200L, 6000L);
//                                      ↑ 10 sekund
```

---

#### 4. Niepotrzebne pole database
**Problem:** Używane tylko dla `sendEmptyUpdate()` która powinna zniknąć

**Fix:** Usuń pole `database`

---

#### 5. Brak metody shutdown()
**Fix:**
```java
public void shutdown() {
    if (!isCancelled()) {
        logger.info("Zatrzymywanie auto-save...");
        cancel();
    }
}
```

**Ocena:** 5/10 → 8.5/10 po poprawkach

---

## UserManager (zarządzanie użytkownikami)

### 🔴 KRYTYCZNE problemy:

#### 1. Database.getInstance() - SINGLETON ANTI-PATTERN
**Problem:**
```java
public void save(UUID uuid, boolean sync) {
    try {
        Database db = Database.getInstance();  // ← ŹLE!
        if (db != null) db.saveUser(user, sync);
    }
}
```

**Fix:**
```java
public class UserManager {
    private final DropPlugin plugin;
    private final Map<UUID, User> userMap = new ConcurrentHashMap<>();

    public UserManager(DropPlugin plugin) {
        this.plugin = plugin;
    }

    public void save(UUID uuid, boolean sync) {
        // ...
        try {
            plugin.getDatabase().saveUser(user, sync);  // ← DOBRZE!
        } catch (Exception e) {
            plugin.getLogger().warning("Błąd zapisu: " + e.getMessage());
        }
    }
}
```

**W DropPlugin.java:**
```java
this.userManager = new UserManager(this);  // ← przekaż plugin
```

---

#### 2. save() próbuje pobrać gracza z Bukkita - RACE CONDITION
**Problem:**
```java
public void save(UUID uuid, boolean sync) {
    User user = this.userMap.get(uuid);
    if (user == null) {
        if (Bukkit.isPrimaryThread()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) user = getUser(p);  // ← Tworzy nowego użytkownika?!
        }
    }
}
```

**Skutek:** Jeśli gracz się wylogował między `getOnlinePlayers()` a `save()`, stracisz dane!

**Fix:**
```java
public void save(UUID uuid, boolean sync) {
    if (uuid == null) return;
    
    User user = this.userMap.get(uuid);
    if (user == null) {
        // NIE PRÓBUJ TWORZYĆ - jeśli nie ma w cache, znaczy że się wylogował
        return;
    }

    try {
        plugin.getDatabase().saveUser(user, sync);
    } catch (Exception e) {
        plugin.getLogger().warning("Błąd zapisu: " + e.getMessage());
    }
}
```

---

#### 3. removeUserFromCache() PRZED save() = UTRATA DANYCH
**Problem:**
```java
public void removeUserFromCache(UUID uuid) {
    this.userMap.remove(uuid);  // ← Usuwa bez zapisu!
}
```

**W PlayerQuitListener (prawdopodobnie):**
```java
userManager.removeUserFromCache(uuid);  // ← Usuwa z cache
userManager.save(uuid);  // ← save() nie znajdzie użytkownika!
```

**Fix:**
```java
public void saveAndRemove(UUID uuid) {
    if (uuid == null) return;
    
    User user = this.userMap.remove(uuid);  // ← atomic remove + return
    if (user != null) {
        try {
            plugin.getDatabase().saveUser(user, true);  // ← sync save przy logout
        } catch (Exception e) {
            plugin.getLogger().warning("Błąd zapisu przy logout: " + uuid);
            e.printStackTrace();
        }
    }
}
```

**W PlayerQuitListener:**
```java
@EventHandler
public void onQuit(PlayerQuitEvent event) {
    UUID uuid = event.getPlayer().getUniqueId();
    userManager.saveAndRemove(uuid);  // ← JEDNA metoda!
}
```

---

#### 4. getUser(UUID) ma logikę tworzenia - NIESPÓJNE
**Problem:**
- `getUser(UUID)` może stworzyć nowego użytkownika
- `getUserIfLoaded(UUID)` tylko zwraca z cache
- Nazwy nie odzwierciedlają różnicy

**Fix:** Zmień nazwy
```java
// Tylko zwraca z cache, NIGDY nie tworzy
public User getUserIfLoaded(UUID uuid) {
    return uuid != null ? userMap.get(uuid) : null;
}

// Może stworzyć nowego użytkownika (tylko na main thread)
public User getOrCreateUser(UUID uuid) {
    if (uuid == null) return null;
    
    User cached = userMap.get(uuid);
    if (cached != null) return cached;
    
    if (!Bukkit.isPrimaryThread()) {
        plugin.getLogger().warning("Cannot create user from async thread: " + uuid);
        return null;
    }
    
    Player player = Bukkit.getPlayer(uuid);
    return player != null ? getOrCreateUser(player) : null;
}

// Dla gracza
public User getOrCreateUser(Player player) {
    if (player == null) return null;
    
    UUID uuid = player.getUniqueId();
    User cached = userMap.get(uuid);
    if (cached != null) return cached;
    
    if (!Bukkit.isPrimaryThread()) {
        plugin.getLogger().warning("Cannot create user from async thread");
        return null;
    }
    
    User newUser = new User(player);
    User existing = userMap.putIfAbsent(uuid, newUser);
    return existing != null ? existing : newUser;
}
```

---

#### 5. getUserMap() - NIEBEZPIECZNE
**Problem:**
```java
public Map<UUID, User> getUserMap() {
    return this.userMap;  // ← Zwraca referencję do wewnętrznej mapy!
}
```

**Fix:**
```java
// Opcja 1: Read-only view
public Map<UUID, User> getUserMap() {
    return Collections.unmodifiableMap(this.userMap);
}

// Opcja 2: Najlepiej - nie udostępniaj całej mapy
public Collection<User> getLoadedUsers() {
    return Collections.unmodifiableCollection(this.userMap.values());
}

public int getLoadedUserCount() {
    return this.userMap.size();
}
```

---

#### 6. Brak metody saveAll()
**Fix:**
```java
public void saveAll(boolean sync) {
    if (userMap.isEmpty()) {
        plugin.getLogger().fine("No users to save");
        return;
    }

    plugin.getLogger().info("Saving " + userMap.size() + " users...");
    
    int saved = 0;
    int failed = 0;
    
    for (User user : userMap.values()) {
        try {
            plugin.getDatabase().saveUser(user, sync);
            saved++;
        } catch (Exception e) {
            failed++;
            plugin.getLogger().warning("Failed to save user " + user.getIdentifier());
        }
    }
    
    plugin.getLogger().info("Save complete: " + saved + " saved, " + failed + " failed");
}
```

**W DropPlugin.onDisable():**
```java
if (this.userManager != null) {
    this.userManager.saveAll(true);  // ← zamiast pętli po graczach
}
```

**Ocena:** 6.5/10 → 9/10 po poprawkach

---

## ConfigManager.java

### 🔴 KRYTYCZNE problemy:

#### 1. Singleton pattern - Database.getInstance() v2
**Problem:**
```java
private static ConfigManager configManager;

public ConfigManager(DropPlugin plugin) {
    configManager = this;  // ← STATIC SINGLETON!
}

public static ConfigManager getConfigManager() {
    return configManager;
}
```

**Skutki:**
- User.java wywołuje `ConfigManager.getConfigManager()` ❌
- Memory leak przy /reload ❌
- To samo co z Database.getInstance() ❌

**Fix:** **USUŃ CAŁKOWICIE** static instance
```java
// W User.java zamiast:
ConfigManager.getConfigManager().getPointsToLvlUp()

// Użyj:
// OPCJA A - Przekaż wartości w metodach:
public int getPointsRequired(int pointsToLvlUp) {
    return this.lvl * pointsToLvlUp;
}

// OPCJA B - Cache w User (przy tworzeniu):
private final int pointsPerLevel;

public User(Player player, ConfigManager config) {
    this.pointsPerLevel = config.getPointsToLvlUp();
}
```

---

#### 2. turboDrop i turboExp nie powinny być w ConfigManager
**Problem:**
```java
private long turboDrop;  // ← nie final!
private long turboExp;   // ← nie final!

public void save() {
    this.plugin.getConfig().set("turbos.drop", this.turboDrop);
    this.plugin.saveConfig();
}
```

**Dlaczego to problem:**
- Config powinien być read-only po załadowaniu
- Wszystkie inne pola są `final`
- Turbos są **stanem serwera**, nie konfiguracją

**Fix:** Przenieś do osobnej klasy `TurboManager`
```java
package pl.tenfajnybartek.dropplugin.managers;

public class TurboManager {
    private final DropPlugin plugin;
    private long globalTurboDrop;
    private long globalTurboExp;

    public TurboManager(DropPlugin plugin) {
        this.plugin = plugin;
        this.globalTurboDrop = plugin.getConfig().getLong("turbos.drop", 0L);
        this.globalTurboExp = plugin.getConfig().getLong("turbos.exp", 0L);
    }

    public void setGlobalTurboDrop(long timestamp) {
        if (timestamp > System.currentTimeMillis()) {
            this.globalTurboDrop = timestamp;
        } else {
            this.globalTurboDrop = System.currentTimeMillis() + timestamp;
        }
        save();
    }

    public void setGlobalTurboExp(long timestamp) {
        if (timestamp > System.currentTimeMillis()) {
            this.globalTurboExp = timestamp;
        } else {
            this.globalTurboExp = System.currentTimeMillis() + timestamp;
        }
        save();
    }

    public long getGlobalTurboDrop() { return this.globalTurboDrop; }
    public long getGlobalTurboExp() { return this.globalTurboExp; }
    public boolean isGlobalTurboDrop() { return this.globalTurboDrop > System.currentTimeMillis(); }
    public boolean isGlobalTurboExp() { return this.globalTurboExp > System.currentTimeMillis(); }

    private void save() {
        plugin.getConfig().set("turbos.drop", this.globalTurboDrop);
        plugin.getConfig().set("turbos.exp", this.globalTurboExp);
        plugin.saveConfig();
    }
}
```

**W DropPlugin.java:**
```java
private TurboManager turboManager;

@Override
public void onEnable() {
    // ...
    this.turboManager = new TurboManager(this);
}

public TurboManager getTurboManager() {
    return this.turboManager;
}
```

---

### ⚠️ WAŻNE usprawnienia:

#### 3. Dodaj pole debug
**Fix:**
```java
private final boolean debug;

// W konstruktorze:
this.debug = getBooleanSafe(fc, "debug", false);

public boolean isDebug() {
    return this.debug;
}
```

**W config.yml:**
```yaml
debug: false  # wyłącz szczegółowe logi w produkcji
```

---

#### 4. Dodaj dropErrorMessage
**Fix:**
```java
private final String dropErrorMessage;

// W konstruktorze:
this.dropErrorMessage = getStringSafe(fc, "messages.drop-error", "&cWystąpił błąd podczas przetwarzania dropu!");

public String getDropErrorMessage() {
    return this.dropErrorMessage;
}
```

**W config.yml:**
```yaml
messages:
  drop-error: "&cWystąpił błąd podczas przetwarzania dropu. Skontaktuj się z administratorem."
```

**Ocena:** 7/10 → 9/10 po poprawkach

---

## PlayerJoinLeaveListener.java

### 🔴 KRYTYCZNE problemy:

#### 1. saveAndCleanup() - ZŁA KOLEJNOŚĆ = UTRATA DANYCH!
**Problem:**
```java
private void saveAndCleanup(Player player) {
    if (player == null) return;
    userManager.save(player.getUniqueId());  // ← ASYNC save
    userManager.removeUserFromCache(player.getUniqueId());  // ← NATYCHMIAST usuwa!
}
```

**Skutek:**
- `save()` jest **ASYNC** (Database.saveUser z sync=false)
- `removeUserFromCache()` usuwa **NATYCHMIAST**
- Save może nie zdążyć znaleźć użytkownika w cache!
- **UTRATA DANYCH!** 🔥

**Fix:** Użyj nowej metody atomic `saveAndRemove()`
```java
private void saveAndCleanup(Player player) {
    if (player == null) return;
    userManager.saveAndRemove(player.getUniqueId());  // ← JEDNA metoda atomic!
}
```

---

#### 2. onKick() - duplikacja z onQuit()
**Problem:**
```java
@EventHandler
public void onLeave(PlayerQuitEvent event) {
    saveAndCleanup(event.getPlayer());
}

@EventHandler
public void onKick(PlayerKickEvent event) {
    saveAndCleanup(event.getPlayer());
}
```

**Skutek:**
- PlayerKickEvent jest **PODKLASĄ** PlayerQuitEvent
- Oba eventy się wywołają
- `saveAndCleanup()` wywoła się **DWA RAZY**
- Pierwszy zapis OK, drugi fail (user już usunięty)

**Fix:** Obsługuj tylko PlayerQuitEvent
```java
@EventHandler
public void onQuit(PlayerQuitEvent event) {
    try {
        userManager.saveAndRemove(event.getPlayer().getUniqueId());
    } catch (Exception e) {
        logger.severe("Failed to save user on quit: " + event.getPlayer().getName());
        e.printStackTrace();
    }
}

// Usuń onKick() - PlayerQuitEvent się wywoła automatycznie przy kick
```

---

### ⚠️ WAŻNE usprawnienia:

#### 3. onJoin() - zmień nazwę metody
**Problem:**
```java
userManager.getUser(player);  // ← nazwa niejednoznaczna
```

**Fix:**
```java
@EventHandler
public void onJoin(PlayerJoinEvent event) {
    try {
        userManager.getOrCreateUser(event.getPlayer());
    } catch (Exception e) {
        logger.severe("Failed to load user on join: " + event.getPlayer().getName());
        e.printStackTrace();
    }
}
```

---

#### 4. Brak error handlingu
**Fix:** Dodaj try-catch (już pokazane wyżej)

**Ocena:** 3/10 → 9/10 po poprawkach

---

## BlockBreakListener.java

### ⚠️ WAŻNE usprawnienia:

#### 1. Hardcoded error message
**Problem:**
```java
event.getPlayer().sendMessage("§cWystąpił błąd podczas przetwarzania dropu...");
```

**Fix:** Przenieś do config.yml
```java
String errorMsg = plugin.getConfigManager().getDropErrorMessage();
event.getPlayer().sendMessage(ChatUtil.color(errorMsg));
```

---

#### 2. printStackTrace() zawsze włączony
**Problem:**
```java
catch (Exception e) {
    e.printStackTrace();  // ← w produkcji to spam w konsoli
}
```

**Fix:** Używaj loggera + debug mode
```java
catch (Exception e) {
    String errorMsg = plugin.getConfigManager().getDropErrorMessage();
    event.getPlayer().sendMessage(ChatUtil.color(errorMsg));
    
    plugin.getLogger().severe("Error processing drop for " + event.getPlayer().getName());
    plugin.getLogger().severe("Block: " + event.getBlock().getType());
    
    if (plugin.getConfigManager().isDebug()) {
        e.printStackTrace();
    }
}
```

**Ocena:** 8/10 → 9.5/10 po poprawkach

---

## DropManager.java

### 🔴 KRYTYCZNE problemy:

#### 1. user.addPoints() BEZ parametru levelingEnabled
**Problem:**
```java
user.addPoints(points);  // ← Brak parametru!
```

W User.java zmieniłem metodę na:
```java
public void addPoints(int points, boolean levelingEnabled)
```

**Fix:**
```java
user.addPoints(points, config.isLvlStatus());
```

---

#### 2. userManager.getUser() - stara nazwa
**Problem:**
```java
User user = this.userManager.getUser(event.getPlayer());
```

**Fix:**
```java
User user = this.userManager.getOrCreateUser(event.getPlayer());
if (user == null) {
    plugin.getLogger().warning("Nie można załadować użytkownika: " + player.getName());
    return;
}
```

---

#### 3. Durability recalculation WEWNĄTRZ pętli dropów
**Problem:**
```java
for (Drop drop : this.dropList) {
    // ...
    if (!RandomUtils.getChance(chance)) continue;

    if (tool != null) {
        ItemUtils.recalculateDurability(event.getPlayer(), tool);  // ← KAŻDY DROP!
    }
}
```

**Skutek:** Durability zmniejsza się **wielokrotnie** zamiast raz na blok!

**Fix:**
```java
boolean anyDropGiven = false;

for (Drop drop : this.dropList) {
    // ... logika dropu
    
    if (RandomUtils.getChance(chance)) {
        anyDropGiven = true;
        // ... daj drop
    }
}

// PO pętli - tylko raz:
if (anyDropGiven && tool != null && !tool.getType().isAir()) {
    ItemUtils.recalculateDurability(event.getPlayer(), tool);
}
```

---

### ⚠️ WAŻNE usprawnienia:

#### 4. Chance calculation - niespójność formatów
**Problem:**
```java
double chance = drop.getChance();  // ← 0.0-1.0 (znormalizowane)

Double c = permChance.getChance();  // ← procenty (np. 5.0)
if (c != null) {
    double bonus = c / 100.0;  // ← dzielisz przez 100
    chance += bonus;
}
```

**Co może pójść nie tak:**
- Drop.chance jest w 0.0-1.0
- Chance.chance w config może być w różnych formatach
- Jeśli ktoś poda 0.05 zamiast 5.0, dostanie 0.0005!

**Fix:** Standaryzuj format w config.yml
```yaml
settings:
  chances:
    vip:
      permission: "drop.vip"
      additionalchance: 5.0  # ZAWSZE w procentach (5.0 = +5%)
    mvp:
      permission: "drop.mvp"
      additionalchance: 10.0  # +10%
```

**Udokumentuj w komentarzu:**
```java
// Permission bonuses (config value in percentage: 5.0 = +5%)
for (Chance permChance : config.getChances().values()) {
    if (user.getPlayer() != null && user.getPlayer().hasPermission(permChance.getPerm())) {
        Double bonus = permChance.getChance();
        if (bonus != null && bonus > 0) {
            chance += (bonus / 100.0);  // Convert percentage to decimal
        }
    }
}
```

---

#### 5. Fortune bonus - niestandardowa implementacja
**Problem:**
```java
if (fortuneLevel == 1) {
    if (bonusChance < 0.33) bonus = 1;  // 33% szansy na +1
} else if (fortuneLevel == 2) {
    if (bonusChance < 0.50) {
        bonus = RandomUtils.getRandInt(1, 2);  // 50% szansy na 1-2
    }
} else if (fortuneLevel >= 3) {
    if (bonusChance < 0.60) {
        bonus = RandomUtils.getRandInt(1, 3);  // 60% szansy na 1-3
    }
}
```

**Twoja implementacja:**
- Fortune I: 33% +1, 67% +0 (średnio +0.33)
- Fortune II: 50% +1-2, 50% +0 (średnio +0.75)
- Fortune III: 60% +1-3, 40% +0 (średnio +1.2)

**Vanilla Fortune:**
- Fortune I: 33% x2, 33% x3, 33% x4 (średnio x3)
- Fortune II: 25% x2, 25% x3, 25% x4, 25% x5 (średnio x3.5)
- Fortune III: 20% x2-x6 (średnio x4)

**Decyzja:**
1. **Jeśli chcesz vanilla-like:**
```java
if (fortuneLevel > 0) {
    // Vanilla-like: multiplier 2-4 (Fortune I), 2-5 (II), 2-6 (III)
    int multiplier = RandomUtils.getRandInt(2, 2 + fortuneLevel);
    amount *= multiplier;
}
```

2. **Jeśli zostaw swoją wersję:** Udokumentuj to w config.yml jako custom fortune

---

#### 6. Fortune wpływa 2 RAZY - na szansę I na ilość
**Problem:**
```java
// Pierwsze użycie - zwiększa SZANSĘ
if (drop.isFortune() && tool != null) {
    int fortuneLevel = tool.getEnchantments().getOrDefault(Enchantment.FORTUNE, 0);
    if (fortuneLevel > 0) {
        chance = Math.min(chance + (fortuneLevel * 0.10), 1.0);  // +10% per level
    }
}

if (!RandomUtils.getChance(chance)) continue;

// Drugie użycie - zwiększa ILOŚĆ
if (drop.isFortune() && tool != null) {
    // ... bonus amount
}
```

**Pytanie:** Czy to zamierzone?
- **Vanilla Minecraft:** Fortune wpływa **tylko** na ilość, nie na szansę
- **Twoja wersja:** Fortune wpływa na **OBA**

**Decyzja:**
1. **Jeśli chcesz vanilla-like:** Usuń pierwszy blok (chance boost)
2. **Jeśli to zamierzone:** OK, ale udokumentuj w config.yml

---

#### 7. Obsidian i Stone - duplikacja kodu exp
**Problem:**
```java
if (originalMaterial == Material.OBSIDIAN) {
    int give = this.obsidianExp;
    if (this.config.isTurboExp() || user.isTurboExp()) give *= 2;
    event.getPlayer().giveExp(give);
    return;
}

// ... potem dla stone:
int give = this.stoneExp;
if (this.config.isTurboExp() || user.isTurboExp()) give *= 2;
event.getPlayer().giveExp(give);
```

**Fix:**
```java
if (originalMaterial == Material.OBSIDIAN) {
    giveExpWithTurbo(event.getPlayer(), user, this.obsidianExp);
    return;
}

if (isStoneLike) {
    giveExpWithTurbo(event.getPlayer(), user, this.stoneExp);
    // ... reszta logiki
}

private void giveExpWithTurbo(Player player, User user, int baseExp) {
    int exp = baseExp;
    if (config.isTurboExp() || user.isTurboExp()) {
        exp *= 2;
    }
    player.giveExp(exp);
}
```

---

#### 8. isStoneLike - hardcoded lista
**Problem:**
```java
boolean isStoneLike = originalMaterial == Material.STONE ||
        originalMaterial == Material.GRANITE ||
        originalMaterial == Material.DIORITE ||
        originalMaterial == Material.ANDESITE ||
        originalMaterial == Material.DEEPSLATE;
```

**Fix:** Przenieś do config.yml
```yaml
settings:
  stone-like-blocks:
    - STONE
    - GRANITE
    - DIORITE
    - ANDESITE
    - DEEPSLATE
    - TUFF
    - CALCITE
```

**W ConfigManager.java:**
```java
private final Set<Material> stoneLikeBlocks;

// W konstruktorze:
this.stoneLikeBlocks = fc.getStringList("settings.stone-like-blocks")
    .stream()
    .map(Material::matchMaterial)
    .filter(Objects::nonNull)
    .collect(Collectors.toSet());

public boolean isStoneLike(Material material) {
    return stoneLikeBlocks.contains(material);
}
```

**W DropManager.java:**
```java
if (!config.isStoneLike(material)) {
    return;
}
```

---

#### 9. playerY może być 0.0 - niepotrzebny check
**Problem:**
```java
double playerY = 0;
if (event.getPlayer().getLocation() != null) {
    playerY = event.getPlayer().getLocation().getY();
}
```

`getLocation()` **NIGDY** nie zwraca null dla online gracza.

**Fix:**
```java
double playerY = event.getPlayer().getLocation().getY();
```

---

#### 10. Refactoring - rozbij metodę breakBlock()
**Problem:** Metoda `breakBlock()` ma ~150 linii kodu

**Fix:** Rozbij na mniejsze metody:
```java
public void breakBlock(BlockBreakEvent event) {
    // Validation
    // Obsidian handling
    // Stone handling
    processDrops(event, user, player, tool);
}

private void processDrops(BlockBreakEvent event, User user, Player player, ItemStack tool) {
    // Pętla po dropach
}

private double calculateChance(Drop drop, User user, ItemStack tool) {
    // Logika szansy
}

private int calculateAmount(Drop drop, ItemStack tool) {
    // Logika ilości
}
```

**Ocena:** 6.5/10 → 8.5/10 po poprawkach

---

## User.java

### 🔴 KRYTYCZNE problemy:

#### 1. ConfigManager.getConfigManager() - singleton
**Problem:**
```java
public int getPointsRequired() {
    int pointsToLvl = ConfigManager.getConfigManager().getPointsToLvlUp();
}

public void addPoints(int points) {
    if (ConfigManager.getConfigManager().isLvlStatus()) {
        // ...
    }
}
```

**Fix - OPCJA A (polecana):** Przekaż wartości w metodach
```java
public int getPointsRequired(int pointsToLvlUp) {
    if (pointsToLvlUp <= 0) {
        return 100;
    }
    return this.lvl * pointsToLvlUp;
}

public void addPoints(int points, boolean levelingEnabled) {
    if (!levelingEnabled || points <= 0) {
        return;
    }
    
    int oldPoints = this.points;
    this.points += points;
    
    Bukkit.getPluginManager().callEvent(new UserPointsChangeEvent(this));
}
```

**Fix - OPCJA B:** Cache w User
```java
private final int pointsPerLevel;
private final boolean levelingEnabled;

public User(Player player, ConfigManager config) {
    this.identifier = player.getUniqueId();
    this.pointsPerLevel = config.getPointsToLvlUp();
    this.levelingEnabled = config.isLvlStatus();
    // ...
}
```

---

### ⚠️ WAŻNE usprawnienia:

#### 2. ResultSet constructor - getBoolean() vs getInt()
**Problem:**
```java
this.cobble = resultSet.getBoolean("cobble");
this.messages = resultSet.getBoolean("messages");
```

W Database dla SQLite używasz `INTEGER` (0/1).

**Fix:**
```java
// Obsługa dla obu baz
int cobbleInt = resultSet.getInt("cobble");
int messagesInt = resultSet.getInt("messages");
this.cobble = cobbleInt != 0;
this.messages = messagesInt != 0;
```

---

#### 3. addTurboExp/Drop - złe nazwy
**Problem:**
```java
public void addTurboExp(long turboExp) {
    this.turboExp = turboExp;  // ← NIE DODAJE, tylko USTAWIA!
}
```

**Fix:**
```java
public void setTurboExp(long timestamp) {
    this.turboExp = timestamp;
}

public void setTurboDrop(long timestamp) {
    this.turboDrop = timestamp;
}

// Jeśli chcesz prawdziwe "add":
public void addTurboExpDuration(long milliseconds) {
    long current = this.turboExp > System.currentTimeMillis() 
        ? this.turboExp 
        : System.currentTimeMillis();
    this.turboExp = current + milliseconds;
}
```

---

#### 4. setLvl() i setPoints() - brak walidacji
**Problem:**
```java
public void setLvl(int lvl) {
    this.lvl = lvl;  // ← może być -1!
}
```

**Fix:**
```java
public void setLvl(int lvl) {
    if (lvl < 1) {
        throw new IllegalArgumentException("Level must be >= 1, got: " + lvl);
    }
    
    int oldLvl = this.lvl;
    this.lvl = lvl;
    
    if (oldLvl != lvl) {
        Bukkit.getPluginManager().callEvent(new UserLevelChangeEvent(this));
    }
}

public void setPoints(int points) {
    if (points < 0) {
        points = 0;
    }
    
    int oldPoints = this.points;
    this.points = points;
    
    if (oldPoints != points) {
        Bukkit.getPluginManager().callEvent(new UserPointsChangeEvent(this));
    }
}
```

---

#### 5. minedDrops - niespójność (final + lazy init)
**Problem:**
```java
private HashMap<String, Integer> minedDrops;

public User(Player player) {
    this.minedDrops = new HashMap<>();  // ← inicjalizowany
}

public HashMap<String, Integer> getMinedDrops() {
    if (this.minedDrops == null) this.minedDrops = new HashMap<>();  // ← lazy init
    return this.minedDrops;
}
```

**Fix - OPCJA A (polecana):**
```java
private final Map<String, Integer> minedDrops = new HashMap<>();

public Map<String, Integer> getMinedDrops() {
    return Collections.unmodifiableMap(this.minedDrops);
}
```

---

#### 6. getMinedDrops() i getDisabledDrops() - zwracają mutable collections
**Problem:**
```java
public HashMap<String, Integer> getMinedDrops() {
    return this.minedDrops;  // ← można zmodyfikować z zewnątrz
}
```

**Fix:**
```java
public Map<String, Integer> getMinedDrops() {
    return Collections.unmodifiableMap(this.minedDrops);
}

public Set<String> getDisabledDrops() {
    return Collections.unmodifiableSet(this.disabledDrops);
}
```

---

#### 7. Brak equals() i hashCode()
**Fix:**
```java
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    User user = (User) o;
    return identifier.equals(user.identifier);
}

@Override
public int hashCode() {
    return identifier.hashCode();
}
```

**Ocena:** 7/10 → 9/10 po poprawkach

---

## Drop.java (klasa pomocnicza)

**Ocena:** 9/10 ✅

**Jedyna uwaga:**
```java
if (exp < 0) {
    java.util.logging.Logger.getLogger("DropPlugin").warning("...");
    this.exp = 0;
}
```

**Lepiej:**
```java
this.exp = Math.max(0, exp);
if (exp < 0) {
    // Użyj loggera z pluginu jeśli możliwe
}
```

---

## Count.java (klasa pomocnicza)

**Ocena:** 10/10 ✅✅✅

**PERFEKCJA!** Nie zmieniaj nic.

---

## Chance.java (klasa pomocnicza)

**Ocena:** 7/10

### ⚠️ Do poprawy:

#### 1. Dwa konstruktory - duplikacja
**Fix:**
```java
public Chance(String name, String perm, Double chance) {
    if (name == null || name.isEmpty()) {
        throw new IllegalArgumentException("name cannot be null/empty");
    }
    if (perm == null || perm.isEmpty()) {
        throw new IllegalArgumentException("perm cannot be null/empty");
    }
    if (chance == null || chance < 0) {
        throw new IllegalArgumentException("chance must be >= 0");
    }
    
    this.name = name;
    this.perm = perm;
    this.chance = chance;
}

// Usuń drugi konstruktor lub użyj:
// new Chance(perm, perm, chance);
```

---

#### 2. Pola nie są final
**Fix:**
```java
private final String name;
private final String perm;
private final Double chance;
```

---

#### 3. Brak equals() i hashCode()
**Fix:**
```java
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Chance)) return false;
    Chance chance1 = (Chance) o;
    return name.equals(chance1.name) && perm.equals(chance1.perm);
}

@Override
public int hashCode() {
    return Objects.hash(name, perm);
}
```

---

## InventoryClickListener.java

### ⚠️ WAŻNE usprawnienia:

#### 1. getPluginConfig() zamiast getConfigManager()
**Problem:**
```java
this.config = plugin.getPluginConfig();
```

**Fix:**
```java
this.config = plugin.getConfigManager();
```

---

#### 2. getUser() - stara nazwa
**Problem:**
```java
User user = plugin.getUserManager().getUser(player);
```

**Fix:**
```java
User user = plugin.getUserManager().getOrCreateUser(player);
if (user == null) {
    player.closeInventory();
    player.sendMessage("§cNie można załadować Twoich danych!");
    return;
}
```

---

#### 3. Brak save po zmianie ustawień
**Problem:**
```java
private void handleIndividualDrop(Player player, User user, Drop drop) {
    if (user.isDisabled(drop)) {
        user.removeDisabledDrop(drop);
    } else {
        user.addDisabledDrop(drop);
    }
    player.openInventory(plugin.getDropMenu().createInventory(player));
    // ← Brak save! Co jeśli serwer crashuje?
}
```

**Fix:**
```java
private void handleIndividualDrop(Player player, User user, Drop drop) {
    if (user.isDisabled(drop)) {
        user.removeDisabledDrop(drop);
    } else {
        user.addDisabledDrop(drop);
    }
    
    // Save changes asynchronously
    plugin.getUserManager().save(user.getIdentifier(), false);
    
    // Refresh GUI
    player.openInventory(plugin.getDropMenu().createInventory(player));
}
```

**Zastosuj to samo dla:**
- `handleEnableAllDrops()`
- `handleDisableAllDrops()`
- `toggleMessages()`
- `toggleCobble()`

---

#### 4. Duplikacja w enable/disable all
**Problem:**
```java
private void handleEnableAllDrops(Player player, User user) {
    for (Drop drop : plugin.getDropManager().getDropList()) {
        if (user.isDisabled(drop)) {
            user.removeDisabledDrop(drop);
        }
    }
    player.openInventory(plugin.getDropMenu().createInventory(player));
}

private void handleDisableAllDrops(Player player, User user) {
    for (Drop drop : plugin.getDropManager().getDropList()) {
        if (!user.isDisabled(drop)) {
            user.addDisabledDrop(drop);
        }
    }
    player.openInventory(plugin.getDropMenu().createInventory(player));
}
```

**Fix - DRY:**
```java
private void handleToggleAllDrops(Player player, User user, boolean enable) {
    for (Drop drop : plugin.getDropManager().getDropList()) {
        if (enable && user.isDisabled(drop)) {
            user.removeDisabledDrop(drop);
        } else if (!enable && !user.isDisabled(drop)) {
            user.addDisabledDrop(drop);
        }
    }
    
    plugin.getUserManager().save(user.getIdentifier(), false);
    player.openInventory(plugin.getDropMenu().createInventory(player));
}
```

---

#### 5. Cache LegacyComponentSerializer
**Fix:**
```java
private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

// W onClick:
Component expectedTitle = SERIALIZER.deserialize(config.getGuiName());
```

**Ocena:** 7.5/10 → 9/10 po poprawkach

---

## DropConfigManager.java

### 🔴 KRYTYCZNE problemy:

#### 1. Dziwna logika inicjalizacji
**Problem:**
```java
FileConfiguration cfg = new YamlConfiguration();
try {
    cfg = YamlConfiguration.loadConfiguration(file);  // ← Ładuje tutaj
} catch (Exception e) {
    try {
        ((YamlConfiguration) cfg).load(file);  // ← I tutaj?
    } catch (IOException | InvalidConfigurationException ex) {
        ex.printStackTrace();
    }
}
```

**Problem:**
- `loadConfiguration()` NIGDY nie rzuca exception (static method)
- Outer try-catch NIGDY się nie wywoła
- Inner try-catch jest zbędny

**Fix:**
```java
public DropConfigManager(DropPlugin plugin) {
    this.plugin = plugin;
    this.file = new File(plugin.getDataFolder(), "drops.yml");
    
    // Create parent directory if needed
    if (!file.getParentFile().exists()) {
        file.getParentFile().mkdirs();
    }
    
    // Create default file if needed
    if (!file.exists()) {
        plugin.saveResource("drops.yml", false);
        plugin.getLogger().info("Utworzono domyślny plik drops.yml");
    }

    // Load configuration
    this.dropConfig = YamlConfiguration.loadConfiguration(file);
    plugin.getLogger().info("Załadowano konfigurację drops.yml");
}
```

---

#### 2. printStackTrace() zamiast loggera
**Problem:**
```java
public void reload() {
    try {
        ((YamlConfiguration) this.dropConfig).load(this.file);
    } catch (IOException | InvalidConfigurationException e) {
        e.printStackTrace();  // ← BAD
    }
}
```

**Fix:**
```java
private final DropPlugin plugin;

public void reload() {
    try {
        ((YamlConfiguration) this.dropConfig).load(this.file);
        plugin.getLogger().info("Przeładowano drops.yml");
    } catch (IOException e) {
        plugin.getLogger().severe("Nie można odczytać pliku drops.yml: " + e.getMessage());
        if (plugin.getConfigManager().isDebug()) {
            e.printStackTrace();
        }
    } catch (InvalidConfigurationException e) {
        plugin.getLogger().severe("Błąd składni w drops.yml: " + e.getMessage());
        if (plugin.getConfigManager().isDebug()) {
            e.printStackTrace();
        }
    }
}
```

**Ocena:** 6/10 → 8.5/10 po poprawkach

---

## ItemUtils.java

### 🔴 KRYTYCZNE problemy:

#### 1. ConfigManager.getConfigManager() - SINGLETON ZNOWU!
**Problem:**
```java
public static void giveItem(Player player, ItemStack itemStack) {
    if (itemStack == null || player == null) return;

    if (!ItemUtils.giveItem(player.getInventory(), itemStack)) {
        ConfigManager cfg = ConfigManager.getConfigManager();  // ← SINGLETON!
        if (cfg != null && cfg.isToInv()) {
            if (cfg.isMessageInv()) {
                ChatUtils.sendMessage(player, cfg.getMessageInvFull());
            }
            return;
        }
        player.getWorld().dropItemNaturally(player.getLocation(), itemStack);
    }
}
```

**Fix:**
```java
public static void giveItem(Player player, ItemStack itemStack, ConfigManager config) {
    if (itemStack == null || player == null) return;

    if (!giveItem(player.getInventory(), itemStack)) {
        if (config != null && config.isToInv()) {
            if (config.isMessageInv()) {
                ChatUtils.sendMessage(player, config.getMessageInvFull());
            }
            return;
        }
        player.getWorld().dropItemNaturally(player.getLocation(), itemStack);
    }
}
```

**W DropManager.java zaktualizuj wywołania:**
```java
// Zamiast:
ItemUtils.giveItem(player, toGive);

// Użyj:
ItemUtils.giveItem(player, toGive, config);
```

**Ocena:** 5/10 → 9/10 po poprawkach

---

## DropMenu.java

### ⚠️ WAŻNE usprawnienia:

#### 1. getPluginConfig() zamiast getConfigManager()
**Problem:**
```java
this.config = plugin.getPluginConfig();
```

**Fix:**
```java
this.config = plugin.getConfigManager();
```

---

#### 2. getUser() zamiast getOrCreateUser()
**Problem:**
```java
User user = this.userManager.getUser(player);
```

**Fix:**
```java
User user = this.userManager.getOrCreateUser(player);
```

---

#### 3. user.getPointsToNextLevel() bez parametru
**Problem:**
```java
entry = StringUtils.replace(entry, "{POINTS-TO-LEVEL}", 
    String.valueOf(user != null ? user.getPointsToNextLevel() : 0));
```

W User.java zmieniłem metodę na:
```java
public int getPointsToNextLevel(int pointsPerLevel)
```

**Fix:**
```java
int pointsPerLevel = config.getPointsToLvlUp();
int pointsToNext = user != null ? user.getPointsToNextLevel(pointsPerLevel) : 0;
entry = StringUtils.replace(entry, "{POINTS-TO-LEVEL}", String.valueOf(pointsToNext));
```

---

#### 4. Magic numbers w replaced() - type 1, 2, 3, 4
**Problem:**
```java
public String replaced(User user, String entry, int type) {
    switch (type) {
        case 1: { /* cobble */ }
        case 2: { /* messages */ }
        case 3: { /* turbo */ }
        case 4: { /* level */ }
    }
}

// W createInventory():
.map(entry -> this.replaced(user, entry, 1))  // ← co to jest 1?
.map(entry -> this.replaced(user, entry, 2))  // ← co to jest 2?
```

**Fix - Stwórz enum:**
```java
public enum MenuItemType {
    COBBLE(1),
    MESSAGES(2),
    TURBO(3),
    LEVEL(4);
    
    private final int legacyId;
    
    MenuItemType(int legacyId) {
        this.legacyId = legacyId;
    }
}

public String replaced(User user, String entry, MenuItemType type) {
    int pointsPerLevel = config.getPointsToLvlUp();
    
    switch (type) {
        case COBBLE:
            entry = StringUtils.replace(entry, "{STATUS}", 
                (user != null && user.isCobble()) ? config.getGuiStatusOn() : config.getGuiStatusOff());
            return entry;
        // ... reszta
    }
}

// W createInventory():
.map(entry -> this.replaced(user, entry, MenuItemType.COBBLE))
.map(entry -> this.replaced(user, entry, MenuItemType.MESSAGES))
```

**Ocena:** 6/10 → 8/10 po poprawkach

---

## MapUtils.java

### ℹ️ NICE TO HAVE:

#### 1. printStackTrace() w deserializeMap()
**Problem:**
```java
try {
    map.put(mapSplit[0], Integer.parseInt(mapSplit[1]));
} catch (NumberFormatException e) {
    e.printStackTrace();  // ← BAD
}
```

**Fix:**
```java
try {
    map.put(mapSplit[0], Integer.parseInt(mapSplit[1]));
} catch (NumberFormatException e) {
    // Ignoruj nieprawidłowe wartości (silent fail)
}
```

---

#### 2. Separator @ może wystąpić w danych
**Problem:** Co jeśli klucz zawiera `@` lub `=`?

**Rozwiązanie:**
- Dla prostych przypadków obecna wersja działa ✅
- Dla złożonych danych rozważ JSON

**Ocena:** 7/10 → 8/10 po poprawkach

---

## ActionBarTask.java

### ⚠️ WAŻNE usprawnienia:

#### 1. getPluginConfig() zamiast getConfigManager()
**Problem:**
```java
this.config = plugin.getPluginConfig();
```

**Fix:**
```java
this.config = plugin.getConfigManager();
```

---

#### 2. Niepotrzebna metoda cancelTask()
**Problem:**
```java
public void cancelTask() {
    try {
        this.cancel();
    } catch (IllegalStateException ignored) {
    }
}
```

W `DropPlugin.onDisable()` wywołujesz `cancel()`, nie `cancelTask()`.

**Fix:** Usuń tę metodę lub dodaj `shutdown()` jak w SaverTask:
```java
public void shutdown() {
    if (!isCancelled()) {
        logger.info("Zatrzymywanie ActionBar task...");
        cancel();
    }
}
```

---

#### 3. Auto-cancel gdy actionbar wyłączony
**Problem:** Co jeśli config się przeładuje i `isActionbarStatus()` = false?

**Fix:**
```java
@Override
public void run() {
    if (!this.config.isActionbarStatus()) {
        cancel();  // ← auto-cancel jeśli wyłączono
        return;
    }
    
    boolean turboDrop = this.config.isTurboDrop();
    // ... reszta
}
```

**Ocena:** 7/10 → 8.5/10 po poprawkach

---

## UserPointsChangeListener.java

### ⚠️ WAŻNE usprawnienia:

#### 1. getPluginConfig() zamiast getConfigManager()
**Problem:**
```java
this.config = plugin.getPluginConfig();
```

**Fix:**
```java
this.config = plugin.getConfigManager();
```

---

#### 2. u.getPointsRequired() bez parametru
**Problem:**
```java
if (levelPoints >= u.getPointsRequired()) {
```

W User.java zmieniłem na `getPointsRequired(int pointsToLvlUp)`.

**Fix:**
```java
int pointsRequired = u.getPointsRequired(config.getPointsToLvlUp());
if (u.getPoints() >= pointsRequired) {
    int newLevel = u.getLvl() + 1;
    u.setLvl(newLevel);
    u.setPoints(0);
    
    // Save immediately after level up (important!)
    plugin.getUserManager().save(u.getIdentifier(), false);
}
```

---

#### 3. Używaj u.getPoints() zamiast event.getLevelPoints()
**Problem:** `event.getLevelPoints()` to snapshot, może być nieaktualny.

**Fix:** Używaj `u.getPoints()` (już pokazane w poprzednim fixie)

**Ocena:** 8/10 → 9/10 po poprawkach

---

## UserLevelChangeListener.java

### ⚠️ WAŻNE usprawnienia:

#### 1. getPluginConfig() zamiast getConfigManager()
**Problem:**
```java
this.config = plugin.getPluginConfig();
```

**Fix:**
```java
this.config = plugin.getConfigManager();
```

---

#### 2. Hardcoded obliczenie punktów
**Problem:**
```java
String personal = this.config.getLvlUpMessage()
        .replace("{LVL}", String.valueOf(lvl))
        .replace("{POINTS}", String.valueOf(lvl * this.config.getPointsToLvlUp()));
```

**Fix:**
```java
int pointsForNextLevel = event.getUser().getPointsRequired(config.getPointsToLvlUp());
String personal = this.config.getLvlUpMessage()
        .replace("{LVL}", String.valueOf(lvl))
        .replace("{POINTS}", String.valueOf(pointsForNextLevel));
```

**Ocena:** 7.5/10 → 8.5/10 po poprawkach

---

## UserPointsChangeEvent.java

### ✅ ŚWIETNA KLASA - 9/10

**Plusy:**
- Poprawna implementacja Event API ✅
- HANDLER_LIST static final ✅
- Wszystkie potrzebne gettery ✅
- Immutable dane (snapshot) ✅

**Nie wymaga zmian!**

---

## UserLevelChangeEvent.java

### ✅ ŚWIETNA KLASA - 9/10

**Plusy:**
- Identyczna struktura jak UserPointsChangeEvent ✅
- Poprawna implementacja ✅

**Nie wymaga zmian!**

---

## DropPluginExpansion.java (PlaceholderAPI)

### 🔴 KRYTYCZNE problemy:

#### 1. ConfigManager.getConfigManager() - SINGLETON!
**Problem:**
```java
private String formatTopLevelPlayer(String[] playerData) {
    if (playerData == null || playerData.length < 2) {
        return ConfigManager.getConfigManager().getToplevelNoPlayer();
    }
    String format = ConfigManager.getConfigManager().getToplevelFormat();
    // ...
}

// W onRequest():
Database db = Database.getInstance();  // ← SINGLETON!
```

**Fix:**
```java
private final DropPlugin plugin;
private final ConfigManager config;

public DropPluginExpansion(DropPlugin plugin) {
    this.plugin = plugin;
    this.config = plugin.getConfigManager();
}

private String formatTopLevelPlayer(String[] playerData) {
    if (playerData == null || playerData.length < 2) {
        return config.getToplevelNoPlayer();
    }
    String format = config.getToplevelFormat();
    return format.replace("{NAME}", playerData[0]).replace("{LEVEL}", playerData[1]);
}

// W onRequest():
Database db = plugin.getDatabase();  // ← używaj plugin
```

---

#### 2. getPointsRequired() i getPointsToNextLevel() bez parametru
**Problem:**
```java
return String.valueOf(user.getPointsRequired());
return String.valueOf(user.getPointsToNextLevel());
```

**Fix:**
```java
if (params.equalsIgnoreCase("points_required")) {
    User user = plugin.getUserManager().getUserIfLoaded(onlinePlayer.getUniqueId());
    if (user == null) return "0";
    
    int pointsPerLevel = config.getPointsToLvlUp();
    return String.valueOf(user.getPointsRequired(pointsPerLevel));
}

if (params.equalsIgnoreCase("points_to_next")) {
    User user = plugin.getUserManager().getUserIfLoaded(onlinePlayer.getUniqueId());
    if (user == null) return "0";
    
    int pointsPerLevel = config.getPointsToLvlUp();
    return String.valueOf(user.getPointsToNextLevel(pointsPerLevel));
}
```

---

#### 3. Cache - ŚWIETNY POMYSŁ! ✅
**Ocena:** ⭐⭐⭐⭐⭐
```java
private final Map<Integer, CacheEntry> topLevelCache = new ConcurrentHashMap<>();
private static final long CACHE_DURATION_MS = 30000; // 30 seconds
```

**To jest DOSKONAŁE!** Unikasz częstych zapytań do bazy.

---

#### 4. getUserIfLoaded() - ŚWIETNIE! ✅
```java
User user = plugin.getUserManager().getUserIfLoaded(onlinePlayer.getUniqueId());
```

**Używasz `getUserIfLoaded()` zamiast `getOrCreateUser()`** - poprawne dla placeholderów!

**Ocena:** 7/10 → 9/10 po poprawkach

---

## DropCommand.java

### ⚠️ WAŻNE usprawnienia:

#### 1. getPluginConfig() zamiast getConfigManager()
**Problem:**
```java
ChatUtils.sendMessage(sender, this.plugin.getPluginConfig().getCmdDropNoPermission());
```

**Fix:**
```java
ChatUtils.sendMessage(sender, this.plugin.getConfigManager().getCmdDropNoPermission());
```

---

#### 2. Konstruktor - dobrze zabezpieczony ✅
```java
if (plugin.getCommand("drop") != null) {
    plugin.getCommand("drop").setExecutor(this);
} else {
    plugin.getLogger().warning("Command 'drop' is not defined in plugin.yml");
}
```

**To jest ŚWIETNE!**

**Ocena:** 8/10 → 9/10 po poprawkach

---

## LevelCommand.java

### ⚠️ WAŻNE usprawnienia:

#### 1. getPluginConfig() zamiast getConfigManager()
**Problem:**
```java
var config = this.plugin.getPluginConfig();
```

**Fix:**
```java
var config = this.plugin.getConfigManager();
```

---

#### 2. getUser() zamiast getOrCreateUser()
**Problem:**
```java
User u = this.userManager.getUser(player);
```

**Fix:**
```java
User u = this.userManager.getOrCreateUser(player);
```

---

#### 3. getPointsToNextLevel() bez parametru
**Problem:**
```java
.replace("{POINTS_TO_NEXT}", String.valueOf(user.getPointsToNextLevel()));
```

**Fix:**
```java
int pointsPerLevel = config.getPointsToLvlUp();
int pointsToNext = user.getPointsToNextLevel(pointsPerLevel);
ChatUtils.sendMessage(sender, config.getCmdLevelPointsToNext()
        .replace("{POINTS_TO_NEXT}", String.valueOf(pointsToNext)));
```

**Ocena:** 7.5/10 → 9/10 po poprawkach

---

## ADropCommand.java

### 🔴 KRYTYCZNE problemy:

#### 1. getPluginConfig() zamiast getConfigManager()
**Problem:**
```java
var config = this.plugin.getPluginConfig();
```

**Fix:**
```java
var config = this.plugin.getConfigManager();
```

---

#### 2. getUser() zamiast getOrCreateUser()
**Problem:**
```java
User user = this.userManager.getUser(targetPlayer);
```

**Fix:**
```java
User user = this.userManager.getOrCreateUser(targetPlayer);
```

---

#### 3. addTurboDrop/Exp - złe nazwy metod
**Problem:**
```java
if (isDrop) {
    user.addTurboDrop(time);  // ← to jest SET, nie ADD
} else {
    user.addTurboExp(time);
}
```

W User.java zmieniłem nazwy na `setTurboDrop()`.

**Fix:**
```java
if (isDrop) {
    user.setTurboDrop(time);
} else {
    user.setTurboExp(time);
}
```

---

#### 4. config.addTurboDrop/Exp - używa ConfigManager zamiast TurboManager
**Problem:**
```java
if (isDrop) {
    config.addTurboDrop(time);
    config.save();
}
```

ConfigManager nie powinien mieć turbo - to powinno być w TurboManager!

**Fix (po stworzeniu TurboManager):**
```java
TurboManager turboManager = plugin.getTurboManager();
if (isDrop) {
    turboManager.setGlobalTurboDrop(time);
} else {
    turboManager.setGlobalTurboExp(time);
}
```

---

#### 5. Brak save po zmianie levelu
**Problem:**
```java
targetUser.setLvl(newLevel);
targetUser.setPoints(newPoints);
// ← Brak save!
```

**Fix:**
```java
targetUser.setLvl(newLevel);
targetUser.setPoints(newPoints);

// Save immediately
plugin.getUserManager().save(targetUser.getIdentifier(), false);
```

---

#### 6. Constants - ŚWIETNIE! ✅
```java
private static final int MIN_LEVEL = 1;
private static final int MAX_LEVEL = 1000;
private static final int MIN_POINTS = 0;
private static final int MAX_POINTS = 1000000;
```

**To jest DOSKONAŁE!**

**Ocena:** 6.5/10 → 8.5/10 po poprawkach

---

## plugin.yml

### ✅ PERFEKCJA - 10/10

**Plusy:**
- API version 1.21 ✅
- Soft-depend PlaceholderAPI ✅
- Commands z aliasami ✅
- Proper descriptions ✅

**Nie zmieniaj NIC!**

---

## config.yml

### ✅ DOSKONAŁA KONFIGURACJA - 10/10

**Plusy:**
- Szczegółowe komentarze PL ✅
- Wyjaśnienie każdej opcji ✅
- Przykłady użycia ✅
- Sensowne wartości domyślne ✅
- Wsparcie dla hex kolorów ✅
- Opcje dla SQLite i MySQL ✅

**Świetnie napisana dokumentacja!**

---

## drops.yml

### ✅ DOSKONAŁA KONFIGURACJA - 10/10

**Plusy:**
- Szczegółowe komentarze ✅
- Przykłady dropów ✅
- Wyjaśnienie Fortune ✅
- Wsparcie dla neededlvl ✅
- Poprawne wartości height (-64 do 320) ✅

**Świetnie napisana!**

---

## Utils - Pozostałe klasy (już dobre)

### ChatUtils.java - 9/10 ✅
- Adventure API + hex colors ✅
- Null safety ✅
- **Nie wymaga zmian**

### ItemBuilder.java - 9/10 ✅
- Fluent API ✅
- Adventure API ✅
- **Nie wymaga zmian**

### IntegerUtils.java - 10/10 ✅✅✅
- **PERFEKCJA! Nie zmieniaj NIC!**

### RandomUtils.java - 9/10 ✅
- ThreadLocalRandom ✅
- Auto-normalizacja chance ✅
- **Nie wymaga zmian**

### DataUtils.java - 8.5/10 ✅
- Regex parsing ✅
- Calendar handling ✅
- **Nie wymaga zmian**

### SafeUtils.java - 8/10 ✅
- Functional interfaces ✅
- Exception suppression ✅
- **Nie wymaga zmian**

### ParserUtils.java - 7.5/10
- **Minor issues** (ignored exceptions)
- Ale działa poprawnie ✅

### TitleUtils.java - 7/10
- **Deprecated API** ale dla kompatybilności OK ✅

---

## ActionBarTask.java

### ⚠️ WAŻNE usprawnienia:

#### 1. getPluginConfig() zamiast getConfigManager()
**Problem:**
```java
this.config = plugin.getPluginConfig();
```

**Fix:**
```java
this.config = plugin.getConfigManager();
```

---

#### 2. Niepotrzebna metoda cancelTask()
**Problem:**
```java
public void cancelTask() {
    try {
        this.cancel();
    } catch (IllegalStateException ignored) {
    }
}
```

W `DropPlugin.onDisable()` wywołujesz `actionBarTask.cancel()`, a nie `cancelTask()`.

**Fix:** Usuń metodę lub dodaj `shutdown()` jak w SaverTask:
```java
public void shutdown() {
    if (!isCancelled()) {
        cancel();
    }
}
```

---

#### 3. Auto-cancel gdy config wyłączy actionbar
**Fix:**
```java
@Override
public void run() {
    if (!this.config.isActionbarStatus()) {
        cancel();  // ← auto-cancel jeśli wyłączono
        return;
    }
    
    boolean turboDrop = this.config.isTurboDrop();
    String actionbarMessage = this.config.getActionbarMessage();

    if (turboDrop && actionbarMessage != null && !actionbarMessage.isEmpty()) {
        String formattedMessage = StringUtils.replace(actionbarMessage, "{TIME}", 
            DataUtils.durationToString(this.config.getTurboDrop()));
        for (Player player : Bukkit.getOnlinePlayers()) {
            TitleUtils.sendActionbar(player, formattedMessage);
        }
    }
}
```

**Ocena:** 7/10 → 8.5/10 po poprawkach

---

## UserPointsChangeListener.java

### ⚠️ WAŻNE usprawnienia:

#### 1. getPluginConfig() zamiast getConfigManager()
**Problem:**
```java
this.config = plugin.getPluginConfig();
```

**Fix:**
```java
this.config = plugin.getConfigManager();
```

---

#### 2. u.getPointsRequired() bez parametru
**Problem:**
```java
if (levelPoints >= u.getPointsRequired()) {
```

**Fix:**
```java
int pointsRequired = u.getPointsRequired(config.getPointsToLvlUp());
if (u.getPoints() >= pointsRequired) {
    int newLevel = u.getLvl() + 1;
    u.setLvl(newLevel);
    u.setPoints(0);
}
```

---

#### 3. Brak save po awansie
**Problem:**
```java
u.setLvl(newLevel);
u.setPoints(0);
// ← Brak save! Ważny milestone może zostać utracony przy crash
```

**Fix:**
```java
u.setLvl(newLevel);
u.setPoints(0);

// Save immediately after level up
plugin.getUserManager().save(u.getIdentifier(), false);
```

**Ocena:** 8/10 → 9/10 po poprawkach

---

## UserLevelChangeListener.java

### ⚠️ WAŻNE usprawnienia:

#### 1. getPluginConfig() zamiast getConfigManager()
**Problem:**
```java
this.config = plugin.getPluginConfig();
```

**Fix:**
```java
this.config = plugin.getConfigManager();
```

---

#### 2. Hardcoded obliczenie punktów
**Problem:**
```java
String personal = this.config.getLvlUpMessage()
        .replace("{LVL}", String.valueOf(lvl))
        .replace("{POINTS}", String.valueOf(lvl * this.config.getPointsToLvlUp()));
```

**Fix:**
```java
int pointsForNextLevel = event.getUser().getPointsRequired(config.getPointsToLvlUp());
String personal = this.config.getLvlUpMessage()
        .replace("{LVL}", String.valueOf(lvl))
        .replace("{POINTS}", String.valueOf(pointsForNextLevel));
```

**Ocena:** 7.5/10 → 8.5/10 po poprawkach

---

## UserPointsChangeEvent.java

### ✅ Świetna klasa - 9/10

**Plusy:**
- Poprawna implementacja Event API ✅
- HANDLER_LIST static final ✅
- Gettery dla wszystkich danych ✅

**Nie wymaga zmian!** ✅

---

## UserLevelChangeEvent.java

### ✅ Świetna klasa - 9/10

**Plusy:**
- Poprawna implementacja Event API ✅
- HANDLER_LIST static final ✅
- Gettery dla wszystkich danych ✅

**Nie wymaga zmian!** ✅

---

## DropPluginExpansion.java (PlaceholderAPI)

### 🔴 KRYTYCZNE problemy:

#### 1. ConfigManager.getConfigManager() i Database.getInstance() - SINGLETONY!
**Problem:**
```java
private String formatTopLevelPlayer(String[] playerData) {
    if (playerData == null || playerData.length < 2) {
        return ConfigManager.getConfigManager().getToplevelNoPlayer();  // ← SINGLETON
    }
    String format = ConfigManager.getConfigManager().getToplevelFormat();  // ← SINGLETON
    // ...
}

// W onRequest():
Database db = Database.getInstance();  // ← SINGLETON!
```

**Fix:**
```java
private final ConfigManager config;

public DropPluginExpansion(DropPlugin plugin) {
    this.plugin = plugin;
    this.config = plugin.getConfigManager();
}

private String formatTopLevelPlayer(String[] playerData) {
    if (playerData == null || playerData.length < 2) {
        return config.getToplevelNoPlayer();
    }
    String format = config.getToplevelFormat();
    return format.replace("{NAME}", playerData[0]).replace("{LEVEL}", playerData[1]);
}

// W onRequest():
Database db = plugin.getDatabase();  // ← używaj plugin
```

---

#### 2. user.getPointsRequired() i getPointsToNextLevel() bez parametru
**Problem:**
```java
if (params.equalsIgnoreCase("points_required")) {
    User user = plugin.getUserManager().getUserIfLoaded(onlinePlayer.getUniqueId());
    if (user == null) return "0";
    return String.valueOf(user.getPointsRequired());  // ← brak parametru
}
```

**Fix:**
```java
if (params.equalsIgnoreCase("points_required")) {
    User user = plugin.getUserManager().getUserIfLoaded(onlinePlayer.getUniqueId());
    if (user == null) return "0";
    
    int pointsPerLevel = config.getPointsToLvlUp();
    return String.valueOf(user.getPointsRequired(pointsPerLevel));
}

if (params.equalsIgnoreCase("points_to_next")) {
    User user = plugin.getUserManager().getUserIfLoaded(onlinePlayer.getUniqueId());
    if (user == null) return "0";
    
    int pointsPerLevel = config.getPointsToLvlUp();
    return String.valueOf(user.getPointsToNextLevel(pointsPerLevel));
}
```

---

#### 3. Cache dla toplevel - ŚWIETNY POMYSŁ! ✅
**Problem:** BRAK! To jest doskonałe rozwiązanie!

```java
private final Map<Integer, CacheEntry> topLevelCache = new ConcurrentHashMap<>();
private static final long CACHE_DURATION_MS = 30000; // 30 seconds
```

**To unika częstych zapytań do bazy!** ✅

---

#### 4. getUserIfLoaded() - POPRAWNE użycie! ✅
```java
User user = plugin.getUserManager().getUserIfLoaded(onlinePlayer.getUniqueId());
```

**Używasz `getUserIfLoaded()` zamiast `getOrCreateUser()`** - to jest POPRAWNE dla placeholderów! ✅

**Ocena:** 7/10 → 9/10 po poprawkach

---

## DropCommand.java

### ⚠️ WAŻNE usprawnienia:

#### 1. getPluginConfig() zamiast getConfigManager()
**Problem:**
```java
ChatUtils.sendMessage(sender, this.plugin.getPluginConfig().getCmdDropNoPermission());
```

**Fix:**
```java
ChatUtils.sendMessage(sender, this.plugin.getConfigManager().getCmdDropNoPermission());
```

---

#### 2. Konstruktor - dobrze zabezpieczony ✅
```java
if (plugin.getCommand("drop") != null) {
    plugin.getCommand("drop").setExecutor(this);
} else {
    plugin.getLogger().warning("Command 'drop' is not defined in plugin.yml");
}
```

**To jest ŚWIETNE!** ✅

**Ocena:** 8/10 → 9/10 po poprawkach

---

## LevelCommand.java

### ⚠️ WAŻNE usprawnienia:

#### 1. getPluginConfig() zamiast getConfigManager()
**Problem:**
```java
var config = this.plugin.getPluginConfig();
```

**Fix:**
```java
var config = this.plugin.getConfigManager();
```

---

#### 2. getUser() zamiast getOrCreateUser()
**Problem:**
```java
User u = this.userManager.getUser(player);
```

**Fix:**
```java
User u = this.userManager.getOrCreateUser(player);
```

---

#### 3. user.getPointsToNextLevel() bez parametru
**Problem:**
```java
ChatUtils.sendMessage(sender, config.getCmdLevelPointsToNext()
        .replace("{POINTS_TO_NEXT}", String.valueOf(user.getPointsToNextLevel())));
```

**Fix:**
```java
int pointsPerLevel = config.getPointsToLvlUp();
int pointsToNext = user.getPointsToNextLevel(pointsPerLevel);
ChatUtils.sendMessage(sender, config.getCmdLevelPointsToNext()
        .replace("{POINTS_TO_NEXT}", String.valueOf(pointsToNext)));
```

**Ocena:** 7.5/10 → 9/10 po poprawkach

---

## ADropCommand.java

### 🔴 KRYTYCZNE problemy:

#### 1. getPluginConfig() zamiast getConfigManager()
**Problem:**
```java
var config = this.plugin.getPluginConfig();
```

**Fix:**
```java
var config = this.plugin.getConfigManager();
```

---

#### 2. getUser() zamiast getOrCreateUser()
**Problem:**
```java
User user = this.userManager.getUser(targetPlayer);
```

**Fix:**
```java
User user = this.userManager.getOrCreateUser(targetPlayer);
```

---

#### 3. addTurboDrop/Exp - złe nazwy (SET a nie ADD)
**Problem:**
```java
if (isDrop) {
    user.addTurboDrop(time);  // ← to jest SET, nie ADD
} else {
    user.addTurboExp(time);
}
```

**Fix:**
```java
if (isDrop) {
    user.setTurboDrop(time);
} else {
    user.setTurboExp(time);
}
```

---

#### 4. config.addTurboDrop/Exp - przenieś do TurboManager
**Problem:**
```java
if (isDrop) {
    config.addTurboDrop(time);
    config.save();
}
```

**Fix (po stworzeniu TurboManager):**
```java
TurboManager turboManager = plugin.getTurboManager();
if (isDrop) {
    turboManager.setGlobalTurboDrop(time);
} else {
    turboManager.setGlobalTurboExp(time);
}
```

---

#### 5. Brak save po zmianie levelu
**Problem:**
```java
targetUser.setLvl(newLevel);
targetUser.setPoints(newPoints);
// ← Brak save!
```

**Fix:**
```java
targetUser.setLvl(newLevel);
targetUser.setPoints(newPoints);

// Save immediately
plugin.getUserManager().save(targetUser.getIdentifier(), false);
```

---

#### 6. Constants - ŚWIETNIE! ✅
```java
private static final int MIN_LEVEL = 1;
private static final int MAX_LEVEL = 1000;
private static final int MIN_POINTS = 0;
private static final int MAX_POINTS = 1000000;
```

**To jest DOSKONAŁE!** ✅

**Ocena:** 6.5/10 → 8.5/10 po poprawkach

---

## plugin.yml

### ✅ PERFEKCJA - 10/10

**Wszystko idealnie:**
- API version ✅
- Soft-depend PlaceholderAPI ✅
- Commands z aliasami ✅
- Description ✅

**Nie zmieniaj NIC!**

---

## Podsumowanie priorytetów

### 🔴 KRYTYCZNE (zrób NAJPIERW):

1. **Database.java - Race condition przy inicjalizacji**
   - Synchroniczna inicjalizacja połączenia
   - Async tylko ładowanie użytkowników
   - PRIORYTET: ⭐⭐⭐⭐⭐

2. **Database.java - Usuń singleton pattern**
   - Usuń `getInstance()`
   - Używaj tylko `plugin.getDatabase()`
   - PRIORYTET: ⭐⭐⭐⭐⭐

3. **UserManager.java - Przekaż plugin w konstruktorze**
   - `new UserManager(this)`
   - Usuń `Database.getInstance()`
   - PRIORYTET: ⭐⭐⭐⭐⭐

4. **UserManager.java - Dodaj saveAndRemove()**
   - Atomic operation dla logout
   - Zapobiega utracie danych
   - PRIORYTET: ⭐⭐⭐⭐⭐

5. **PlayerJoinLeaveListener.java - Zmień saveAndCleanup() na saveAndRemove()**
   - ZŁA KOLEJNOŚĆ save/remove = UTRATA DANYCH!
   - PRIORYTET: ⭐⭐⭐⭐⭐

6. **ConfigManager.java - Usuń singleton pattern**
   - Usuń `getConfigManager()`
   - User.java nie powinien używać singletona
   - PRIORYTET: ⭐⭐⭐⭐⭐

7. **ItemUtils.java - Usuń ConfigManager.getConfigManager()**
   - Przekaż ConfigManager jako parametr w giveItem()
   - Zaktualizuj wywołania w DropManager
   - PRIORYTET: ⭐⭐⭐⭐⭐

8. **DropPluginExpansion.java - Usuń singletony**
   - ConfigManager.getConfigManager() → plugin.getConfigManager()
   - Database.getInstance() → plugin.getDatabase()
   - PRIORYTET: ⭐⭐⭐⭐⭐

9. **DropManager.java - Durability w pętli**
   - Zmniejsza się wielokrotnie zamiast raz!
   - Przenieś POZA pętlę dropów
   - PRIORYTET: ⭐⭐⭐⭐⭐

10. **DropManager.java - user.addPoints() bez parametru**
    - Dodaj `config.isLvlStatus()` jako parametr
    - PRIORYTET: ⭐⭐⭐⭐

11. **Database.java - Dodaj kolumnę lastKnownName**
    - Cache nazw graczy
    - Unikaj blokującego getOfflinePlayer()
    - PRIORYTET: ⭐⭐⭐⭐

12. **DropConfigManager.java - Uprość logikę inicjalizacji**
    - Usuń zbędny try-catch
    - Dodaj pole plugin dla loggera
    - PRIORYTET: ⭐⭐⭐⭐

### ⚠️ WAŻNE (zrób następnie):

6. **Database.java - Dodaj indeksy**
   - `CREATE INDEX idx_lvl_points`
   - PRIORYTET: ⭐⭐⭐

7. **SaverTask.java - Try-catch wewnątrz pętli**
   - Jeden błąd nie kończy całego save
   - PRIORYTET: ⭐⭐⭐

8. **DropPlugin.java - Kolejność shutdown**
   - Taski → Gracze → Baza
   - PRIORYTET: ⭐⭐⭐

9. **UserManager.java - Zmień nazwy metod**
   - `getOrCreateUser()` zamiast `getUser()`
   - Jasność intencji
   - PRIORYTET: ⭐⭐⭐

10. **ConfigManager.java - Przenieś turboDrop/turboExp do TurboManager**
    - Stwórz nową klasę TurboManager
    - Config powinien być read-only
    - PRIORYTET: ⭐⭐⭐

11. **PlayerJoinLeaveListener.java - Usuń onKick()**
    - PlayerKickEvent jest podklasą PlayerQuitEvent
    - Duplikacja eventów
    - PRIORYTET: ⭐⭐⭐

12. **DropManager.java - isStoneLike do config.yml**
    - Hardcoded lista materiałów
    - Przenieś do konfiguracji
    - PRIORYTET: ⭐⭐⭐

13. **User.java - Usuń ConfigManager.getConfigManager()**
    - Przekaż wartości w metodach
    - PRIORYTET: ⭐⭐⭐

14. **User.java - getMinedDrops() zwraca mutable**
    - Collections.unmodifiableMap()
    - PRIORYTET: ⭐⭐

15. **InventoryClickListener.java - Dodaj save po zmianach**
    - handleIndividualDrop, toggleMessages, toggleCobble
    - Bez tego utrata danych przy crash
    - PRIORYTET: ⭐⭐⭐

16. **InventoryClickListener.java - Refactor enable/disable all**
    - handleToggleAllDrops(enable: boolean)
    - DRY principle
    - PRIORYTET: ⭐⭐

17. **DropMenu.java - user.getPointsToNextLevel() bez parametru**
    - Przekaż pointsPerLevel
    - PRIORYTET: ⭐⭐⭐

18. **DropMenu.java - Magic numbers w replaced()**
    - Stwórz enum MenuItemType
    - PRIORYTET: ⭐⭐

19. **UserPointsChangeListener.java - Dodaj save po level up**
    - Save po setLvl() + setPoints(0)
    - Ważny milestone
    - PRIORYTET: ⭐⭐⭐

20. **UserPointsChangeListener.java - getPointsRequired() bez parametru**
    - Przekaż config.getPointsToLvlUp()
    - PRIORYTET: ⭐⭐⭐

21. **UserLevelChangeListener.java - Hardcoded formula punktów**
    - Użyj user.getPointsRequired(config.getPointsToLvlUp())
    - PRIORYTET: ⭐⭐

22. **DropPluginExpansion.java - getPointsRequired/ToNextLevel bez parametru**
    - Przekaż config.getPointsToLvlUp()
    - PRIORYTET: ⭐⭐⭐

23. **LevelCommand.java - getUser() + getPointsToNextLevel()**
    - Zmień na getOrCreateUser()
    - Przekaż parametr
    - PRIORYTET: ⭐⭐⭐

24. **ADropCommand.java - Brak save po level change**
    - Save po setLvl() + setPoints()
    - PRIORYTET: ⭐⭐⭐

25. **ADropCommand.java - addTurboDrop/Exp → setTurboDrop/Exp**
    - Poprawne nazwy metod
    - PRIORYTET: ⭐⭐⭐

### ℹ️ NICE TO HAVE (zrób gdy masz czas):

10. **build.gradle - Aktualizacja zależności**
    - MySQL connector, PlaceholderAPI
    - PRIORYTET: ⭐⭐

11. **DropPlugin.java - Refactor registerListeners**
    - Czytelniejszy kod
    - PRIORYTET: ⭐⭐

12. **SaverTask.java - Dodaj debug logging**
    - Lepszy monitoring
    - PRIORYTET: ⭐

13. **Database.java - Connection validation**
    - `setConnectionTestQuery()`
    - PRIORYTET: ⭐

14. **BlockBreakListener.java - Error message do config**
    - Przenieś hardcoded message
    - PRIORYTET: ⭐

15. **ConfigManager.java - Dodaj debug mode**
    - `debug: false` w config.yml
    - PRIORYTET: ⭐

16. **DropManager.java - Refactoring breakBlock()**
    - Rozbij na mniejsze metody
    - PRIORYTET: ⭐

17. **DropManager.java - Udokumentuj Fortune logic**
    - Czy wpływa na szansę i ilość?
    - PRIORYTET: ⭐

18. **User.java - Dodaj equals() i hashCode()**
    - Dla kolekcji
    - PRIORYTET: ⭐

19. **Chance.java - Dodaj final i validation**
    - Immutable pola
    - PRIORYTET: ⭐

20. **InventoryClickListener.java - Cache SERIALIZER**
    - Static final field
    - PRIORYTET: ⭐

21. **DropConfigManager.java - printStackTrace() → logger**
    - Debug mode check
    - PRIORYTET: ⭐

22. **MapUtils.java - Usuń printStackTrace()**
    - Silent fail lub logger
    - PRIORYTET: ⭐

23. **DropMenu.java - getUser() → getOrCreateUser()**
    - Spójność z resztą kodu
    - PRIORYTET: ⭐

24. **ActionBarTask.java - Dodaj shutdown() method**
    - Zamiast cancelTask()
    - PRIORYTET: ⭐

25. **ActionBarTask.java - Auto-cancel w run()**
    - Gdy config wyłączy actionbar
    - PRIORYTET: ⭐

26. **Wszystkie listenery - Zmień getPluginConfig() → getConfigManager()**
    - ActionBarTask, UserPointsChangeListener, UserLevelChangeListener
    - PRIORYTET: ⭐

27. **Wszystkie komendy - Zmień getPluginConfig() → getConfigManager()**
    - DropCommand, LevelCommand, ADropCommand
    - PRIORYTET: ⭐

---

## Checklist do wykonania

```markdown
### KRYTYCZNE (12 zadań)
- [ ] Database: Synchroniczna inicjalizacja połączenia (race condition fix)
- [ ] Database: Usuń singleton pattern (getInstance())
- [ ] UserManager: Przekaż plugin w konstruktorze
- [ ] UserManager: Dodaj metodę saveAndRemove()
- [ ] PlayerJoinLeaveListener: Zmień saveAndCleanup() na saveAndRemove()
- [ ] ConfigManager: Usuń singleton pattern (getConfigManager())
- [ ] ItemUtils: Usuń ConfigManager.getConfigManager(), przekaż jako parametr
- [ ] ItemUtils: Zaktualizuj wszystkie wywołania giveItem() w DropManager
- [ ] DropPluginExpansion: Usuń singletony (ConfigManager, Database)
- [ ] DropManager: Przenieś durability recalculation POZA pętlę dropów
- [ ] DropManager: Dodaj parametr levelingEnabled do user.addPoints()
- [ ] Database: Dodaj kolumnę lastKnownName do tabeli
- [ ] DropConfigManager: Uprość logikę inicjalizacji, dodaj pole plugin

### WAŻNE (25 zadań)
- [ ] Database: Dodaj indeksy (idx_lvl_points)
- [ ] SaverTask: Try-catch wewnątrz pętli
- [ ] DropPlugin: Popraw kolejność shutdown (taski → gracze → baza)
- [ ] UserManager: Zmień nazwy metod (getOrCreateUser)
- [ ] UserManager: Dodaj metodę saveAll()
- [ ] ConfigManager: Stwórz TurboManager i przenieś turboDrop/turboExp
- [ ] PlayerJoinLeaveListener: Usuń onKick() (duplikacja)
- [ ] DropManager: Przenieś isStoneLike do config.yml
- [ ] User: Usuń ConfigManager.getConfigManager(), przekaż wartości w metodach
- [ ] User: getMinedDrops() → Collections.unmodifiableMap()
- [ ] InventoryClickListener: Dodaj save w handleIndividualDrop()
- [ ] InventoryClickListener: Dodaj save w toggleMessages() i toggleCobble()
- [ ] InventoryClickListener: Refactor enable/disable all → handleToggleAllDrops()
- [ ] InventoryClickListener: Zmień getPluginConfig() na getConfigManager()
- [ ] InventoryClickListener: Zmień getUser() na getOrCreateUser()
- [ ] DropMenu: Przekaż pointsPerLevel do getPointsToNextLevel()
- [ ] DropMenu: Stwórz enum MenuItemType zamiast magic numbers
- [ ] DropMenu: Zmień getPluginConfig() na getConfigManager()
- [ ] UserPointsChangeListener: Dodaj save po level up
- [ ] UserPointsChangeListener: Przekaż parametr do getPointsRequired()
- [ ] UserLevelChangeListener: Użyj getPointsRequired() z parametrem
- [ ] DropPluginExpansion: Przekaż parametr do getPointsRequired/ToNextLevel
- [ ] LevelCommand: getUser() → getOrCreateUser(), przekaż parametr
- [ ] ADropCommand: Dodaj save po level change
- [ ] ADropCommand: addTurboDrop/Exp → setTurboDrop/Exp

### NICE TO HAVE (27 zadań)
- [ ] build.gradle: Aktualizuj zależności (MySQL, PlaceholderAPI)
- [ ] DropPlugin: Refactor registerListeners
- [ ] DropPlugin: Usuń duplikację getterów (getConfigManager vs getPluginConfig)
- [ ] SaverTask: Dodaj debug logging
- [ ] SaverTask: Zmień delay z 20L na 200L
- [ ] Database: Dodaj connection validation
- [ ] BlockBreakListener: Error message do config.yml
- [ ] BlockBreakListener: Debug mode dla printStackTrace()
- [ ] ConfigManager: Dodaj pole debug i dropErrorMessage
- [ ] DropManager: Refactoring - rozbij breakBlock() na mniejsze metody
- [ ] DropManager: Udokumentuj Fortune logic (szansa + ilość)
- [ ] DropManager: Standaryzuj format Chance.chance w config
- [ ] User: Dodaj equals() i hashCode()
- [ ] User: addTurboExp/Drop → setTurboExp/Drop lub addDuration
- [ ] User: Walidacja w setLvl() i setPoints()
- [ ] Chance: Dodaj final do pól i validation
- [ ] Chance: Dodaj equals() i hashCode()
- [ ] InventoryClickListener: Cache SERIALIZER jako static final
- [ ] DropConfigManager: printStackTrace() → logger + debug mode
- [ ] DropConfigManager: Dodaj getFile() getter
- [ ] MapUtils: Usuń printStackTrace() w deserializeMap()
- [ ] DropMenu: getUser() → getOrCreateUser()
- [ ] ActionBarTask: Dodaj shutdown() method
- [ ] ActionBarTask: Auto-cancel w run() gdy config wyłączy
- [ ] ActionBarTask: getPluginConfig() → getConfigManager()
- [ ] UserPointsChangeListener: getPluginConfig() → getConfigManager()
- [ ] UserLevelChangeListener: getPluginConfig() → getConfigManager()
- [ ] DropCommand: getPluginConfig() → getConfigManager()
- [ ] LevelCommand: getPluginConfig() → getConfigManager()
- [ ] ADropCommand: getPluginConfig() → getConfigManager()
- [ ] ParserUtils: Rozważ logowanie ignored exceptions
```

---

## Dodatkowe uwagi

### Config.yml - Dodaj te opcje:
```yaml
database:
  type: "sqlite"  # lub "mysql"
  
  # MySQL settings
  host: "localhost"
  port: 3306
  database: "minecraft"
  username: "root"
  password: "password"
  
  # Connection pool
  max-pool-size: 10
  connection-timeout: 30000
  idle-timeout: 600000
  leak-detection-threshold: 0  # 0 = wyłączone

autosave:
  enabled: true
  delay: 200      # 10 sekund po starcie
  period: 6000    # co 5 minut

debug: false      # wyłącz szczegółowe logi w produkcji

messages:
  drop-error: "&cWystąpił błąd podczas przetwarzania dropu. Skontaktuj się z administratorem."

settings:
  stone-like-blocks:
    - STONE
    - GRANITE
    - DIORITE
    - ANDESITE
    - DEEPSLATE
    - TUFF
    - CALCITE
  
  chances:
    vip:
      permission: "drop.vip"
      additionalchance: 5.0  # ZAWSZE w procentach (5.0 = +5%)
    mvp:
      permission: "drop.mvp"
      additionalchance: 10.0  # +10%
```

### Struktura tabel SQL:
```sql
-- MySQL
CREATE TABLE IF NOT EXISTS `drop_users` (
    identifier VARCHAR(36) NOT NULL,
    cobble TINYINT(1) NOT NULL,
    messages TINYINT(1) NOT NULL,
    turboDrop BIGINT NOT NULL,
    turboExp BIGINT NOT NULL,
    lvl INT NOT NULL,
    points INT NOT NULL,
    minedDrops TEXT NOT NULL,
    disabledDrops TEXT NOT NULL,
    lastKnownName VARCHAR(16),
    PRIMARY KEY(identifier),
    INDEX idx_lvl_points (lvl DESC, points DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- SQLite
CREATE TABLE IF NOT EXISTS drop_users (
    identifier TEXT PRIMARY KEY NOT NULL,
    cobble INTEGER NOT NULL,
    messages INTEGER NOT NULL,
    turboDrop INTEGER NOT NULL,
    turboExp INTEGER NOT NULL,
    lvl INTEGER NOT NULL,
    points INTEGER NOT NULL,
    minedDrops TEXT NOT NULL,
    disabledDrops TEXT NOT NULL,
    lastKnownName TEXT
);
CREATE INDEX IF NOT EXISTS idx_lvl_points ON drop_users(lvl DESC, points DESC);
```

---

**Powodzenia z poprawkami! 🚀**

Jeśli masz pytania lub potrzebujesz pomocy z implementacją którejkolwiek z poprawek, śmiało pytaj!

---

## 📊 Tabela ocen wszystkich klas

### Klasy główne i managery

| Klasa | Ocena przed | Ocena po | Główne problemy |
|-------|-------------|----------|-----------------|
| **DropPlugin** | 7.5/10 | 9/10 | Duplikacja getterów, kolejność shutdown |
| **Database** | 6/10 | 9/10 | Race condition, singleton pattern, brak indeksów |
| **UserManager** | 6.5/10 | 9/10 | Singleton, saveAndRemove, nazwy metod |
| **ConfigManager** | 7/10 | 9/10 | Singleton pattern, turboDrop/turboExp |
| **DropConfigManager** | 6/10 | 8.5/10 | Dziwna logika init, printStackTrace |
| **DropManager** | 6.5/10 | 8.5/10 | Durability w pętli, singleton, magic numbers |
| **SaverTask** | 5/10 | 8.5/10 | Try-catch w złym miejscu, sendEmptyUpdate |
| **ActionBarTask** | 7/10 | 8.5/10 | getPluginConfig, cancelTask() |

### Listeners

| Klasa | Ocena przed | Ocena po | Główne problemy |
|-------|-------------|----------|-----------------|
| **PlayerJoinLeaveListener** | 3/10 | 9/10 | UTRATA DANYCH (save/remove), duplikacja onKick |
| **BlockBreakListener** | 8/10 | 9.5/10 | Hardcoded message, printStackTrace |
| **InventoryClickListener** | 7.5/10 | 9/10 | Brak save, duplikacja enable/disable |
| **UserPointsChangeListener** | 8/10 | 9/10 | getPointsRequired bez param, brak save |
| **UserLevelChangeListener** | 7.5/10 | 8.5/10 | getPluginConfig, hardcoded formula |

### Obiekty (objects)

| Klasa | Ocena przed | Ocena po | Główne problemy |
|-------|-------------|----------|-----------------|
| **User** | 7/10 | 9/10 | Singleton ConfigManager, validation, mutable |
| **Drop** | 9/10 | 9.5/10 | Minor: exp validation |
| **Count** | 10/10 | 10/10 | **PERFEKCJA!** ✅ |
| **Chance** | 7/10 | 8.5/10 | Brak final, validation, equals/hashCode |

### Events

| Klasa | Ocena | Główne problemy |
|-------|-------|-----------------|
| **UserPointsChangeEvent** | 9/10 | Świetna! ✅ |
| **UserLevelChangeEvent** | 9/10 | Świetna! ✅ |

### Commands

| Klasa | Ocena przed | Ocena po | Główne problemy |
|-------|-------------|----------|-----------------|
| **DropCommand** | 8/10 | 9/10 | getPluginConfig |
| **LevelCommand** | 7.5/10 | 9/10 | getUser, getPointsToNextLevel bez param |
| **ADropCommand** | 6.5/10 | 8.5/10 | Singleton turbo, getUser, brak save |

### Integrations

| Klasa | Ocena przed | Ocena po | Główne problemy |
|-------|-------------|----------|-----------------|
| **DropPluginExpansion** | 7/10 | 9/10 | Singletony, getPointsRequired bez param |

### Utils

| Klasa | Ocena | Główne problemy |
|-------|-------|-----------------|
| **ChatUtils** | 9/10 | Już świetna! ✅ |
| **ItemBuilder** | 9/10 | Już świetna! ✅ |
| **IntegerUtils** | 10/10 | **PERFEKCJA!** ✅ |
| **RandomUtils** | 9/10 | Już bardzo dobra! ✅ |
| **DataUtils** | 8.5/10 | Już dobra! ✅ |
| **SafeUtils** | 8/10 | Już dobra! ✅ |
| **ItemUtils** | 5/10 → 9/10 | Singleton ConfigManager |
| **MapUtils** | 7/10 → 8/10 | printStackTrace |
| **ParserUtils** | 7.5/10 | Minor: ignored exceptions |
| **TitleUtils** | 7/10 | Deprecated API (ale OK) |
| **DropMenu** | 6/10 → 8/10 | Magic numbers, getPointsToNextLevel |

### Config

| Klasa | Ocena | Notatki |
|-------|-------|---------|
| **plugin.yml** | 10/10 | **PERFEKCJA!** ✅ |
| **build.gradle** | 8/10 → 9/10 | MySQL connector deprecated |

---

## 🏆 Najlepsze klasy (nie zmieniaj!)

1. **Count.java** - 10/10 ✅✅✅
2. **IntegerUtils.java** - 10/10 ✅✅✅
3. **plugin.yml** - 10/10 ✅✅✅
4. **UserPointsChangeEvent** - 9/10 ✅
5. **UserLevelChangeEvent** - 9/10 ✅
6. **ChatUtils.java** - 9/10 ✅
7. **ItemBuilder.java** - 9/10 ✅
8. **RandomUtils.java** - 9/10 ✅
9. **Drop.java** - 9/10 ✅

---

## 🔥 Najgorsze klasy (wymagają pilnych poprawek!)

1. **PlayerJoinLeaveListener** - 3/10 → 9/10 (UTRATA DANYCH!)
2. **ItemUtils** - 5/10 → 9/10 (singleton)
3. **SaverTask** - 5/10 → 8.5/10 (try-catch w złym miejscu)
4. **Database** - 6/10 → 9/10 (race condition, singleton)
5. **DropMenu** - 6/10 → 8/10 (magic numbers)
6. **ADropCommand** - 6.5/10 → 8.5/10 (singleton turbo, brak save)

---

## 📈 Statystyki FINALNE

- **Przeanalizowane klasy:** 35+ (WSZYSTKIE!)
- **Znalezione singletony:** 5 (Database, ConfigManager, ItemUtils via ConfigManager, DropPluginExpansion, TurboManager needed)
- **Race conditions:** 2 (Database init, PlayerJoinLeaveListener save/remove)
- **Potencjalne memory leaks:** 3 (singletony)
- **Utrata danych:** 4 (PlayerJoinLeaveListener, InventoryClickListener bez save, UserPointsChangeListener bez save, ADropCommand bez save)
- **Brak walidacji:** 8 przypadków (getPointsRequired, getPointsToNextLevel bez parametrów)
- **Magic numbers:** 1 (DropMenu - type 1,2,3,4)
- **getPluginConfig vs getConfigManager:** 15+ miejsc

---

## 📝 Przykładowa konfiguracja

### config.yml - Najważniejsze dodatkowe opcje

```yaml
# === NOWE OPCJE DO DODANIA ===

database:
  type: "sqlite"  # lub "mysql"
  
  # MySQL settings
  host: "localhost"
  port: 3306
  user: "user"
  base: "database"
  password: "password"
  
  # Connection pool (HikariCP)
  maxPool: 10
  connectionTimeoutMs: 30000
  idleTimeoutMs: 600000
  leakDetectionThresholdMs: 0

# Autosave settings
autosave:
  enabled: true
  delay: 200       # 10 sekund po starcie (20 ticks = 1 sekunda)
  period: 6000     # co 5 minut (6000 ticks = 300 sekund)

# Debug mode (wyłącz w produkcji!)
debug: false

# Dodatkowe wiadomości
messages:
  drop-error: "&cWystąpił błąd podczas przetwarzania dropu. Skontaktuj się z administratorem."

# Stone-like blocks (konfigurowalna lista zamiast hardcoded)
settings:
  stone-like-blocks:
    - STONE
    - GRANITE
    - DIORITE
    - ANDESITE
    - DEEPSLATE
    - TUFF
    - CALCITE
  
  # Format szans w config (ZAWSZE w procentach)
  # 5.0 = +5% szansy, 0.5 = +0.5% szansy
  chances:
    vip:
      permission: "dropplugin.vip"
      additionalchance: 5.0     # +5% szansy
    mvp:
      permission: "dropplugin.mvp"
      additionalchance: 10.0    # +10% szansy

# PlaceholderAPI settings
placeholders:
  toplevel-no-player: "-"
  toplevel-format: "&f{NAME} &7[&e{LEVEL}&7]"
```

---

### drops.yml - Przykładowe dropy

```yaml
drops:
  diamond:
    name: "Diament"
    item: "material:diamond"
    chance: 0.5          # 0.5% szansy (automatycznie konwertowane do 0.005)
    amount: 1-2          # 1-2 diamenty (bez Fortune)
    height: -64-16       # Głębokie warstwy (od -64 do 16 Y)
    points: 8-15         # 8-15 punktów do levelowania
    exp: 10              # 10 exp za wykopanie
    fortune: true        # Fortune działa:
                         #   I: 33% szansa +1
                         #   II: 50% szansa +1-2
                         #   III: 60% szansa +1-3
    neededlvl: 15        # Wymagany poziom 15 do odblokowania

  netherite_scrap:
    name: "Netherite Scrap"
    item: "material:netherite_scrap"
    chance: 0.1          # 0.1% (bardzo rzadki!)
    amount: 1-1          # Zawsze 1
    height: -64-16       # Najgłębsze warstwy
    points: 20-30        # Dużo punktów
    exp: 20              # Dużo exp
    fortune: false       # Fortune NIE działa
    neededlvl: 30        # Wymaga poziomu 30

# Exp za kopanie bloków (bez dropu)
exps:
  stone: 1             # Stone, granite, diorite, andesite, deepslate
  obsidian: 5          # Obsidian
```

---

### SQL Schema (po poprawkach)

```sql
-- MySQL
CREATE TABLE `drop_users` (
    identifier VARCHAR(36) NOT NULL,
    cobble TINYINT(1) NOT NULL DEFAULT 1,
    messages TINYINT(1) NOT NULL DEFAULT 1,
    turboDrop BIGINT NOT NULL DEFAULT 0,
    turboExp BIGINT NOT NULL DEFAULT 0,
    lvl INT NOT NULL DEFAULT 1,
    points INT NOT NULL DEFAULT 0,
    minedDrops TEXT NOT NULL,
    disabledDrops TEXT NOT NULL,
    lastKnownName VARCHAR(16),  -- NOWA KOLUMNA!
    PRIMARY KEY(identifier),
    INDEX idx_lvl_points (lvl DESC, points DESC)  -- NOWY INDEKS!
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- SQLite
CREATE TABLE drop_users (
    identifier TEXT PRIMARY KEY NOT NULL,
    cobble INTEGER NOT NULL DEFAULT 1,
    messages INTEGER NOT NULL DEFAULT 1,
    turboDrop INTEGER NOT NULL DEFAULT 0,
    turboExp INTEGER NOT NULL DEFAULT 0,
    lvl INTEGER NOT NULL DEFAULT 1,
    points INTEGER NOT NULL DEFAULT 0,
    minedDrops TEXT NOT NULL,
    disabledDrops TEXT NOT NULL,
    lastKnownName TEXT  -- NOWA KOLUMNA!
);
CREATE INDEX idx_lvl_points ON drop_users(lvl DESC, points DESC);  -- NOWY INDEKS!
```

---

## ✅ Finalne zalecenia

### Zrób w tej kolejności:

1. **DZIEŃ 1 - KRYTYCZNE (11 zadań):**
   - Database: race condition + singleton
   - UserManager: saveAndRemove + przekaż plugin
   - ConfigManager: usuń singleton
   - ItemUtils: usuń singleton
   - PlayerJoinLeaveListener: FIX UTRATY DANYCH!
   - DropManager: durability + addPoints
   - DropConfigManager: uprość init

2. **DZIEŃ 2 - WAŻNE (18 zadań):**
   - Wszystkie nazwy metod (getOrCreateUser)
   - TurboManager
   - Save w InventoryClickListener
   - Indeksy w Database
   - Try-catch w SaverTask

3. **DZIEŃ 3+ - NICE TO HAVE (23 zadania):**
   - Refactoring, code cleanup
   - Debug mode
   - Dokumentacja
   - Enums zamiast magic numbers

## ✨ PRZYSZŁOŚCIOWE ##

1. **MODULAR GUI SYSTEM REFACTORING:**
Więcej opcji gui - Obecnie po wpisaniu /drop pokazuje tylko i wyłacznie drop ze stone z innymi zmiennymi typu wyłącz wiadomości, cobble etc.
    - Cała konfiguracja paneli gui musi się odbywać w pliku gui.yml
    - Mamy opcje konfiguracji wyglądu przedmiotu, nazwy, lore, czy ma miec enchant oraz inne ważne flagi, w jakim slocie ma byc skrzynki etc.
    - Drop z stone ma być zawsze włączony, natomiast mamy opcje włączenia czy drop z cobblex ma być włączony, wyświetlanie jego, tak samo drop ze skrzynek
    - ZMIANA: po wpisaniu /drop wyświetla sie gui w którym będą itemki:
    * drop ze stone, drop z cobblex, drop ze skrzynki,
    * możliwośc wyłączenia wiadomości z każdego dropu: drop ze stone, drop z cobblex,
    * drop ze skrzynki będzie tylko wyświetlać co można wydropić - osobny plugin na skrzynki 
    * po klinkieciu w niego przechodzimy dopiero do wybranego gui z dropem stone
    - Itemek z statystykami gracza:
    * Wyświetli statystyki zdobyte przez gracza w których będzie:
    * Nick gracza, poziom gracza, punkty gracza, wykopany kamień, pozycja w topce rankingu poziomu 
    - Abstrakcja Menu: Każde menu (Drop, Statystyki, CobbleX) musi dziedziczyć
    * po bazowym interfejsie/klasie InventoryHolder lub dedykowanym wrapperze, aby uniknąć redundancji kodu przy obsłudze InventoryClickEvent.
    - Dynamiczne Statystyki: Dane gracza (poziom, punkty) muszą być pobierane z cache
    * (np. Caffeine) zsynchronizowanego z bazą danych (MySQL/SQLite) przez HikariCP.
Zamiast jednego wielkiego menu, GUI powinno działać w strukturze drzewa. Każdy węzeł to osobna klasa implementująca wspólny interfejs widoku.
Root (Main Menu): Pełni rolę routera.
Sub-Menus (Stone, CobbleX, Crates): Pobierają dane z odpowiednich serwisów .
State Management: System musi zapamiętywać, skąd gracz przyszedł, aby przycisk "Powrót" działał intuicyjnie.