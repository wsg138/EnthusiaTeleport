package org.enthusia.teleport.command;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.request.TeleportRequest;
import org.enthusia.teleport.request.TeleportRequestManager;
import org.enthusia.teleport.util.Messages;

import java.util.Map;

public class TpDenyCommand implements CommandExecutor {

    private final EnthusiaTeleportPlugin plugin;

    public TpDenyCommand(EnthusiaTeleportPlugin plugin) {
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

        Player requester = req.getSenderPlayer();
        reqMgr.removeRequest(req);

        msg.send(target, "teleport.denied-target");
        if (requester != null && requester.isOnline()) {
            msg.send(requester, "teleport.denied-requester",
                    Map.of("target", target.getName()));
        }
        return true;
    }
}
