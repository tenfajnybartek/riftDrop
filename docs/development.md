# Budowanie i architektura

[Wróć do README](../README.md)

## Build na JDK 25

Ustaw `JAVA_HOME` na JDK 25. Repozytorium zawiera Gradle Wrapper 9.8.2.

```powershell
.\gradlew.bat clean build
.\gradlew.bat runServer
```

W systemach Linux i macOS użyj `./gradlew`. Zadanie `build` testuje kod i składa `build/libs/riftDrop-1.3.jar`. Zadanie `runServer` uruchamia deweloperski serwer Paper 26.3. Kod produkcyjny kompiluje się z `--release 25` przeciw Paper API `26.3.build.255-stable`.

JAR zawiera relokowane HikariCP. Sterowniki SQLite JDBC 3.49.1.0 i MySQL Connector/J 9.2.0 mają w produkcyjnym buildzie zakres `compileOnly`, ponieważ zapewnia je Paper 26.3 #255. Testy Gradle mają własne zależności JDBC. Po zmianie docelowego Paper sprawdź ponownie dostępność sterowników i oba backendy.

## Pakiety

| Pakiet w `pl.tenfajnybartek.riftdrop` | Odpowiedzialność |
| --- | --- |
| `configuration` | Odczyt i walidacja YAML oraz migracja katalogu |
| `mining` | Reguły wydobycia, losowanie, XP i dostarczanie nagród |
| `menu` | Układ, renderowanie i obsługa slotów GUI |
| `profile` | Sesje graczy, poziomy, Turbo i publikowanie snapshotów |
| `storage` | JDBC, zapis profili, odzyskiwanie kolejki i ranking |
| `command`, `integration`, `event` | Wejścia Bukkit, PlaceholderAPI oraz zdarzenia punktów i poziomu |

Wątek serwera obsługuje graczy, inventory i eventy. Pojedynczy worker wykonuje operacje JDBC na kopiach profili. PlaceholderAPI odczytuje opublikowane dane, więc zwykłe wywołanie placeholdera nie inicjuje operacji SQL.

## Weryfikacja wydania 1.3

- Build na JDK 25 i Gradle 9.8.2 uruchomił dziesięć testów automatycznych, w tym scenariusze harmonogramu Turbo i edycji zapisanego profilu.
- Na Paper 26.3 #255 sprawdzono edycję profilu po UUID gracza offline, eksport do YAML, historię administracyjną, nakładanie się Turbo z harmonogramu i boosta osobistego oraz zachowanie anulowania po `/adrop reload`.
- Próba z graczem sprawdziła drop z bazaltu, wyższy próg poziomu w regionie oraz anulowanie pojedynczej nagrody przez `MiningRewardEvent`.
- SQLite i MariaDB 11.8.8 z TLS `REQUIRED` przeszły zapis, odczyt tego samego profilu po ponownym uruchomieniu serwera i sprawdzenie rankingu. Paper załadował ze swoich bibliotek SQLite JDBC 3.49.1.0 i MySQL Connector/J 9.2.0.

## Weryfikacja wydania 1.2

- Build i siedem testów automatycznych przeszły na JDK 25 i Gradle 9.8.2.
- Na Paper 26.3 #255 sprawdzono start, inicjalizację SQLite, reload, Turbo i poprawne zatrzymanie.
- Na tym samym Paper sprawdzono istniejący profil SQLite, GUI, kopanie, zużycie kilofa, późniejsze anulowanie zdarzenia, przepełnienie ekwipunku i ponowne wejście. Test z graczem korzystał z klienta testowego przez ViaProxy.
- PlaceholderAPI 2.12.3 zwróciło dane gracza pod prefiksami `riftdrop` i `dropplugin`.
- Po zmianie pakowania na tym samym odchudzonym JAR-ze sprawdzono zapis, odczyt profilu po restarcie i ranking na SQLite oraz na MariaDB 11.8.8 z TLS `REQUIRED`. Paper załadował SQLite JDBC 3.49.1.0 i MySQL Connector/J 9.2.0 ze swoich bibliotek.

## Weryfikacja wydania 1.1

- Build na JDK 21 i Gradle 8.14.7 uruchomił siedem testów automatycznych, w tym migrację katalogu i odzyskiwanie zapisów SQLite.
- Gotowy JAR przeszedł start, inicjalizację SQLite, reload, aktywację Turbo i zatrzymanie na Paper 1.21, 1.21.1, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10 i 1.21.11.
- Testy z graczem na 1.21.4 i 1.21.11 objęły migrację profilu, GUI, kopanie, ochronę bloku, pełny ekwipunek, ponowne wejście i placeholdery pod oboma prefiksami.
- Warstwa zapisu z gotowego JAR-a przeszła próbę migracji tabeli, zapisu, ponownego połączenia i odczytu rankingu na MariaDB 11.8.8 z TLS `REQUIRED`.

## Migracja integracji Java

Deskryptor udostępnia alias zależności `DropPlugin`, aby serwer mógł rozpoznać starszą nazwę. Integracje Java importujące stare klasy wymagają aktualizacji importów i ponownej kompilacji. Zdarzenia są teraz w `pl.tenfajnybartek.riftdrop.event`.

### API nagrody

Zadeklaruj `depend: [riftDrop]` i dodaj artefakt 1.3 jako zależność kompilacji. Synchroniczny `MiningRewardEvent` jest publikowany po zaakceptowaniu łamania bloku oraz wylosowaniu nagrody, przed jej dostawą i naliczeniem związanych z nią punktów, XP i statystyk.

```java
@EventHandler(ignoreCancelled = true)
public void onMiningReward(MiningRewardEvent event) {
    UUID playerId = event.getPlayerId();
    String dropId = event.getDropId();
    int quantity = event.getAmount();
    // Użyj danych przy sprawdzaniu warunków integracji.
}
```

Źródło ma UUID świata, współrzędne i materiał. `getItemTemplate()` zwraca kopię przedmiotu, a rzeczywistą liczbę sztuk podaje `getAmount()`. `getRequiredLevel()` uwzględnia region. `setCancelled(true)` pomija tę nagrodę wraz z jej punktami, XP i statystyką. Podstawowy drop bloku i podstawowe XP są obsługiwane osobno. Gdy integracja ma liczyć faktycznie przyznane nagrody, powinna uwzględniać końcowy stan anulowania.

## Logo

[Plik PNG](assets/riftdrop-logo.png) powstał we wbudowanym narzędziu generowania obrazów. Założenie projektu to oryginalna grafitowa kostka skalna, turkusowa szczelina i niewielki ametyst na przezroczystym tle, bez napisu i elementów z gry.

<details>
<summary>Polecenie użyte do wygenerowania logo</summary>

> Logo-brand. A standalone original emblem for riftDrop, a Minecraft server mining plugin, for its GitHub README on dark and light themes. A compact angular, faceted stone cube with a single vivid teal luminous rift splitting it from upper right to lower left, with one small amethyst crystal visible inside. Three-quarter perspective, voxel-inspired geometry with premium clean illustrative finish. Graphite stone, turquoise slit, subtle violet accent. Strong simple silhouette recognizable at 48 px. Centered square composition with generous transparent padding. True transparent background. No text, no watermark, no game assets, no people, no drop shadow extending beyond the mark.

</details>
