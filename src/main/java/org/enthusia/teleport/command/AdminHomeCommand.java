package org.enthusia.teleport.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.util.Messages;

import java.util.Map;

public class AdminHomeCommand implements CommandExecutor {

    private final EnthusiaTeleportPlugin plugin;

    public AdminHomeCommand(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages msg = plugin.getMessages();

        if (!(sender instanceof Player player)) {
            msg.send(sender, "generic.no-console");
            return true;
        }

        if (!sender.hasPermission("enthusia.teleport.admin.homes.view")) {
            msg.send(sender, "generic.no-permission");
            return true;
        }

        if (args.length != 1) {
            msg.send(sender, "admin.homes.view-usage");
            return true;
        }

        String targetName = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target == null || (!target.isOnline() && !target.hasPlayedBefore())) {
            msg.send(sender, "admin.player-not-found", Map.of("target", targetName));
            return true;
        }

        plugin.getHomeGuiManager().openAdminHomeGui(player, target, targetName);
        return true;
    }
}
