package pl.tenfajnybartek.riftdrop.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.tenfajnybartek.riftdrop.RiftDropPlugin;
import pl.tenfajnybartek.riftdrop.text.Messages;

public class DropCommand implements CommandExecutor {
    private final RiftDropPlugin plugin;

    public DropCommand(RiftDropPlugin plugin) {
        this.plugin = plugin;
        if (plugin.getCommand("drop") != null) {
            plugin.getCommand("drop").setExecutor(this);
        } else {
            plugin.getLogger().warning("Command 'drop' is not defined in plugin.yml");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!Permissions.has(sender, "riftdrop.cmd.drop")) {
            Messages.sendMessage(sender, this.plugin.configuration().getCmdDropNoPermission());
            return true;
        }

        if (!(sender instanceof Player)) {
            Messages.sendMessage(sender, this.plugin.configuration().getCmdDropOnlyPlayers());
            return true;
        }

        Player player = (Player) sender;
        var user = plugin.profiles().getUser(player);
        if (user == null) return Messages.sendMessage(player, plugin.configuration().getCmdLevelPlayerDataError());
        player.openInventory(plugin.menu().createInventory(player, user));
        return true;
    }
}
