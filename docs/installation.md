# Instalacja i utrzymanie

[Wróć do README](../README.md)

## Wymagania

| Element | riftDrop 1.1 |
| --- | --- |
| Serwer | Paper 1.21.x |
| Java | Java 21 |
| Baza | Wbudowany SQLite lub skonfigurowany MySQL |
| Integracje | PlaceholderAPI jest opcjonalne |

Serwer i wersja Javy muszą być zgodne. Planowany cel kolejnego wydania to Paper 26.3 na Javie 25. [Wymagania Paper](https://docs.papermc.io/paper/getting-started/)

## Nowa instalacja

1. Umieść `riftDrop-1.1.jar` w `plugins/`, a następnie uruchom serwer.
2. Zaczekaj, aż w logu pojawi się informacja o gotowej bazie `Baza danych gotowa (sqlite)`.
3. Przetestuj `/drop` i `/level`. Ustawienia znajdziesz w `plugins/riftDrop/`.

| Plik | Zawartość |
| --- | --- |
| `config.yml` | Baza danych, progresja, bonusy, powiadomienia, globalne Turbo i teksty poleceń |
| `drops.yml` | Tabela dropów oraz podstawowe XP za kopanie |
| `gui.yml` | Układ, ikony, nazwy i opisy menu |
| `database.db` | Profile w trybie SQLite. SQLite może tworzyć również pliki pomocnicze. |
| `pending-*.properties` | Zabezpieczone snapshoty oczekujące na ponowny zapis po problemie z bazą |

## Aktualizacja z DropPlugin 1.0.0

1. Zatrzymaj serwer. Zrób kopię `plugins/DropPlugin/`. Przy MySQL zrób też kopię bazy zawierającej tabelę `drop_users`.
2. Wyjmij dotychczasowy JAR DropPlugin z `plugins/` i wstaw `riftDrop-1.1.jar`.
3. Uruchom serwer. Jeśli `plugins/riftDrop/` nie istnieje lub jest pusty, riftDrop kopiuje konfigurację, SQLite z plikami pomocniczymi i oczekujące zapisy z `plugins/DropPlugin/`. Jeżeli nowy katalog już zawiera pliki, zostanie użyty jako źródło bieżącej konfiguracji i danych.
4. Zweryfikuj `/level`, osobiste ustawienia w `/drop` i istniejące integracje. Możesz stopniowo przenosić uprawnienia i placeholdery na prefiks `riftdrop`.

Starsze foldery mogą zawierać własne teksty w `config.yml`. Migracja kopiuje je w oryginalnej postaci. Tabeli MySQL i nazwy pliku SQLite po rebrandingu używa się tak samo.

### Powrót do 1.0.0

Zatrzymaj serwer przed wymianą artefaktu. Przy SQLite stary JAR korzysta z `plugins/DropPlugin/`, czyli z kopii sprzed aktualizacji. Postęp zapisany później w katalogu `riftDrop/` wymaga osobnej migracji, jeśli chcesz go zachować. Przy MySQL oba wydania korzystają z tej samej tabeli i formatu danych. Kopia bazy pozwala przywrócić stan z momentu aktualizacji.

## Diagnostyka

| Objaw | Co sprawdzić |
| --- | --- |
| Komenda zwraca brak danych | Sprawdź, czy baza jest gotowa, a użytkownik może się z nią połączyć. Profil ładuje się po UUID. |
| Plugin przestaje działać po zmianie YAML | W logu sprawdź plik i klucz wskazany przy błędzie. Popraw konfigurację i ponów `/adrop reload` lub uruchomienie. |
| Puste pozycje rankingu | Sprawdź wynik odświeżania bazy. Ranking działa na zapisanych profilach, a pozycje obejmują zakres 1–100. |
| Zmiana danych MySQL niczego nie zmieniła | Uruchom serwer ponownie. `/adrop reload` nie przełącza aktywnej puli. |
| Po aktualizacji wczytał się pusty profil | Sprawdź katalog `plugins/riftDrop/`, typ bazy i UUID gracza. Zmiana trybu uwierzytelniania lub konfiguracji proxy może zmienić UUID. |
