# Budowanie i architektura

[Wróć do README](../README.md)

## Build na JDK 21

Ustaw `JAVA_HOME` na JDK 21. Repozytorium zawiera Gradle Wrapper.

```powershell
.\gradlew.bat clean build
.\gradlew.bat runServer -PminecraftVersion=1.21.11
```

W systemach Linux i macOS użyj `./gradlew`. Zadanie `build` testuje kod i składa `build/libs/riftDrop-1.1.jar`. Biblioteki JDBC są dołączone do JAR-a, a Paper API i PlaceholderAPI są zależnościami kompilacji. Kod produkcyjny kompiluje się z `--release 21` przeciw Paper API 1.21.

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

## Weryfikacja wydania 1.1

- Build na JDK 21 i Gradle 8.14.7 uruchomił siedem testów automatycznych, w tym migrację katalogu i odzyskiwanie zapisów SQLite.
- Gotowy JAR przeszedł start, inicjalizację SQLite, reload, aktywację Turbo i zatrzymanie na Paper 1.21, 1.21.1, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10 i 1.21.11.
- Testy z graczem na 1.21.4 i 1.21.11 objęły migrację profilu, GUI, kopanie, ochronę bloku, pełny ekwipunek, ponowne wejście i placeholdery pod oboma prefiksami.
- Warstwa zapisu z gotowego JAR-a przeszła próbę migracji tabeli, zapisu, ponownego połączenia i odczytu rankingu na MariaDB 11.8.8 z TLS `REQUIRED`.

## Migracja integracji Java

Deskryptor udostępnia alias zależności `DropPlugin`, aby serwer mógł rozpoznać starszą nazwę. Integracje Java importujące stare klasy wymagają aktualizacji importów i ponownej kompilacji. Zdarzenia są teraz w `pl.tenfajnybartek.riftdrop.event`.

## Logo

[Plik PNG](assets/riftdrop-logo.png) powstał we wbudowanym narzędziu generowania obrazów. Założenie projektu to oryginalna grafitowa kostka skalna, turkusowa szczelina i niewielki ametyst na przezroczystym tle, bez napisu i elementów z gry.

<details>
<summary>Polecenie użyte do wygenerowania logo</summary>

> Logo-brand. A standalone original emblem for riftDrop, a Minecraft server mining plugin, for its GitHub README on dark and light themes. A compact angular, faceted stone cube with a single vivid teal luminous rift splitting it from upper right to lower left, with one small amethyst crystal visible inside. Three-quarter perspective, voxel-inspired geometry with premium clean illustrative finish. Graphite stone, turquoise slit, subtle violet accent. Strong simple silhouette recognizable at 48 px. Centered square composition with generous transparent padding. True transparent background. No text, no watermark, no game assets, no people, no drop shadow extending beyond the mark.

</details>
