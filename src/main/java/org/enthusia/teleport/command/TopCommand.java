package org.enthusia.teleport.command;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.teleport.TeleportManager;
import org.enthusia.teleport.util.Messages;

public class TopCommand implements CommandExecutor {

    private final EnthusiaTeleportPlugin plugin;

    public TopCommand(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Messages msg = plugin.getMessages();

        if (!(sender instanceof Player player)) {
            msg.send(sender, "generic.no-console");
            return true;
        }

        if (!player.hasPermission("enthusia.teleport.top")) {
            msg.send(player, "generic.no-permission");
            return true;
        }

        World world = player.getWorld();
        Location current = player.getLocation();
        int x = current.getBlockX();
        int z = current.getBlockZ();

        int y = world.getHighestBlockYAt(x, z) + 1;

        Location top = new Location(
                world,
                x + 0.5,
                y,
                z + 0.5,
                current.getYaw(),
                current.getPitch()
        );

        plugin.getTeleportManager().startTeleport(
                player,
                top,
                false,
                null,
                "teleport.warmup-start",
                null,
                TeleportManager.TeleportFlags.instant()
        );
        msg.send(player, "top.teleported");
        return true;
    }
}
