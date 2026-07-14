package org.enthusia.teleport.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.home.Home;
import org.enthusia.teleport.home.HomeManager;
import org.enthusia.teleport.util.Messages;

import java.util.Collection;
import java.util.Map;

import static org.enthusia.teleport.command.CommandStrings.ignoresEqualCase;

public class HomeCommand implements CommandExecutor {

    private final EnthusiaTeleportPlugin plugin;

    public HomeCommand(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages msg = plugin.getMessages();

        if (!(sender instanceof Player player)) {
            msg.send(sender, "generic.no-console");
            return true;
        }

        if (ignoresEqualCase(command.getName(), "homes")) {
            handleHomesCommand(player, args, msg);
            return true;
        }

        HomeManager hm = plugin.getHomeManager();
        if (hm.isOverLimit(player)) {
            plugin.getHomeGuiManager().openLimitGui(player);
            msg.send(player, "home.limit-select.required");
            return true;
        }

        // /home teleports directly when there is only one possible destination.
        if (args.length == 0) {
            Collection<Home> homes = hm.getHomes(player.getUniqueId());
            if (homes.isEmpty()) {
                msg.send(player, "home.no-homes");
                return true;
            }
            if (homes.size() == 1) {
                Home home = homes.iterator().next();
                plugin.getHomeGuiManager().teleportToHome(player, home.getName(), false);
                return true;
            }
            msg.send(player, "home.specify-name");
            return true;
        }

        // /home <name> [force]
        String name = args[0];
        boolean force = args.length >= 2 && ignoresEqualCase(args[1], "force");

        plugin.getHomeGuiManager().teleportToHome(player, name, force);
        return true;
    }

    private void handleHomesCommand(Player player, String[] args, Messages msg) {
        if (args.length == 0) {
            HomeManager hm = plugin.getHomeManager();
            if (hm.isOverLimit(player)) {
                plugin.getHomeGuiManager().openLimitGui(player);
                msg.send(player, "home.limit-select.required");
                return;
            }
            plugin.getHomeGuiManager().openHomeGui(player);
            return;
        }

        if (args.length != 1) {
            msg.send(player, "admin.homes.view-usage");
            return;
        }

        if (!player.hasPermission("enthusia.teleport.admin.homes.view")) {
            msg.send(player, "generic.no-permission");
            return;
        }

        String targetName = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target == null || (!target.isOnline() && !target.hasPlayedBefore())) {
            msg.send(player, "admin.player-not-found", Map.of("target", targetName));
            return;
        }

        plugin.getHomeGuiManager().openAdminHomeGui(player, target, targetName);
    }
}
