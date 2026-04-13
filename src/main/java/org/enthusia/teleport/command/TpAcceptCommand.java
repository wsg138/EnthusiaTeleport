package org.enthusia.teleport.command;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.request.TeleportRequest;
import org.enthusia.teleport.request.TeleportRequestManager;
import org.enthusia.teleport.request.TeleportRequestType;
import org.enthusia.teleport.teleport.TeleportManager;
import org.enthusia.teleport.util.Messages;

import java.util.Map;

public class TpAcceptCommand implements CommandExecutor {

    private final EnthusiaTeleportPlugin plugin;

    public TpAcceptCommand(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Messages msg = plugin.getMessages();

        if (!(sender instanceof Player target)) {
            msg.send(sender, "generic.no-console");
            return true;
        }

        TeleportRequestManager reqMgr = plugin.getRequestManager();
        TeleportRequest req;

        if (args.length >= 1) {
            Player from = Bukkit.getPlayerExact(args[0]);
            if (from == null) {
                msg.send(target, "teleport.no-pending-from",
                        Map.of("sender", args[0]));
                return true;
            }
            req = reqMgr.getIncoming(target, from);
        } else {
            req = reqMgr.getMostRecentIncoming(target);
        }

        if (req == null) {
            msg.send(target, "teleport.no-pending");
            return true;
        }

        Player senderPlayer = req.getSenderPlayer();
        Player targetPlayer = req.getTargetPlayer();
        if (senderPlayer == null || !senderPlayer.isOnline()) {
            msg.send(target, "teleport.requester-offline");
            reqMgr.removeRequest(req);
            return true;
        }

        TeleportManager tpMgr = plugin.getTeleportManager();

        Player teleporter;
        Player anchor;

        if (req.getType() == TeleportRequestType.TPA) {
            teleporter = senderPlayer;
            anchor = targetPlayer;
        } else {
            teleporter = targetPlayer;
            anchor = senderPlayer;
        }

        int warmupSeconds = teleporter.hasPermission("enthusia.teleport.bypass-teleport")
                ? 0
                : (int) Math.round(tpMgr.getEffectiveWarmupSeconds(teleporter.getUniqueId()));

        msg.send(teleporter, "teleport.accepted-to-teleporter",
                Map.of("other", anchor.getName(), "seconds", String.valueOf(warmupSeconds)));
        msg.send(anchor, "teleport.accepted-to-anchor",
                Map.of("teleporter", teleporter.getName(), "seconds", String.valueOf(warmupSeconds)));

        Location targetLoc = anchor.getLocation();

        reqMgr.removeRequest(req);

        tpMgr.startTeleport(
                teleporter,
                targetLoc,
                true,
                anchor,
                "teleport.warmup-start"
        );

        return true;
    }
}
