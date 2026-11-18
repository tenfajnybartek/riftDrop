package pl.tenfajnybartek.dropplugin.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.tenfajnybartek.dropplugin.base.DropPlugin;
import pl.tenfajnybartek.dropplugin.utils.ChatUtils;

public class DropCommand implements CommandExecutor {
    private final DropPlugin plugin;

    public DropCommand(DropPlugin plugin) {
        this.plugin = plugin;
        if (plugin.getCommand("drop") != null) {
            plugin.getCommand("drop").setExecutor(this);
        } else {
            plugin.getLogger().warning("Command 'drop' is not defined in plugin.yml");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("dropplugin.cmd.drop")) {
            ChatUtils.sendMessage(sender, this.plugin.getPluginConfig().getCmdDropNoPermission());
            return true;
        }

        if (!(sender instanceof Player)) {
            ChatUtils.sendMessage(sender, this.plugin.getPluginConfig().getCmdDropOnlyPlayers());
            return true;
        }

        Player player = (Player) sender;
        var user = plugin.getUserManager().getUser(player);
        if (user == null) return ChatUtils.sendMessage(player, plugin.getPluginConfig().getCmdLevelPlayerDataError());
        player.openInventory(plugin.getDropMenu().createInventory(player, user));
        return true;
    }
}
