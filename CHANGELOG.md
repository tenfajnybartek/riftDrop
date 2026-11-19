# Changelog

## riftDrop 1.3 — kopalnie, Turbo i zarządzanie profilami

**Wymagania** · Paper 26.3 i Java 25

**Plik do instalacji** · `riftDrop-1.3.jar`

### Co nowego

- **Źródła dropów i progi regionalne.** Każdy drop może mieć własną listę bloków źródłowych, współdzielone grupy materiałów lub oba rodzaje wpisów. Regiony mogą wymagać wyższego poziomu górnictwa do otrzymania dodatkowej nagrody.
- **Tygodniowy harmonogram Turbo.** TurboDrop i TurboExp można uruchamiać automatycznie w wybrane dni, o godzinie ustawionej w lokalnej strefie czasowej. Każde wydarzenie może mieć własną zapowiedź.
- **Zarządzanie Turbo.** `/adrop turbo` pokazuje aktywne boosty i kolejne terminy, pozwala ustawiać, przedłużać i anulować boosty oraz wyświetla historię zmian.
- **Profile offline i eksport.** `/adrop level` pozwala zmienić poziom i punkty zapisanego profilu po UUID, także gdy gracz jest offline. `/adrop profile export <gracz|UUID>` zapisuje profil do YAML, a `/adrop profile history <gracz|UUID>` pokazuje ostatnie operacje.
- **API nagród.** `MiningRewardEvent` pozwala innym pluginom odczytać wynik losowania i anulować pojedynczą nagrodę przed jej przyznaniem.

### Aktualizacja z 1.2

Zatrzymaj serwer, wykonaj kopię konfiguracji i bazy, a następnie podmień JAR. riftDrop odczyta dotychczasowe pliki YAML i profile. Źródła, regiony i harmonogram możesz dodać do istniejących plików konfiguracyjnych.

### Weryfikacja

Przeszło 10 testów automatycznych oraz próby na Paper 26.3 #255. Sprawdzono też zapis i odczyt profili po restarcie na SQLite i MariaDB.

[Konfiguracja](docs/configuration.md) · [Komendy](docs/commands.md) · [API](docs/development.md#api-nagrody) · [Weryfikacja](docs/development.md#weryfikacja-wydania-13)

## riftDrop 1.2 — Paper 26.3

**Wymagania** · Paper 26.3 i Java 25  
**Plik do instalacji** · `riftDrop-1.2.jar`

Wydanie 1.2 przenosi riftDrop na Paper 26.3. Po aktualizacji plugin korzysta z dotychczasowych tabel dropów, ustawień GUI i profili graczy.

### Zmiany

- Plugin kompiluje się pod Javę 25 i API Paper 26.3, zweryfikowane na stabilnym buildzie #255.
- Enchanty z `drops.yml` są odczytywane z rejestru Paper, również przy użyciu kluczy takich jak `minecraft:unbreaking`.
- Opcjonalną integrację sprawdzono z PlaceholderAPI 2.12.3. Dostępne są oba prefiksy, `%riftdrop_…%` i `%dropplugin_…%`.
- Zaktualizowano Gradle Wrapper do 9.8.2, Shadow do 9.6.1 i run-paper do 3.1.0. Zadanie `runServer` uruchamia Paper 26.3.
- Sterowniki SQLite i MySQL ładuje Paper. JAR riftDrop zawiera HikariCP i zajmuje około 0,32 MB.

### Weryfikacja

- Na Paper 26.3 #255 sprawdzono start, zatrzymanie, SQLite, `/adrop reload` i Turbo.
- Scenariusz z graczem objął GUI, kopanie, ochronę bloków, pełny ekwipunek i zachowanie postępu po ponownym wejściu.
- Przeszło siedem testów automatycznych oraz osobny test zapisu, ponownego połączenia i rankingu na MariaDB z TLS.

### Aktualizacja z 1.1

Zatrzymaj serwer, wykonaj kopię świata i bazy, przejdź na Paper 26.3 z Javą 25 i wymień JAR pluginu. riftDrop odczyta istniejącą konfigurację z `plugins/riftDrop/` i dane z dotychczasowego SQLite lub MySQL. Dla serwerów Paper 1.21.x użyj wydania 1.1. [Instrukcja aktualizacji](docs/installation.md#aktualizacja-z-riftdrop-11)

## riftDrop 1.1

- Nowa nazwa pluginu i artefakt `riftDrop-1.1.jar` dla Paper 1.21.x na Javie 21.
- Kod podzielony według odpowiedzialności za konfigurację, kopanie, GUI, profile, zapis i integracje. Układ menu jest walidowany i renderowany ze wspólnej definicji przycisków.
- Automatyczne kopiowanie konfiguracji i danych z `plugins/DropPlugin/` przy pierwszym uruchomieniu z pustym katalogiem docelowym.
- Nowe prefiksy `riftdrop` dla permisji i PlaceholderAPI, z obsługą starszych nazw.
- Nowe logo i [instrukcje instalacji, aktualizacji i konfiguracji](README.md#dokumentacja).
- Następny planowany cel zgodności to Paper 26.3 na Javie 25.

## DropPlugin 1.0.0 — pierwsze stabilne wydanie

**Platforma:** Paper 1.21.x • Java 21  
**Plik do instalacji:** `DropPlugin-1.0.0.jar`

DropPlugin dodaje konfigurowalne nagrody za kopanie kamienia i system poziomów górnictwa. Administrator ustala zasady dropu, a gracze mogą zmieniać swoje preferencje w menu `/drop`.

### Co zawiera wydanie

- **Konfigurowalne dropy** z kamienia, granitu, diorytu, andezytu i deepslate. Każdy wpis może mieć własną szansę, ilość, zakres wysokości Y, minimalny poziom gracza, punkty i XP.
- **Poziomy górnictwa** z odblokowywaniem kolejnych dropów. Komenda `/level` pokazuje postęp do następnego poziomu.
- **Menu `/drop`** z podglądem szans, wymagań i statystyk oraz przełącznikami dropów, podstawowych przedmiotów i powiadomień.
- **Bonusy z permisji, TurboDrop i TurboExp.** Administrator może włączyć Turbo dla całego serwera albo wybranego gracza. Pozostały czas wyświetla się w menu i na pasku akcji, jeśli jest włączony.
- **Zapis danych w SQLite lub MySQL.** SQLite działa domyślnie, a sterowniki obu baz są w paczce.
- **Opcjonalna integracja z PlaceholderAPI** z placeholderami poziomu, punktów i rankingu graczy.

### Stabilność i poprawki

- Profile zachowują postęp, statystyki i preferencje po ponownym wejściu i restarcie. Oczekujące zapisy można odzyskać po awarii bazy przy wyłączaniu serwera.
- Anulowane kopanie nie przyznaje nagród. Zużyciem narzędzia zarządza serwer, a nadwyżka przy pełnym ekwipunku wypada obok wykopanego bloku.
- Szansa wyświetlana w GUI korzysta z tych samych obliczeń co mechanika dropu. Fortune może zwiększyć ilość nagrody zgodnie z `drops.yml`.
- `/adrop reload` odświeża ustawienia, tabelę dropów i menu. Jeśli nowa konfiguracja jest błędna, plugin zachowuje poprzednie ustawienia.

### Instalacja i zgodność

Umieść JAR w folderze `plugins/` i uruchom serwer. Przy aktualizacji z wcześniejszej kompilacji zrób kopię bazy i folderu `plugins/DropPlugin`, ponieważ wydanie migruje zapisane dane i starszą konfigurację GUI.

Testy startu, SQLite, reloadu, Turbo i zatrzymania przeszły na wszystkich 11 wydaniach Paper 1.21.x dostępnych w katalogu Paper. Rozgrywkę z graczem i PlaceholderAPI sprawdzono dodatkowo na Paper 1.21.4 i 1.21.11.

### Następne wydanie

Kolejne wydanie planowane jest dla **Paper 26.3 na Javie 25**. Wydanie 1.0.0 jest przeznaczone dla linii **1.21.x**.
