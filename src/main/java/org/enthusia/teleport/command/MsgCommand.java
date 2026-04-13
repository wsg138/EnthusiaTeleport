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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MsgCommand implements CommandExecutor {

    private final EnthusiaTeleportPlugin plugin;
    private final MessageManager messageManager;

    public MsgCommand(EnthusiaTeleportPlugin plugin) {
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

        if (args.length < 2) {
            msg.send(player, "message.usage");
            return true;
        }

        List<String> targets = parseTargets(args[0]);
        if (targets.isEmpty()) {
            msg.send(player, "message.usage");
            return true;
        }

        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        if (message.isEmpty()) {
            msg.send(player, "message.usage");
            return true;
        }

        List<Player> recipients = new ArrayList<>();
        for (String name : targets) {
            if (name.equalsIgnoreCase(player.getName())) {
                msg.send(player, "message.to-self");
                continue;
            }

            Player target = Bukkit.getPlayerExact(name);
            if (target == null || !target.isOnline()) {
                msg.send(player, "message.player-offline",
                        Map.of("target", name));
                continue;
            }

            messageManager.sendDirectMessage(player, target, message);
            recipients.add(target);
        }

        if (!recipients.isEmpty()) {
            messageManager.setLastPartners(player, recipients);
        }

        return true;
    }

    private List<String> parseTargets(String raw) {
        String[] parts = raw.split(",");
        Map<String, String> unique = new LinkedHashMap<>();

        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            unique.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
        }

        return new ArrayList<>(unique.values());
    }
}
