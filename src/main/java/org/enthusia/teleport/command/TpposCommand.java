package org.enthusia.teleport.command;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.teleport.TeleportManager;
import org.enthusia.teleport.util.Messages;

import java.util.Map;

public class TpposCommand implements CommandExecutor {

    private final EnthusiaTeleportPlugin plugin;

    public TpposCommand(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Messages msg = plugin.getMessages();

        if (!(sender instanceof Player player)) {
            msg.send(sender, "generic.no-console");
            return true;
        }

        if (args.length < 3) {
            msg.send(player, "tppos.usage");
            return true;
        }

        double x, y, z;
        try {
            x = Double.parseDouble(args[0]);
            y = Double.parseDouble(args[1]);
            z = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            msg.send(player, "tppos.invalid-coordinates");
            return true;
        }

        World world;
        if (args.length >= 4) {
            world = Bukkit.getWorld(args[3]);
            if (world == null) {
                msg.send(player, "tppos.invalid-world", Map.of("world", args[3]));
                return true;
            }
        } else {
            world = player.getWorld();
        }

        Location loc = new Location(world, x, y, z, player.getLocation().getYaw(), player.getLocation().getPitch());
        plugin.getTeleportManager().startTeleport(
                player,
                loc,
                false,
                null,
                "teleport.warmup-start",
                null,
                TeleportManager.TeleportFlags.instant()
        );

        msg.send(player, "tppos.success", Map.of(
                "x", String.valueOf(x),
                "y", String.valueOf(y),
                "z", String.valueOf(z),
                "world", world.getName()
        ));
        return true;
    }
}
