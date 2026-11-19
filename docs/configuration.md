# Konfiguracja

[Wróć do README](../README.md) · [Komendy i permisje](commands.md)

Domyślne pliki zawierają przykłady i teksty gotowe dla polskiego serwera. Zmiany mechaniki oraz wyglądu wczytasz przez `/adrop reload`. Działające ustawienia są publikowane po pomyślnym wczytaniu całego zestawu.

- [Domyślny config.yml](../src/main/resources/config.yml)
- [Domyślny drops.yml](../src/main/resources/drops.yml)
- [Domyślny gui.yml](../src/main/resources/gui.yml)

## config.yml

| Sekcja | Ustawienia |
| --- | --- |
| `database` | `type` wybiera `sqlite` lub `mysql`. Przy MySQL uzupełnij `host`, `port`, `base`, `user`, `password` i `sslMode`. Parametry puli mają jednostki podane w nazwach kluczy. |
| `settings.lvling` | `status` steruje zdobywaniem punktów. `pointsToLvlup` określa mnożnik progu. `maxLevel` ogranicza progresję, a `chatLevels` steruje komunikatami publicznymi. |
| `settings.toinv` | Przy `status: true` nagrody trafiają do ekwipunku, a nadwyżka wypada przy bloku. Przy `false` wszystkie przedmioty wypadają przy bloku. `message-status` i `messageFullInv` sterują powiadomieniem o nadwyżce. |
| `settings.actionbar` | `status` włącza pasek Turbo. `message` i `exp-message` to szablony z tokenem `{TIME}`. |
| `settings.chances` | Bonusy permisji podane w punktach procentowych. Bonusy z kilku pasujących uprawnień sumują się. |
| `messages` | Teksty nagród, komend i Turbo. Obsługuje kody kolorów `&a` i `&#RRGGBB`. |
| `placeholders` | `toplevel-format` używa tokenów `{NAME}` i `{LEVEL}`. `toplevel-no-player` zastępuje pustą pozycję. |
| `turbos` | Znaczniki czasu wygaśnięcia globalnych boostów. Obsługuj je komendą `/adrop`. |
| `turbo-schedule` | Powtarzalne zdarzenia według dnia tygodnia i godziny lokalnej. |

Do zdalnego MySQL możesz użyć `sslMode: VERIFY_IDENTITY`. Certyfikat serwera musi pochodzić z zaufanego źródła i pasować do adresu ustawionego w `host`. [Tryby TLS Connector/J](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-security.html)

Przykład bonusu `+0,5` punktu procentowego:

```yaml
settings:
  chances:
    vip:
      permission: riftdrop.vip
      additionalchance: 0.5
```

Próg następnego poziomu to bieżący poziom × `pointsToLvlup`. Zgromadzona nadwyżka przechodzi na następny poziom i może wystarczyć na kilka awansów. Na maksymalnym poziomie brakujący postęp wynosi zero.

W komunikacie `messages.drop` dostępne są `{DROP}`, `{AMOUNT}`, `{PKT}` i `{EXP}`. Teksty Turbo mogą używać `{TYPE}` i `{TIME}`, a globalne ogłoszenie obsługuje też `{ADMIN}`. Szablony poziomów i komend najlepiej edytować na podstawie tokenów obecnych w domyślnym pliku.

## drops.yml

```yaml
drops:
  diamond:
    name: "Diament"
    item: "material:minecraft:diamond"
    chance: 0.5
    amount: 1-2
    height: -64-16
    points: 8-15
    exp: 10
    fortune: true
    neededlvl: 15
exps:
  stone: 1
  obsidian: 5
```

| Pole | Znaczenie |
| --- | --- |
| Klucz wpisu, np. `diamond` | Stałe ID statystyk i ustawień. Etykietę można zmienić przez `name`. |
| `item` | Obowiązkowy materiał. Opcjonalne są `name:`, `lore:`, `enchants:` i `durability:`. W nazwie i lore `_` oznacza spację, a `@nl` rozdziela linie lub enchanty. Zapis enchantu to np. `enchants:minecraft:unbreaking;2`. |
| `chance` | Procent bazowy od 0 do 100. Wartość `0.5` oznacza 0,5%. |
| `amount`, `points` | Wartość pojedyncza albo zakres z włączonymi końcami. Punkty i XP są naliczane za sztukę. |
| `height` | Wysokość łamanego bloku. Wartości ujemne działają również po obu stronach, np. `-64--16`. |
| `exp` | Dodatkowe XP Minecrafta na sztukę, niezależne od punktów górnictwa. |
| `fortune` | Czy Fortune może zwiększyć liczbę sztuk. |
| `neededlvl` | Najniższy poziom górnictwa, na którym drop jest dostępny. |

Szansa końcowa to suma bazy i bonusów z permisji, podwojona przy aktywnym TurboDrop i ograniczona do 100%. Każdy kwalifikujący się wpis ma osobne losowanie. TurboExp podwaja XP Minecrafta. Boost globalny i osobisty tego samego typu nie mnożą się przez siebie.

Po udanym losowaniu Fortune I daje 33% szansy na dodatkową sztukę, Fortune II ma 50% szansy na dodatkowe 1–2 sztuki, a Fortune III lub wyższe ma 60% szansy na dodatkowe 1–3 sztuki. Dotyczy wpisów z `fortune: true`.

Dodatkowe dropy działają w Survival przy użyciu odpowiedniego narzędzia. Wpis bez `sources` działa na kamieniu, granicie, diorycie, andezycie i deepslate. `exps.stone` ustala podstawowe XP za te materiały, a `exps.obsidian` dotyczy obsydianu.

### Źródła i regiony

Grupy zawierają konkretne materiały. W `sources` można łączyć materiał i odwołanie do grupy zaczynające się od `@`.

```yaml
source-groups:
  basalt-mine: [basalt, polished_basalt]
regions:
  glebinowa:
    world: world
    from: [-20, -64, -20]
    to: [20, 0, 20]
    minimum-level: 25
drops:
  diamond:
    item: "material:diamond"
    chance: 2
    height: -64-32
    neededlvl: 15
    sources: ["@basalt-mine", deepslate]
```

Granice regionu są włączne. Dla punktu wewnątrz kilku regionów obowiązuje najwyższy próg razem z `neededlvl` danego dropu. Próg zmienia uprawnienie do dodatkowej nagrody. Podstawowe łamanie bloku nadal zależy od mechaniki serwera i innych pluginów.

## Weekendowe Turbo

```yaml
turbo-schedule:
  enabled: true
  zone: Europe/Warsaw
  events:
    weekend-drop:
      type: drop
      days: [SATURDAY, SUNDAY]
      time: "18:00"
      duration-minutes: 120
      announce-minutes: 10
      message: "&6{TYPE} rozpocznie się o {START} i potrwa {MINUTES} min."
```

`type` to `drop` lub `exp`, `days` to angielskie nazwy dni tygodnia, a `time` jest w formacie `HH:mm`. Zapowiedź może używać `{TYPE}`, `{START}`, `{MINUTES}` i `{ID}`. Wartość 0 wysyła ją o starcie. Po restarcie plugin uwzględnia już trwające zdarzenie. Zapowiedź pojawia się po osiągnięciu jej terminu podczas działania serwera.

## gui.yml

Sekcja `gui` określa tytuł i rozmiar ekwipunku. Rozmiar musi być wielokrotnością 9 w zakresie 9–54. Każdy aktywny przycisk potrzebuje własnego slotu liczonego od zera. Pozostałe sloty mieszczą wpisy z `drops.yml`.

Przyciski mają ustawienia `status`, `slot`, `item`, `name` i listę `lores`. Przycisk poziomu używa klucza `lore`. Sekcja `filler` określa tło pustych slotów, a `item.enchanted` steruje poświatą włączonych dropów.

| Element | Tokeny dostępne w opisie lub nazwie |
| --- | --- |
| `item` | `{NAME}`, `{CHANCE}`, `{CHANCE-BONUS}`, `{CHANCE-SUM}`, `{HEIGHT-MIN}`, `{HEIGHT-MAX}`, `{AMOUNT-MIN}`, `{AMOUNT-MAX}`, `{POINTS-MIN}`, `{POINTS-MAX}`, `{EXP}`, `{FORTUNE}`, `{STATUS}`, `{MINED}` |
| `locked` | `{DROP-NAME}`, `{NEEDED-LEVEL}`, `{PLAYER-LEVEL}` |
| `cobble`, `messages` | `{STATUS}` |
| `level` | `{LVL}`, `{PKT}`, `{POINTS-TO-LEVEL}`, `{POINTS-REQUIRED}` |
| `turbos` | `{DROP-STATUS}`, `{DROP-EXPIRE}`, `{EXP-STATUS}`, `{EXP-EXPIRE}` oraz odpowiedniki z prefiksem `ME-` dla gracza |
