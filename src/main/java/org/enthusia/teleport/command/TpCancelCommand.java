package org.enthusia.teleport.command;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.request.TeleportRequest;
import org.enthusia.teleport.request.TeleportRequestManager;
import org.enthusia.teleport.util.Messages;

import java.util.Collection;
import java.util.Map;

public class TpCancelCommand implements CommandExecutor {

    private final EnthusiaTeleportPlugin plugin;

    public TpCancelCommand(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Messages msg = plugin.getMessages();

        if (!(sender instanceof Player player)) {
            msg.send(sender, "generic.no-console");
            return true;
        }

        TeleportRequestManager reqMgr = plugin.getRequestManager();
        Collection<TeleportRequest> outgoing = reqMgr.getOutgoingRequests(player);

        if (outgoing.isEmpty()) {
            msg.send(player, "teleport.no-pending");
            return true;
        }

        // Cancel all outgoing requests from this player
        for (TeleportRequest req : outgoing) {
            Player target = req.getTargetPlayer();
            reqMgr.removeRequest(req);

            if (target != null && target.isOnline()) {
                msg.send(target, "teleport.cancelled-target",
                        Map.of("sender", player.getName()));
            }
        }

        msg.send(player, "teleport.cancelled-requester");
        return true;
    }
}
