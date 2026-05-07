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

import static org.enthusia.teleport.command.CommandStrings.ignoresEqualCase;

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

        if (args.length == 1 && ignoresEqualCase(args[0], "reload")) {
            plugin.reloadPlugin();
            msg.send(sender, "admin.reloaded");
            return true;
        }

        if (args.length == 1 && ignoresEqualCase(args[0], "performance")) {
            msg.send(sender, "performance.header");
            for (Map.Entry<String, Long> entry : plugin.getPerformanceMonitor().snapshot().entrySet()) {
                msg.send(sender, "performance.line", Map.of("key", entry.getKey(), "value", String.valueOf(entry.getValue())));
            }
            return true;
        }

        if (args.length >= 2 && ignoresEqualCase(args[0], "homes")) {
            HomeManager hm = plugin.getHomeManager();
            String action = args[1];

            if (args.length >= 3) {
                String targetName = args[2];
                OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                if (target == null || (!target.isOnline() && !target.hasPlayedBefore())) {
                    msg.send(sender, "admin.player-not-found", Map.of("target", targetName));
                    return true;
                }

                if (ignoresEqualCase(action, "clear")) {
                    hm.clearHomes(target.getUniqueId());
                    hm.saveAll();
                    msg.send(sender, "admin.homes.cleared", Map.of("target", target.getName() == null ? targetName : target.getName()));
                    return true;
                }

                if (ignoresEqualCase(action, "del") && args.length >= 4) {
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

                if (ignoresEqualCase(action, "tp") && args.length >= 4) {
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
