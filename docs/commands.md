# Komendy i integracje

[Wróć do README](../README.md) · [Konfiguracja](configuration.md)

## Polecenia

| Polecenie | Działanie | Permisja |
| --- | --- | --- |
| `/drop` | Otwiera menu dropów. Aliasy `/stone` i `/kamien`. | `riftdrop.cmd.drop` |
| `/level` | Pokazuje Twój poziom i punkty. Aliasy `/lvl` i `/poziom`. | `riftdrop.cmd.level` |
| `/level <gracz>` | Pokazuje postęp wskazanego gracza online. | `riftdrop.cmd.level` i `riftdrop.cmd.alevel` |
| `/adrop reload` | Wczytuje ponownie ustawienia, dropy i GUI. Alias komendy `/admindrop`. | `riftdrop.cmd.adrop` |
| `/adrop drop <all\|gracz> <czas>` | Włącza TurboDrop globalnie albo dla gracza online. | `riftdrop.cmd.adrop` |
| `/adrop exp <all\|gracz> <czas>` | Włącza TurboExp globalnie albo dla gracza online. | `riftdrop.cmd.adrop` |
| `/adrop level <gracz> <poziom> [punkty]` | Ustawia postęp gracza online. Bez ostatniego argumentu ustawia zero punktów. | `riftdrop.cmd.adrop` |

Domyślnie wszyscy gracze mają dostęp do swoich dropów i poziomu. Polecenia administracyjne i odczyt poziomu innego gracza są dostępne dla operatorów. Jeśli nowy węzeł `riftdrop.*` nie został ustawiony, plugin sprawdza odpowiadający mu węzeł `dropplugin.*`. Ta reguła obejmuje także bonusy skonfigurowane pod nowym prefiksem.

Jeśli konfiguracja innego pluginu wywołuje komendę z pełną nazwą, zmień `dropplugin:drop` na `riftdrop:drop` i analogicznie zmień prefiks dla `level` i `adrop`.

Czas Turbo można podać np. jako `30m`, `2h30m15s` albo `1d2h`. Jednostki to `y`, `mo`, `w`, `d`, `h`, `m` i `s`. Ponowne użycie komendy zastępuje dotychczasowy czas danego boostu dla wskazanego celu.

```text
/adrop drop all 2h
/adrop exp Alex 30m
/adrop level Alex 15 250
```

## PlaceholderAPI

Zainstaluj PlaceholderAPI i uruchom serwer z riftDrop. Możesz zachować stare placeholdery z prefiksem `%dropplugin_…%` albo użyć nowego.

| Placeholder | Zwraca |
| --- | --- |
| `%riftdrop_level%` | Aktualny poziom górnictwa |
| `%riftdrop_points%` | Aktualne punkty na tym poziomie |
| `%riftdrop_points_required%` | Próg następnego poziomu |
| `%riftdrop_points_to_next%` | Punkty brakujące do awansu |
| `%riftdrop_toplevel-1%` | Wpis na wybranej pozycji rankingu, od 1 do 100 |

Ranking sortuje zapisane profile według poziomu i punktów. Przy remisie rozstrzyga UUID. Wartości pochodzą z odświeżanego snapshotu, dlatego zmiana może pojawić się po następnym zapisie. Wpis starego profilu bez zapamiętanej nazwy może wyświetlać UUID do kolejnego wejścia gracza.
