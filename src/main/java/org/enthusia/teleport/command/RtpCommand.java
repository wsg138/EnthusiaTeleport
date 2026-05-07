package org.enthusia.teleport.command;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.rtp.RtpManager;
import org.enthusia.teleport.util.Messages;

import java.util.Map;

public class RtpCommand implements CommandExecutor {

    private final EnthusiaTeleportPlugin plugin;

    public RtpCommand(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Messages msg = plugin.getMessages();

        if (!(sender instanceof Player player)) {
            msg.send(sender, "generic.no-console");
            return true;
        }

        if (!plugin.getPluginConfigManager().current().rtp().enabled()) {
            msg.send(player, "rtp.disabled");
            return true;
        }

        if (args.length > 0) {
            msg.send(player, "rtp.usage");
            return true;
        }

        RtpManager rtp = plugin.getRtpManager();

        if (!rtp.canUse(player)) {
            int limit = rtp.getLimit(player);
            msg.send(player, "rtp.limit-reached",
                    Map.of("limit", String.valueOf(limit)));
            return true;
        }

        rtp.enqueue(player);
        return true;
    }
}
