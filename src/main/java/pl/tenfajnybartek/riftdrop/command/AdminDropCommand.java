package pl.tenfajnybartek.riftdrop.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import pl.tenfajnybartek.riftdrop.RiftDropPlugin;
import pl.tenfajnybartek.riftdrop.boost.BoostType;
import pl.tenfajnybartek.riftdrop.profile.MiningProfile;
import pl.tenfajnybartek.riftdrop.profile.MiningProfile.Profile;
import pl.tenfajnybartek.riftdrop.storage.ProfileExport;
import pl.tenfajnybartek.riftdrop.text.BoostTime;
import pl.tenfajnybartek.riftdrop.text.Messages;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.logging.Level;

public final class AdminDropCommand implements TabExecutor {
    private final RiftDropPlugin plugin;
    private final AdminHistory history;

    public AdminDropCommand(RiftDropPlugin plugin, AdminHistory history) {
        this.plugin = plugin;
        this.history = history;
        var command = Objects.requireNonNull(plugin.getCommand("adrop"));
        command.setExecutor(this);
        command.setTabCompleter(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!Permissions.has(sender, "riftdrop.cmd.adrop")) {
            Messages.sendMessage(sender, plugin.configuration().getCmdADropNoPermission());
            return true;
        }
        try {
            switch (args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT)) {
                case "reload" -> reload(sender);
                case "level" -> level(sender, args);
                case "drop", "exp" -> {
                    requireLength(args, 3, "/adrop <drop|exp> <all|gracz|UUID> <czas>");
                    boost(sender, "set", BoostType.parse(args[0]), args[1], args[2]);
                }
                case "turbo" -> turbo(sender, args);
                case "profile" -> profile(sender, args);
                default -> help(sender);
            }
        } catch (IllegalArgumentException failure) {
            Messages.sendMessage(sender, "&c" + failure.getMessage());
        }
        return true;
    }

    private void help(CommandSender sender) {
        plugin.configuration().getCmdADropUsage().forEach(line -> Messages.sendMessage(sender, line));
        Messages.sendMessage(sender, "&7/adrop turbo <status|set|cancel|extend|history>");
        Messages.sendMessage(sender, "&7/adrop profile <export|history> <gracz|UUID>");
    }

    private void reload(CommandSender sender) {
        var config = plugin.configuration();
        long start = System.nanoTime();
        Messages.sendMessage(sender, config.getCmdADropReloading());
        try {
            plugin.reloadSettings();
            Messages.sendMessage(sender, plugin.configuration().getCmdADropReloaded()
                    .replace("{TIME}", String.format(Locale.ROOT, "%.3f", (System.nanoTime() - start) / 1e9)));
        } catch (Exception failure) {
            Messages.sendMessage(sender, config.getCmdADropReloadError());
            plugin.getLogger().log(Level.WARNING, "Przeładowanie konfiguracji nie powiodło się.", failure);
        }
    }

    private void level(CommandSender sender, String[] args) {
        if (args.length < 3 || args.length > 4) throw usage("/adrop level <gracz|UUID> <poziom> [punkty]");
        var config = plugin.configuration();
        int level = integer(args[2], 1, config.getMaxLevel(), "Poziom");
        int points = args.length == 4 ? integer(args[3], 0, Math.max(0, MiningProfile.pointsRequired(level, config) - 1), "Punkty") : 0;
        UUID id = target(sender, args[1]);
        edit(sender, id, "profile.level", before -> before.withProgress(level, points),
                (before, after) -> before.level() + "/" + before.points() + " -> " + after.level() + "/" + after.points(),
                after -> Messages.sendMessage(sender, "&aZapisano poziom " + after.level() + " i " + after.points()
                        + " pkt dla " + displayName(after) + "."));
    }

    private void turbo(CommandSender sender, String[] args) {
        if (args.length < 2) throw usage("/adrop turbo <status|set|cancel|extend|history>");
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "status" -> {
                if (args.length > 3) throw usage("/adrop turbo status [all|gracz|UUID]");
                if (args.length == 2 || args[2].equalsIgnoreCase("all")) showStatus(sender, null);
                else inspect(sender, target(sender, args[2]), profile -> showStatus(sender, profile));
            }
            case "history" -> {
                if (args.length > 3) throw usage("/adrop turbo history [all|gracz|UUID]");
                String id = args.length < 3 ? null : args[2].equalsIgnoreCase("all") ? "all" : target(sender, args[2]).toString();
                showHistory(sender, id, "turbo.");
            }
            case "set", "extend" -> {
                requireLength(args, 5, "/adrop turbo <set|extend> <drop|exp> <all|gracz|UUID> <czas>");
                boost(sender, args[1].toLowerCase(Locale.ROOT), type(args[2]), args[3], args[4]);
            }
            case "cancel" -> {
                requireLength(args, 4, "/adrop turbo cancel <drop|exp> <all|gracz|UUID>");
                boost(sender, "cancel", type(args[2]), args[3], "");
            }
            default -> throw usage("/adrop turbo <status|set|cancel|extend|history>");
        }
    }

    private void boost(CommandSender sender, String action, BoostType type, String selector, String time) {
        long now = System.currentTimeMillis();
        long requested = action.equals("cancel") ? 0 : BoostTime.parseDateDiff(time, true);
        if (!action.equals("cancel") && requested <= now) {
            throw new IllegalArgumentException("Niepoprawny czas: " + time);
        }
        String operation = "turbo." + type.key() + "." + action;
        if (selector.equalsIgnoreCase("all")) {
            var config = plugin.configuration();
            long before = plugin.boosts().globalExpiry(type);
            long expiry = expiry(action, before, now, requested);
            try {
                config.saveTurbo(type, expiry, action.equals("cancel") ? now : 0);
            } catch (Exception failure) {
                reportFailure(sender, failure);
                return;
            }
            history.record(actor(sender), "all", operation, timestamp(before) + " -> " + timestamp(expiry));
            if (action.equals("set")) {
                String broadcast = config.getTurboMessage().replace("{ADMIN}", sender.getName())
                        .replace("{TYPE}", type.label()).replace("{TIME}", time);
                Bukkit.getOnlinePlayers().forEach(player -> Messages.sendMessage(player, broadcast));
                Messages.sendMessage(sender, config.getCmdADropTurboEnabled()
                        .replace("{TYPE}", type.label()).replace("{TIME}", time));
            } else {
                Messages.sendMessage(sender, "&a" + type.label() + " globalnie: " + timestamp(expiry));
            }
            return;
        }
        UUID id = target(sender, selector);
        edit(sender, id, operation,
                before -> before.withBoost(type == BoostType.DROP, expiry(action, personal(type, before), now, requested)),
                (before, after) -> timestamp(personal(type, before)) + " -> " + timestamp(personal(type, after)),
                after -> {
                    Player player = Bukkit.getPlayer(id);
                    if (player != null && action.equals("set")) {
                        Messages.sendMessage(player, plugin.configuration().getTurboGetMessage()
                                .replace("{TYPE}", type.label()).replace("{TIME}", time));
                    }
                    Messages.sendMessage(sender, "&a" + type.label() + " dla " + displayName(after) + ": "
                            + timestamp(personal(type, after)));
                });
    }

    private static long expiry(String action, long before, long now, long requested) {
        return action.equals("extend") ? Math.max(before, now) + (requested - now) : requested;
    }

    private void showStatus(CommandSender sender, Profile profile) {
        long now = System.currentTimeMillis();
        if (profile != null) Messages.sendMessage(sender, "&6Profil: &f" + displayName(profile));
        for (BoostType type : BoostType.values()) {
            long manual = plugin.boosts().manualExpiry(type);
            long scheduled = plugin.boosts().scheduledExpiry(type);
            long personal = profile == null ? 0 : personal(type, profile);
            long until = Math.max(personal, Math.max(manual, scheduled));
            Messages.sendMessage(sender, "&6" + type.label() + " &7" + (until > now ? "×2 do " + timestamp(until) : "nieaktywne"));
            Messages.sendMessage(sender, "&7Ręczne: " + timestamp(manual) + " | Harmonogram: " + timestamp(scheduled)
                    + (profile == null ? "" : " | Osobiste: " + timestamp(personal))
                    + " | Następny start: " + plugin.boosts().nextStart(type));
        }
    }

    private void profile(CommandSender sender, String[] args) {
        requireLength(args, 3, "/adrop profile <export|history> <gracz|UUID>");
        UUID id = target(sender, args[2]);
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "history" -> showHistory(sender, id.toString(), "");
            case "export" -> {
                String admin = actor(sender);
                var folder = plugin.getDataFolder().toPath();
                var result = plugin.profiles().inspect(id).thenApplyAsync(profile -> {
                    if (profile == null) return null;
                    try {
                        String path = ProfileExport.write(folder, profile);
                        history.record(admin, id.toString(), "profile.export", path);
                        return path;
                    } catch (IOException failure) {
                        throw new CompletionException(failure);
                    }
                });
                watch(result, (path, failure) -> {
                    if (failure != null) reportFailure(sender, failure);
                    else if (path == null) missing(sender);
                    else Messages.sendMessage(sender, "&aEksport zapisany w plugins/riftDrop/" + path);
                });
            }
            default -> throw usage("/adrop profile <export|history> <gracz|UUID>");
        }
    }

    private void edit(CommandSender sender, UUID id, String operation, UnaryOperator<Profile> change,
                      java.util.function.BiFunction<Profile, Profile, String> describe, Consumer<Profile> success) {
        if (!plugin.profiles().beginAdminEdit(id)) {
            Messages.sendMessage(sender, "&cTrwa już edycja tego profilu.");
            return;
        }
        String admin = actor(sender);
        var future = plugin.database().updateExisting(id, change,
                (before, after) -> history.record(admin, id.toString(), operation, describe.apply(before, after)));
        watch(future, (profile, failure) -> {
            plugin.profiles().finishAdminEdit(id, failure == null && profile != null);
            if (failure != null) reportFailure(sender, failure);
            else if (profile == null) missing(sender);
            else success.accept(profile);
        });
    }

    private void inspect(CommandSender sender, UUID id, Consumer<Profile> success) {
        watch(plugin.profiles().inspect(id), (profile, failure) -> {
            if (failure != null) reportFailure(sender, failure);
            else if (profile == null) missing(sender);
            else success.accept(profile);
        });
    }

    private void showHistory(CommandSender sender, String target, String prefix) {
        var result = CompletableFuture.supplyAsync(() -> {
            try {
                return history.recent(target, prefix, 10);
            } catch (IOException failure) {
                throw new CompletionException(failure);
            }
        });
        watch(result, (rows, failure) -> {
            if (failure != null) reportFailure(sender, failure);
            else if (rows.isEmpty()) Messages.sendMessage(sender, "&7Brak wpisów historii.");
            else rows.forEach(sender::sendMessage);
        });
    }

    private <T> void watch(CompletableFuture<T> future, BiConsumer<T, Throwable> result) {
        new BukkitRunnable() {
            @Override public void run() {
                if (!future.isDone()) return;
                cancel();
                T value;
                try {
                    value = future.join();
                } catch (CompletionException failure) {
                    result.accept(null, failure.getCause());
                    return;
                }
                result.accept(value, null);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void reportFailure(CommandSender sender, Throwable failure) {
        Messages.sendMessage(sender, "&cOperacja nie powiodła się. Sprawdź log serwera.");
        plugin.getLogger().log(Level.WARNING, "Błąd operacji administracyjnej riftDrop.", failure);
    }

    private static void missing(CommandSender sender) {
        Messages.sendMessage(sender, "&cNie znaleziono zapisanego profilu dla tego UUID.");
    }

    private static UUID target(CommandSender sender, String value) {
        try {
            UUID id = UUID.fromString(value);
            if (id.toString().equalsIgnoreCase(value)) return id;
        } catch (IllegalArgumentException ignored) {
            // An exact online player name is also accepted.
        }
        Player player = Bukkit.getPlayerExact(value);
        if (player == null || sender instanceof Player viewer && !viewer.canSee(player)) {
            throw new IllegalArgumentException("Podaj UUID lub dokładną nazwę gracza online.");
        }
        return player.getUniqueId();
    }

    private static int integer(String raw, int min, int max, String label) {
        try {
            int value = Integer.parseInt(raw);
            if (value >= min && value <= max) return value;
        } catch (NumberFormatException ignored) {}
        throw new IllegalArgumentException(label + " musi mieścić się w zakresie " + min + "–" + max + ".");
    }

    private static long personal(BoostType type, Profile profile) {
        return type == BoostType.DROP ? profile.turboDrop() : profile.turboExp();
    }

    private static BoostType type(String name) {
        try {
            return BoostType.parse(name);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("Wybierz typ drop lub exp.");
        }
    }

    private static String timestamp(long expiry) {
        return expiry > System.currentTimeMillis() ? java.time.Instant.ofEpochMilli(expiry).toString() : "brak";
    }

    private static String displayName(Profile value) {
        return value.name() == null ? value.id().toString() : value.name();
    }

    private static String actor(CommandSender sender) {
        return sender instanceof Player player ? player.getName() + " (" + player.getUniqueId() + ")" : sender.getName();
    }

    private static void requireLength(String[] args, int length, String command) {
        if (args.length != length) throw usage(command);
    }

    private static IllegalArgumentException usage(String command) {
        return new IllegalArgumentException("Użycie: " + command);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!Permissions.has(sender, "riftdrop.cmd.adrop") || args.length == 0) return List.of();
        List<String> options = new ArrayList<>();
        String commandName = args[0].toLowerCase(Locale.ROOT);
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (args.length == 1) options.addAll(List.of("reload", "drop", "exp", "level", "turbo", "profile"));
        else if (args.length == 2 && commandName.equals("turbo")) options.addAll(List.of("status", "set", "extend", "cancel", "history"));
        else if (args.length == 2 && commandName.equals("profile")) options.addAll(List.of("export", "history"));
        else if (args.length == 3 && commandName.equals("turbo") && Set.of("set", "extend", "cancel").contains(action)) {
            options.addAll(List.of("drop", "exp"));
        } else if ((args.length == 2 && Set.of("drop", "exp", "level").contains(commandName))
                || (commandName.equals("turbo") && ((args.length == 3 && Set.of("status", "history").contains(action))
                || (args.length == 4 && Set.of("set", "extend", "cancel").contains(action))))
                || (args.length == 3 && commandName.equals("profile"))) {
            if (Set.of("drop", "exp", "turbo").contains(commandName)) options.add("all");
            Bukkit.getOnlinePlayers().forEach(player -> {
                if (!(sender instanceof Player viewer) || viewer.canSee(player)) options.add(player.getName());
            });
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }
}
