package org.enthusia.teleport.command;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.teleport.TeleportManager;
import org.enthusia.teleport.util.Messages;

/**
 * Teleports the player to their current bed spawn (if it still exists).
 */
public class BedCommand implements CommandExecutor {

    private final EnthusiaTeleportPlugin plugin;

    public BedCommand(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages msg = plugin.getMessages();

        if (!(sender instanceof Player player)) {
            msg.send(sender, "generic.no-console");
            return true;
        }

        Location bed = player.getBedSpawnLocation();
        if (bed == null || bed.getWorld() == null) {
            sendOrFallback(msg, player, "bed.not-set", "&cYou do not have a bed spawn set.");
            return true;
        }

        TeleportManager tpMgr = plugin.getTeleportManager();
        // If the warmup message is missing, provide a short default so the player sees feedback.
        if (msg.raw("bed.warmup-start") == null) {
            player.sendMessage(msg.color("&eTeleporting to your bed. Don't move."));
        }
        tpMgr.startTeleport(player, bed, true, null, "bed.warmup-start");
        return true;
    }

    private void sendOrFallback(Messages msg, Player player, String key, String fallback) {
        String raw = msg.raw(key);
        if (raw == null || raw.isEmpty()) {
            player.sendMessage(msg.color(fallback));
        } else {
            msg.send(player, key);
        }
    }
}
