package pl.tenfajnybartek.riftdrop.command;

import org.bukkit.permissions.Permissible;

public final class Permissions {
    private static final String PREFIX = "riftdrop.";

    private Permissions() {}

    public static boolean has(Permissible subject, String permission) {
        if (permission.startsWith(PREFIX) && !subject.isPermissionSet(permission)) {
            return subject.hasPermission("dropplugin." + permission.substring(PREFIX.length()));
        }
        return subject.hasPermission(permission);
    }
}
