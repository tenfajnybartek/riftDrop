package pl.tenfajnybartek.dropplugin.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import pl.tenfajnybartek.dropplugin.base.DropPlugin;
import pl.tenfajnybartek.dropplugin.managers.UserManager;
import pl.tenfajnybartek.dropplugin.objects.User;
import pl.tenfajnybartek.dropplugin.utils.ChatUtils;
import pl.tenfajnybartek.dropplugin.utils.DataUtils;

public class ADropCommand implements TabExecutor {

    private static final int MIN_LEVEL = 1;
    private static final int MIN_POINTS = 0;
    
    private final UserManager userManager;
    private final DropPlugin plugin;

    public ADropCommand(DropPlugin plugin) {
        this.plugin = plugin;
        this.userManager = plugin.getUserManager();

        if (plugin.getCommand("adrop") != null) {
            plugin.getCommand("adrop").setExecutor(this);
            plugin.getCommand("adrop").setTabCompleter(this);
        } else {
            plugin.getLogger().warning("Command 'adrop' not defined in plugin.yml");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var config = this.plugin.getPluginConfig();
        
        if (!sender.hasPermission("dropplugin.cmd.adrop")) {
            ChatUtils.sendMessage(sender, config.getCmdADropNoPermission());
            return true;
        }

        if (args.length < 1) {
            config.getCmdADropUsage().forEach(m -> ChatUtils.sendMessage(sender, m));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload": {
                long start = System.currentTimeMillis();
                ChatUtils.sendMessage(sender, config.getCmdADropReloading());
                try {
                    this.plugin.reloadConfigManager();
                    float time = (float) (System.currentTimeMillis() - start) / 1000.0f;
                    ChatUtils.sendMessage(sender, config.getCmdADropReloaded().replace("{TIME}", String.valueOf(time)));
                } catch (Exception t) {
                    ChatUtils.sendMessage(sender, config.getCmdADropReloadError());
                    this.plugin.getLogger().log(Level.WARNING, "Przeładowanie konfiguracji nie powiodło się.", t);
                }
                return true;
            }

            case "drop":
            case "exp": {
                if (args.length < 3) {
                    config.getCmdADropUsage().forEach(m -> ChatUtils.sendMessage(sender, m));
                    return true;
                }
                String target = args[1];
                String timeStr = args[2];
                long time = DataUtils.parseDateDiff(timeStr, true);
                if (time <= 0L) {
                    ChatUtils.sendMessage(sender, config.getCmdADropInvalidTime().replace("{TIME}", timeStr));
                    return true;
                }

                boolean isDrop = sub.equals("drop");
                String typeName = isDrop ? "TurboDrop" : "TurboExp";

                if ("all".equalsIgnoreCase(target)) {
                    try {
                        config.saveTurbo(isDrop, time);
                    } catch (Exception failure) {
                        ChatUtils.sendMessage(sender, "&cNie udało się zapisać czasu turbo.");
                        plugin.getLogger().log(Level.WARNING, "Nie udało się zapisać czasu turbo.", failure);
                        return true;
                    }

                    String broadcast = config.getTurboMessage()
                            .replace("{ADMIN}", sender.getName())
                            .replace("{TYPE}", typeName)
                            .replace("{TIME}", timeStr);
                    Bukkit.getOnlinePlayers().forEach(player -> ChatUtils.sendMessage(player, broadcast));
                    ChatUtils.sendMessage(sender, config.getCmdADropTurboEnabled()
                            .replace("{TYPE}", typeName)
                            .replace("{TIME}", timeStr));
                    return true;
                }

                Player targetPlayer = Bukkit.getPlayerExact(target);
                if (targetPlayer == null) {
                    ChatUtils.sendMessage(sender, config.getCmdADropPlayerOffline());
                    return true;
                }

                User user = this.userManager.getUser(targetPlayer);
                if (user == null) {
                    ChatUtils.sendMessage(sender, config.getCmdADropPlayerDataError());
                    return true;
                }

                if (isDrop) {
                    user.addTurboDrop(time);
                } else {
                    user.addTurboExp(time);
                }

                ChatUtils.sendMessage(targetPlayer, config.getTurboGetMessage().replace("{TYPE}", typeName).replace("{TIME}", timeStr));
                ChatUtils.sendMessage(sender, config.getCmdADropTurboEnabledFor()
                        .replace("{TYPE}", typeName)
                        .replace("{TIME}", timeStr)
                        .replace("{PLAYER}", targetPlayer.getName()));
                return true;
            }

            case "level": {
                if (args.length < 3) {
                    config.getCmdADropUsage().forEach(m -> ChatUtils.sendMessage(sender, m));
                    return true;
                }

                String targetName = args[1];
                int newLevel;
                int maxLevel = config.getMaxLevel();
                int newPoints = 0;

                try {
                    newLevel = Integer.parseInt(args[2]);
                    if (newLevel < MIN_LEVEL || newLevel > maxLevel) {
                        ChatUtils.sendMessage(sender, config.getCmdADropLevelMinMax()
                                .replace("{MIN}", String.valueOf(MIN_LEVEL))
                                .replace("{MAX}", String.valueOf(maxLevel)));
                        return true;
                    }
                } catch (NumberFormatException e) {
                    ChatUtils.sendMessage(sender, config.getCmdADropLevelNotNumber());
                    return true;
                }

                int maxPoints = Math.max(0, User.pointsRequired(newLevel, config) - 1);
                if (args.length >= 4) {
                    try {
                        newPoints = Integer.parseInt(args[3]);
                        if (newPoints < MIN_POINTS || newPoints > maxPoints) {
                            ChatUtils.sendMessage(sender, config.getCmdADropPointsMinMax()
                                    .replace("{MIN}", String.valueOf(MIN_POINTS))
                                    .replace("{MAX}", String.valueOf(maxPoints)));
                            return true;
                        }
                    } catch (NumberFormatException e) {
                        ChatUtils.sendMessage(sender, config.getCmdADropPointsNotNumber());
                        return true;
                    }
                }

                Player targetPlayer = Bukkit.getPlayerExact(targetName);
                if (targetPlayer == null) {
                    ChatUtils.sendMessage(sender, config.getCmdADropPlayerOffline());
                    return true;
                }

                User targetUser = this.userManager.getUser(targetPlayer);
                if (targetUser == null) {
                    ChatUtils.sendMessage(sender, config.getCmdADropPlayerDataError());
                    return true;
                }

                int currentLevel = targetUser.getLvl();
                targetUser.setProgress(newLevel, newPoints);

                ChatUtils.sendMessage(sender, config.getCmdADropLevelChanged()
                        .replace("{PLAYER}", targetPlayer.getName())
                        .replace("{OLD_LEVEL}", String.valueOf(currentLevel))
                        .replace("{NEW_LEVEL}", String.valueOf(newLevel))
                        .replace("{POINTS}", String.valueOf(newPoints)));
                return true;
            }

            default: {
                config.getCmdADropUsage().forEach(m -> ChatUtils.sendMessage(sender, m));
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("dropplugin.cmd.adrop")) return List.of();
        List<String> choices = new ArrayList<>();
        if (args.length == 1) {
            choices.addAll(List.of("reload", "drop", "exp", "level"));
        } else if (args.length == 2) {
            String action = args[0].toLowerCase(Locale.ROOT);
            if (!List.of("drop", "exp", "level").contains(action)) return List.of();
            if (!action.equals("level")) choices.add("all");
            Bukkit.getOnlinePlayers().forEach(player -> {
                if (!(sender instanceof Player viewer) || viewer.canSee(player)) choices.add(player.getName());
            });
        } else {
            return List.of();
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(choice -> choice.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }
}
