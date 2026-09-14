package org.enthusia.teleport.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.request.TeleportRequest;
import org.enthusia.teleport.request.TeleportRequestManager;
import org.enthusia.teleport.request.TeleportRequestType;
import org.enthusia.teleport.teleport.TeleportManager;
import org.enthusia.teleport.util.Messages;

import java.util.Map;

public class TpAcceptCommand implements CommandExecutor {

    private static final String BYPASS_COMBAT_PERMISSION = "enthusia.teleport.bypass-combat";

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

        // /tpaccept is gated by the accepter's combat state at acceptance time.
        // Once accepted, the anchor entering combat later does not invalidate the
        // already-started teleport for the other player.
        if (isCombatBlocked(target)) {
            msg.send(target, "teleport.combat-blocked");
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

        // A pending /tpahere becomes invalid as soon as its sender enters combat.
        // This is a backstop for the CombatLogX PlayerTagEvent listener; accepted
        // requests are removed before warmup starts and are intentionally not checked
        // again if the anchor enters combat later.
        if (req.getType() == TeleportRequestType.TPA_HERE && isCombatBlocked(senderPlayer)) {
            reqMgr.removeRequest(req);
            msg.send(target, "teleport.request.cancelled-tpahere-combat",
                    Map.of("player", senderPlayer.getName()));
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

        // A normal /tpa may stay pending while its requester is in combat, but it
        // must not be consumed as "accepted" until that requester can actually begin
        // the teleport. This also prevents misleading acceptance messages.
        if (isCombatBlocked(teleporter)) {
            msg.send(target, "teleport.request.teleporter-in-combat",
                    Map.of("player", teleporter.getName()));
            return true;
        }

        int warmupSeconds = teleporter.hasPermission("enthusia.teleport.bypass-teleport")
                ? 0
                : (int) Math.round(tpMgr.getEffectiveWarmupSeconds(teleporter.getUniqueId()));

        msg.send(teleporter, "teleport.accepted-to-teleporter",
                Map.of("other", anchor.getName(), "seconds", String.valueOf(warmupSeconds)));
        msg.send(anchor, "teleport.accepted-to-anchor",
                Map.of("teleporter", teleporter.getName(), "seconds", String.valueOf(warmupSeconds)));

        reqMgr.removeRequest(req);

        tpMgr.startTeleportToLivePlayer(
                teleporter,
                anchor,
                true,
                "teleport.warmup-start",
                null,
                TeleportManager.TeleportFlags.standard()
        );

        return true;
    }

    private boolean isCombatBlocked(Player player) {
        return plugin.getCombatManager().isInCombat(player)
                && !player.hasPermission(BYPASS_COMBAT_PERMISSION);
    }
}
