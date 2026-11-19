package org.enthusia.teleport.command;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.teleport.TeleportManager;
import org.enthusia.teleport.util.Messages;

public class SpawnCommand implements CommandExecutor {

    private final EnthusiaTeleportPlugin plugin;

    public SpawnCommand(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Messages msg = plugin.getMessages();

        if (!(sender instanceof Player player)) {
            msg.send(sender, "generic.no-console");
            return true;
        }

        if (args.length > 0) {
            if (!args[0].equalsIgnoreCase("respawn") || args.length > 2) {
                return false;
            }
            if (!plugin.getPluginConfigManager().current().spawn().respawnOverrideEnabled()) {
                msg.send(player, "respawn.disabled");
                return true;
            }
            var manager = plugin.getSpawnManager();
            if (args.length == 2) {
                boolean useSpawn = args[1].equalsIgnoreCase("spawn");
                if (!useSpawn && !args[1].equalsIgnoreCase("bed")) {
                    return false;
                }
                manager.setServerSpawnPreference(player, useSpawn);
            }
            msg.send(player, manager.prefersServerSpawn(player) ? "respawn.spawn" : "respawn.bed");
            return true;
        }

        Location loc = plugin.getSpawnManager().getSpawnLocation();
        if (loc == null) {
            player.sendMessage("§cSpawn is not configured.");
            return true;
        }

        TeleportManager tpMgr = plugin.getTeleportManager();
        tpMgr.startTeleport(player, loc, true, null, "spawn.warmup-start");

        return true;
    }
}
