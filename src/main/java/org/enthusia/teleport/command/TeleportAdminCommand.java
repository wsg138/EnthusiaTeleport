package org.enthusia.teleport.command;

import org.bukkit.command.*;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.home.Home;
import org.enthusia.teleport.home.HomeManager;
import org.enthusia.teleport.teleport.TeleportManager;
import org.enthusia.teleport.util.Messages;

import java.util.Map;

public class TeleportAdminCommand implements CommandExecutor {

    private final EnthusiaTeleportPlugin plugin;

    public TeleportAdminCommand(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Messages msg = plugin.getMessages();

        if (!sender.hasPermission("enthusia.teleport.admin")) {
            msg.send(sender, "generic.no-permission");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadPlugin();
            msg.send(sender, "admin.reloaded");
            return true;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("homes")) {
            HomeManager hm = plugin.getHomeManager();
            String action = args[1].toLowerCase();

            if (args.length >= 3) {
                String targetName = args[2];
                OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                if (target == null || (!target.isOnline() && !target.hasPlayedBefore())) {
                    msg.send(sender, "admin.player-not-found", Map.of("target", targetName));
                    return true;
                }

                if (action.equals("clear")) {
                    hm.clearHomes(target.getUniqueId());
                    hm.saveAll();
                    msg.send(sender, "admin.homes.cleared", Map.of("target", target.getName() == null ? targetName : target.getName()));
                    return true;
                }

                if (action.equals("del") && args.length >= 4) {
                    String homeName = args[3];
                    Home home = hm.getHome(target.getUniqueId(), homeName);
                    if (home == null) {
                        msg.send(sender, "home.unknown", Map.of("name", homeName));
                        return true;
                    }
                    hm.deleteHome(target.getUniqueId(), homeName);
                    hm.saveAll();
                    msg.send(sender, "admin.homes.deleted", Map.of("target", target.getName() == null ? targetName : target.getName(), "name", home.getName()));
                    plugin.getAdminLogManager().logHomeDelete(sender, target, home);
                    return true;
                }

                if (action.equals("tp") && args.length >= 4) {
                    if (!sender.hasPermission("enthusia.teleport.admin.homes.teleport")) {
                        msg.send(sender, "generic.no-permission");
                        return true;
                    }

                    if (!(sender instanceof Player player)) {
                        msg.send(sender, "generic.no-console");
                        return true;
                    }

                    String homeName = args[3];
                    Home home = hm.getHome(target.getUniqueId(), homeName);
                    if (home == null) {
                        msg.send(sender, "home.unknown", Map.of("name", homeName));
                        return true;
                    }

                    org.bukkit.Location dest = home.toLocation();
                    plugin.getTeleportManager().startTeleport(
                            player,
                            dest,
                            false,
                            null,
                            "teleport.warmup-start",
                            () -> {
                                msg.send(player, "admin.homes.teleported",
                                        Map.of("target", target.getName() == null ? targetName : target.getName(),
                                                "name", home.getName()));
                                plugin.getAdminLogManager().logHomeTeleport(player, target, home, dest);
                            },
                            TeleportManager.TeleportFlags.instant()
                    );
                    return true;
                }
            }
        }

        sender.sendMessage("§e/eteleport reload");
        sender.sendMessage("§e/eteleport homes clear <player>");
        sender.sendMessage("§e/eteleport homes del <player> <name>");
        sender.sendMessage("§e/eteleport homes tp <player> <name>");
        return true;
    }
}
