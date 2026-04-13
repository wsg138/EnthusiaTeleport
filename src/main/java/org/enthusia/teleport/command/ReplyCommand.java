package org.enthusia.teleport.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.message.MessageManager;
import org.enthusia.teleport.util.Messages;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ReplyCommand implements CommandExecutor {

    private final EnthusiaTeleportPlugin plugin;
    private final MessageManager messageManager;

    public ReplyCommand(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
        this.messageManager = plugin.getMessageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages msg = plugin.getMessages();

        if (!(sender instanceof Player player)) {
            msg.send(sender, "generic.no-console");
            return true;
        }

        if (args.length < 1) {
            msg.send(player, "reply.usage");
            return true;
        }

        List<UUID> lastIds = messageManager.getLastPartnerIds(player.getUniqueId());
        if (lastIds.isEmpty()) {
            msg.send(player, "reply.none");
            return true;
        }

        String message = String.join(" ", args).trim();
        if (message.isEmpty()) {
            msg.send(player, "reply.usage");
            return true;
        }

        List<Player> onlineTargets = new ArrayList<>();
        for (UUID id : lastIds) {
            Player target = Bukkit.getPlayer(id);
            if (target == null || !target.isOnline()) {
                continue;
            }
            onlineTargets.add(target);
        }

        if (onlineTargets.isEmpty()) {
            List<String> names = messageManager.getLastPartnerNames(player.getUniqueId());
            if (names.isEmpty()) {
                msg.send(player, "reply.offline", Map.of("target", "that player"));
            } else {
                for (String name : names) {
                    msg.send(player, "reply.offline", Map.of("target", name));
                }
            }
            return true;
        }

        for (Player target : onlineTargets) {
            messageManager.sendDirectMessage(player, target, message);
        }
        messageManager.setLastPartners(player, onlineTargets);
        return true;
    }
}
