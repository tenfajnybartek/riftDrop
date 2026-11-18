package pl.tenfajnybartek.riftdrop.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;


public final class Messages {
    private Messages() {}

    // Supports both legacy color codes (&c, &a, etc.) and hex colors (&#RRGGBB)
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexCharacter('#')
            .hexColors()
            .build();

    public static Component toComponent(String text) {
        if (text == null) return Component.empty();
        return LEGACY_SERIALIZER.deserialize(text);
    }

    public static boolean sendMessage(Player player, String message) {
        if (player == null || message == null || message.isEmpty()) return true;
        player.sendMessage(toComponent(message));
        return true;
    }

    public static boolean sendMessage(CommandSender commandSender, String message) {
        if (commandSender != null && message != null && !message.isEmpty()) commandSender.sendMessage(toComponent(message));
        return true;
    }
}
