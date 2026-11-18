package pl.tenfajnybartek.riftdrop.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import pl.tenfajnybartek.riftdrop.RiftDropPlugin;
import pl.tenfajnybartek.riftdrop.profile.PlayerProfiles;
import pl.tenfajnybartek.riftdrop.profile.MiningProfile;
import pl.tenfajnybartek.riftdrop.text.Messages;
import pl.tenfajnybartek.riftdrop.text.BoostTime;

public class AdminDropCommand implements TabExecutor {

    private static final int MIN_LEVEL = 1;
    private static final int MIN_POINTS = 0;
    
    private final PlayerProfiles userManager;
    private final RiftDropPlugin plugin;

    public AdminDropCommand(RiftDropPlugin plugin) {
        this.plugin = plugin;
        this.userManager = plugin.profiles();

        if (plugin.getCommand("adrop") != null) {
            plugin.getCommand("adrop").setExecutor(this);
            plugin.getCommand("adrop").setTabCompleter(this);
        } else {
            plugin.getLogger().warning("Command 'adrop' not defined in plugin.yml");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var config = this.plugin.configuration();
        
        if (!Permissions.has(sender, "riftdrop.cmd.adrop")) {
            Messages.sendMessage(sender, config.getCmdADropNoPermission());
            return true;
        }

        if (args.length < 1) {
            config.getCmdADropUsage().forEach(m -> Messages.sendMessage(sender, m));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload": {
                long start = System.currentTimeMillis();
                Messages.sendMessage(sender, config.getCmdADropReloading());
                try {
                    this.plugin.reloadSettings();
                    float time = (float) (System.currentTimeMillis() - start) / 1000.0f;
                    Messages.sendMessage(sender, config.getCmdADropReloaded().replace("{TIME}", String.valueOf(time)));
                } catch (Exception t) {
                    Messages.sendMessage(sender, config.getCmdADropReloadError());
                    this.plugin.getLogger().log(Level.WARNING, "Przeładowanie konfiguracji nie powiodło się.", t);
                }
                return true;
            }

            case "drop":
            case "exp": {
                if (args.length < 3) {
                    config.getCmdADropUsage().forEach(m -> Messages.sendMessage(sender, m));
                    return true;
                }
                String target = args[1];
                String timeStr = args[2];
                long time = BoostTime.parseDateDiff(timeStr, true);
                if (time <= 0L) {
                    Messages.sendMessage(sender, config.getCmdADropInvalidTime().replace("{TIME}", timeStr));
                    return true;
                }

                boolean isDrop = sub.equals("drop");
                String typeName = isDrop ? "TurboDrop" : "TurboExp";

                if ("all".equalsIgnoreCase(target)) {
                    try {
                        config.saveTurbo(isDrop, time);
                    } catch (Exception failure) {
                        Messages.sendMessage(sender, "&cNie udało się zapisać czasu turbo.");
                        plugin.getLogger().log(Level.WARNING, "Nie udało się zapisać czasu turbo.", failure);
                        return true;
                    }

                    String broadcast = config.getTurboMessage()
                            .replace("{ADMIN}", sender.getName())
                            .replace("{TYPE}", typeName)
                            .replace("{TIME}", timeStr);
                    Bukkit.getOnlinePlayers().forEach(player -> Messages.sendMessage(player, broadcast));
                    Messages.sendMessage(sender, config.getCmdADropTurboEnabled()
                            .replace("{TYPE}", typeName)
                            .replace("{TIME}", timeStr));
                    return true;
                }

                Player targetPlayer = Bukkit.getPlayerExact(target);
                if (targetPlayer == null) {
                    Messages.sendMessage(sender, config.getCmdADropPlayerOffline());
                    return true;
                }

                MiningProfile user = this.userManager.getUser(targetPlayer);
                if (user == null) {
                    Messages.sendMessage(sender, config.getCmdADropPlayerDataError());
                    return true;
                }

                if (isDrop) {
                    user.addTurboDrop(time);
                } else {
                    user.addTurboExp(time);
                }

                Messages.sendMessage(targetPlayer, config.getTurboGetMessage().replace("{TYPE}", typeName).replace("{TIME}", timeStr));
                Messages.sendMessage(sender, config.getCmdADropTurboEnabledFor()
                        .replace("{TYPE}", typeName)
                        .replace("{TIME}", timeStr)
                        .replace("{PLAYER}", targetPlayer.getName()));
                return true;
            }

            case "level": {
                if (args.length < 3) {
                    config.getCmdADropUsage().forEach(m -> Messages.sendMessage(sender, m));
                    return true;
                }

                String targetName = args[1];
                int newLevel;
                int maxLevel = config.getMaxLevel();
                int newPoints = 0;

                try {
                    newLevel = Integer.parseInt(args[2]);
                    if (newLevel < MIN_LEVEL || newLevel > maxLevel) {
                        Messages.sendMessage(sender, config.getCmdADropLevelMinMax()
                                .replace("{MIN}", String.valueOf(MIN_LEVEL))
                                .replace("{MAX}", String.valueOf(maxLevel)));
                        return true;
                    }
                } catch (NumberFormatException e) {
                    Messages.sendMessage(sender, config.getCmdADropLevelNotNumber());
                    return true;
                }

                int maxPoints = Math.max(0, MiningProfile.pointsRequired(newLevel, config) - 1);
                if (args.length >= 4) {
                    try {
                        newPoints = Integer.parseInt(args[3]);
                        if (newPoints < MIN_POINTS || newPoints > maxPoints) {
                            Messages.sendMessage(sender, config.getCmdADropPointsMinMax()
                                    .replace("{MIN}", String.valueOf(MIN_POINTS))
                                    .replace("{MAX}", String.valueOf(maxPoints)));
                            return true;
                        }
                    } catch (NumberFormatException e) {
                        Messages.sendMessage(sender, config.getCmdADropPointsNotNumber());
                        return true;
                    }
                }

                Player targetPlayer = Bukkit.getPlayerExact(targetName);
                if (targetPlayer == null) {
                    Messages.sendMessage(sender, config.getCmdADropPlayerOffline());
                    return true;
                }

                MiningProfile targetUser = this.userManager.getUser(targetPlayer);
                if (targetUser == null) {
                    Messages.sendMessage(sender, config.getCmdADropPlayerDataError());
                    return true;
                }

                int currentLevel = targetUser.getLvl();
                targetUser.setProgress(newLevel, newPoints);

                Messages.sendMessage(sender, config.getCmdADropLevelChanged()
                        .replace("{PLAYER}", targetPlayer.getName())
                        .replace("{OLD_LEVEL}", String.valueOf(currentLevel))
                        .replace("{NEW_LEVEL}", String.valueOf(newLevel))
                        .replace("{POINTS}", String.valueOf(newPoints)));
                return true;
            }

            default: {
                config.getCmdADropUsage().forEach(m -> Messages.sendMessage(sender, m));
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!Permissions.has(sender, "riftdrop.cmd.adrop")) return List.of();
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
