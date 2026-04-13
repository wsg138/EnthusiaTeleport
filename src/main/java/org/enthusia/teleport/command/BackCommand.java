package org.enthusia.teleport.command;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.back.BackManager;
import org.enthusia.teleport.teleport.TeleportManager;
import org.enthusia.teleport.util.Messages;

public class BackCommand implements CommandExecutor {

    private final EnthusiaTeleportPlugin plugin;

    public BackCommand(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Messages msg = plugin.getMessages();

        if (!(sender instanceof Player player)) {
            msg.send(sender, "generic.no-console");
            return true;
        }

        BackManager backManager = plugin.getBackManager();
        Location backLoc = backManager.peek(player);
        if (backLoc == null) {
            msg.send(player, "back.none");
            return true;
        }

        if (backLoc.getWorld() == null) {
            backManager.pop(player);
            msg.send(player, "back.invalid");
            return true;
        }

        TeleportManager tpMgr = plugin.getTeleportManager();
        tpMgr.startTeleport(
                player,
                backLoc,
                false,
                null,
                "teleport.warmup-start",
                () -> backManager.pop(player),
                TeleportManager.TeleportFlags.back()
        );
        return true;
    }
}
