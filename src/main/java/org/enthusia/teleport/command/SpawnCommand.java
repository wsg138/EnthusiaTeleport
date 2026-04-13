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
