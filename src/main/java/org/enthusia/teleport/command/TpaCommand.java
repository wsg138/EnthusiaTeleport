package org.enthusia.teleport.command;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.ignore.IgnoreManager;
import org.enthusia.teleport.request.TeleportRequestManager;
import org.enthusia.teleport.request.TeleportRequestType;
import org.enthusia.teleport.util.Messages;

import java.util.Map;

public class TpaCommand implements CommandExecutor {

    private final EnthusiaTeleportPlugin plugin;
    private final boolean here;

    public TpaCommand(EnthusiaTeleportPlugin plugin, boolean here) {
        this.plugin = plugin;
        this.here = here;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages msg = plugin.getMessages();

        if (!(sender instanceof Player player)) {
            msg.send(sender, "generic.no-console");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("§cUsage: /" + label + " <player>");
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            msg.send(player, "teleport.request.player-not-found",
                    Map.of("target", targetName));
            return true;
        }

        if (target.equals(player)) {
            msg.send(player, "teleport.request.to-self");
            return true;
        }

        TeleportRequestManager reqMgr = plugin.getRequestManager();
        IgnoreManager ignoreMgr = plugin.getIgnoreManager();

        // Only block if there's already a request from this sender to THIS target
        if (reqMgr.hasOutgoingTo(player, target)) {
            msg.send(player, "teleport.request.already-outgoing",
                    Map.of("target", target.getName()));
            return true;
        }

        if (ignoreMgr.isIgnoring(target.getUniqueId(), player.getUniqueId())) {
            msg.send(player, "teleport.request.ignored-sender",
                    Map.of("target", target.getName()));
            return true;
        }

        TeleportRequestType type = here ? TeleportRequestType.TPA_HERE : TeleportRequestType.TPA;
        reqMgr.createRequest(player, target, type);

        int expirySeconds = plugin.getConfig().getInt("teleport.request-expiry-seconds", 60);

        if (here) {
            msg.send(player, "teleport.request.tpa-here-sent",
                    Map.of("target", target.getName(), "seconds", String.valueOf(expirySeconds)));

            msg.send(target, "teleport.request.to-receiver-here",
                    Map.of("sender", player.getName()));
            msg.send(target, "teleport.request.to-receiver-here-extra",
                    Map.of("sender", player.getName(), "seconds", String.valueOf(expirySeconds)));
        } else {
            msg.send(player, "teleport.request.tpa-sent",
                    Map.of("target", target.getName(), "seconds", String.valueOf(expirySeconds)));

            msg.send(target, "teleport.request.to-receiver",
                    Map.of("sender", player.getName()));
            msg.send(target, "teleport.request.to-receiver-extra",
                    Map.of("sender", player.getName(), "seconds", String.valueOf(expirySeconds)));
        }

        return true;
    }
}
