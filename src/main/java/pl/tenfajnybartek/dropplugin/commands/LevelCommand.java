package pl.tenfajnybartek.dropplugin.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import pl.tenfajnybartek.dropplugin.base.DropPlugin;
import pl.tenfajnybartek.dropplugin.managers.UserManager;
import pl.tenfajnybartek.dropplugin.objects.User;
import pl.tenfajnybartek.dropplugin.utils.ChatUtils;

public class LevelCommand implements TabExecutor {

    private final UserManager userManager;
    private final DropPlugin plugin;

    public LevelCommand(DropPlugin plugin) {
        this.plugin = plugin;
        this.userManager = plugin.getUserManager();
        if (plugin.getCommand("level") != null) {
            plugin.getCommand("level").setExecutor(this);
            plugin.getCommand("level").setTabCompleter(this);
        } else {
            plugin.getLogger().warning("Command 'level' is not defined in plugin.yml");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var config = this.plugin.getPluginConfig();
        
        if (!sender.hasPermission("dropplugin.cmd.level")) {
            ChatUtils.sendMessage(sender, config.getCmdLevelNoPermission());
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                ChatUtils.sendMessage(sender, config.getCmdLevelOnlyPlayers());
                return true;
            }
            Player player = (Player) sender;
            User u = this.userManager.getUser(player);
            if (u == null) {
                ChatUtils.sendMessage(sender, config.getCmdLevelPlayerDataError());
                return true;
            }
            displayPlayerInfo(sender, u, config);
            return true;
        }

        if (args.length == 1) {
            if (!sender.hasPermission("dropplugin.cmd.alevel")) {
                ChatUtils.sendMessage(sender, config.getCmdLevelNoALevelPermission());
                return true;
            }

            Player targetPlayer = Bukkit.getPlayerExact(args[0]);
            if (targetPlayer == null) {
                ChatUtils.sendMessage(sender, config.getCmdLevelPlayerOffline());
                return true;
            }

            User targetUser = this.userManager.getUser(targetPlayer);
            if (targetUser == null) {
                ChatUtils.sendMessage(sender, config.getCmdLevelTargetPlayerDataError());
                return true;
            }

            displayPlayerInfo(sender, targetUser, config);
            return true;
        }

        ChatUtils.sendMessage(sender, config.getCmdLevelUsage());
        return true;
    }

    private void displayPlayerInfo(CommandSender sender, User user, pl.tenfajnybartek.dropplugin.managers.ConfigManager config) {
        if (user == null || user.getPlayer() == null) {
            ChatUtils.sendMessage(sender, config.getCmdLevelNoData());
            return;
        }

        ChatUtils.sendMessage(sender, config.getCmdLevelHeader());
        ChatUtils.sendMessage(sender, " ");
        ChatUtils.sendMessage(sender, config.getCmdLevelNick().replace("{PLAYER}", user.getPlayer().getName()));
        ChatUtils.sendMessage(sender, config.getCmdLevelLevelPoints()
                .replace("{LEVEL}", String.valueOf(user.getLvl()))
                .replace("{POINTS}", String.valueOf(user.getPoints())));
        ChatUtils.sendMessage(sender, config.getCmdLevelPointsToNext()
                .replace("{POINTS_TO_NEXT}", String.valueOf(user.getPointsToNextLevel())));
        ChatUtils.sendMessage(sender, " ");
        ChatUtils.sendMessage(sender, config.getCmdLevelFooter());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission("dropplugin.cmd.level") || !sender.hasPermission("dropplugin.cmd.alevel")) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> !(sender instanceof Player viewer) || viewer.canSee(player))
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted()
                .toList();
    }
}
