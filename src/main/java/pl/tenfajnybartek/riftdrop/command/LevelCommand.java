package pl.tenfajnybartek.riftdrop.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import pl.tenfajnybartek.riftdrop.RiftDropPlugin;
import pl.tenfajnybartek.riftdrop.profile.PlayerProfiles;
import pl.tenfajnybartek.riftdrop.profile.MiningProfile;
import pl.tenfajnybartek.riftdrop.text.Messages;

public class LevelCommand implements TabExecutor {

    private final PlayerProfiles userManager;
    private final RiftDropPlugin plugin;

    public LevelCommand(RiftDropPlugin plugin) {
        this.plugin = plugin;
        this.userManager = plugin.profiles();
        if (plugin.getCommand("level") != null) {
            plugin.getCommand("level").setExecutor(this);
            plugin.getCommand("level").setTabCompleter(this);
        } else {
            plugin.getLogger().warning("Command 'level' is not defined in plugin.yml");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var config = this.plugin.configuration();
        
        if (!Permissions.has(sender, "riftdrop.cmd.level")) {
            Messages.sendMessage(sender, config.getCmdLevelNoPermission());
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                Messages.sendMessage(sender, config.getCmdLevelOnlyPlayers());
                return true;
            }
            Player player = (Player) sender;
            MiningProfile u = this.userManager.getUser(player);
            if (u == null) {
                Messages.sendMessage(sender, config.getCmdLevelPlayerDataError());
                return true;
            }
            displayPlayerInfo(sender, u, config);
            return true;
        }

        if (args.length == 1) {
            if (!Permissions.has(sender, "riftdrop.cmd.alevel")) {
                Messages.sendMessage(sender, config.getCmdLevelNoALevelPermission());
                return true;
            }

            Player targetPlayer = Bukkit.getPlayerExact(args[0]);
            if (targetPlayer == null) {
                Messages.sendMessage(sender, config.getCmdLevelPlayerOffline());
                return true;
            }

            MiningProfile targetUser = this.userManager.getUser(targetPlayer);
            if (targetUser == null) {
                Messages.sendMessage(sender, config.getCmdLevelTargetPlayerDataError());
                return true;
            }

            displayPlayerInfo(sender, targetUser, config);
            return true;
        }

        Messages.sendMessage(sender, config.getCmdLevelUsage());
        return true;
    }

    private void displayPlayerInfo(CommandSender sender, MiningProfile user, pl.tenfajnybartek.riftdrop.configuration.PluginConfiguration config) {
        if (user == null || user.getPlayer() == null) {
            Messages.sendMessage(sender, config.getCmdLevelNoData());
            return;
        }

        Messages.sendMessage(sender, config.getCmdLevelHeader());
        Messages.sendMessage(sender, " ");
        Messages.sendMessage(sender, config.getCmdLevelNick().replace("{PLAYER}", user.getPlayer().getName()));
        Messages.sendMessage(sender, config.getCmdLevelLevelPoints()
                .replace("{LEVEL}", String.valueOf(user.getLvl()))
                .replace("{POINTS}", String.valueOf(user.getPoints())));
        Messages.sendMessage(sender, config.getCmdLevelPointsToNext()
                .replace("{POINTS_TO_NEXT}", String.valueOf(user.getPointsToNextLevel())));
        Messages.sendMessage(sender, " ");
        Messages.sendMessage(sender, config.getCmdLevelFooter());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !Permissions.has(sender, "riftdrop.cmd.level") || !Permissions.has(sender, "riftdrop.cmd.alevel")) {
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
