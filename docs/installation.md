# Instalacja i utrzymanie

[Wróć do README](../README.md)

## Wymagania

| Element | riftDrop 1.2 |
| --- | --- |
| Serwer | Paper 26.3 |
| Java | Java 25 |
| Baza | Wbudowany SQLite lub skonfigurowany MySQL |
| Integracje | PlaceholderAPI 2.12.3 jest opcjonalne |

Wersja 1.1 pozostaje wydaniem dla Paper 1.21.x na Javie 21. [Wymagania Paper](https://docs.papermc.io/paper/getting-started/)

## Nowa instalacja

1. Umieść `riftDrop-1.2.jar` w `plugins/`, a następnie uruchom serwer.
2. Zaczekaj, aż w logu pojawi się informacja o gotowej bazie `Baza danych gotowa (sqlite)`.
3. Przetestuj `/drop` i `/level`. Ustawienia znajdziesz w `plugins/riftDrop/`.

| Plik | Zawartość |
| --- | --- |
| `config.yml` | Baza danych, progresja, bonusy, powiadomienia, globalne Turbo i teksty poleceń |
| `drops.yml` | Tabela dropów oraz podstawowe XP za kopanie |
| `gui.yml` | Układ, ikony, nazwy i opisy menu |
| `database.db` | Profile w trybie SQLite. SQLite może tworzyć również pliki pomocnicze. |
| `pending-*.properties` | Zabezpieczone snapshoty oczekujące na ponowny zapis po problemie z bazą |

## Aktualizacja z riftDrop 1.1

1. Zatrzymaj poprzedni serwer. Zrób kopię świata i całego katalogu serwera oraz, przy MySQL, osobną kopię bazy. Kopia pozwala odtworzyć poprzednie wydanie również po konwersji świata przez Paper 26.3.
2. Ustaw Javę 25 w skrypcie startowym lub panelu i podmień JAR serwera na Paper 26.3.
3. Wymień plik pluginu na `riftDrop-1.2.jar`. Profile i konfiguracja są odczytywane z istniejącego katalogu `plugins/riftDrop/`. Przy MySQL zostaje użyta dotychczasowa tabela `drop_users`.
4. Uruchom serwer i sprawdź gotowość bazy, `/level`, `/drop` oraz inne pluginy, których używasz na serwerze.

## Aktualizacja z DropPlugin 1.0.0

1. Zatrzymaj serwer. Zrób kopię `plugins/DropPlugin/`. Przy MySQL zrób też kopię bazy zawierającej tabelę `drop_users`.
2. Wykonaj kroki aktualizacji Paper i Javy opisane powyżej. Wyjmij dotychczasowy JAR DropPlugin z `plugins/` i wstaw `riftDrop-1.2.jar`.
3. Uruchom serwer. Jeśli `plugins/riftDrop/` nie istnieje lub jest pusty, riftDrop kopiuje konfigurację, SQLite z plikami pomocniczymi i oczekujące zapisy z `plugins/DropPlugin/`. Jeżeli nowy katalog już zawiera pliki, zostanie użyty jako źródło bieżącej konfiguracji i danych.
4. Zweryfikuj `/level`, osobiste ustawienia w `/drop` i istniejące integracje. Możesz stopniowo przenosić uprawnienia i placeholdery na prefiks `riftdrop`.

Starsze foldery mogą zawierać własne teksty w `config.yml`. Migracja kopiuje je w oryginalnej postaci. Tabeli MySQL i nazwy pliku SQLite po rebrandingu używa się tak samo.

### Powrót do poprzedniego serwera

Zatrzymaj serwer i przywróć kopię świata, konfiguracji oraz wersje Paper, Javy i pluginów sprzed aktualizacji. Przywrócenie kopii bazy odtwarza profile z chwili wykonania kopii. Jeżeli wracasz do DropPlugin 1.0.0 na SQLite, ten JAR czyta katalog `plugins/DropPlugin/`.

## Diagnostyka

| Objaw | Co sprawdzić |
| --- | --- |
| Komenda zwraca brak danych | Sprawdź, czy baza jest gotowa, a użytkownik może się z nią połączyć. Profil ładuje się po UUID. |
| Plugin przestaje działać po zmianie YAML | W logu sprawdź plik i klucz wskazany przy błędzie. Popraw konfigurację i ponów `/adrop reload` lub uruchomienie. |
| Puste pozycje rankingu | Sprawdź wynik odświeżania bazy. Ranking działa na zapisanych profilach, a pozycje obejmują zakres 1–100. |
| Zmiana danych MySQL niczego nie zmieniła | Uruchom serwer ponownie. `/adrop reload` nie przełącza aktywnej puli. |
| Po aktualizacji wczytał się pusty profil | Sprawdź katalog `plugins/riftDrop/`, typ bazy i UUID gracza. Zmiana trybu uwierzytelniania lub konfiguracji proxy może zmienić UUID. |
