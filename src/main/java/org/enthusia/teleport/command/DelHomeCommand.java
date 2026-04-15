package org.enthusia.teleport.command;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.home.Home;
import org.enthusia.teleport.home.HomeManager;
import org.enthusia.teleport.util.Messages;

import java.util.Map;

public class DelHomeCommand implements CommandExecutor {

    private final EnthusiaTeleportPlugin plugin;

    public DelHomeCommand(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Messages msg = plugin.getMessages();

        if (!(sender instanceof Player player)) {
            msg.send(sender, "generic.no-console");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("§cUsage: /delhome <name>");
            return true;
        }

        String name = args[0];

        HomeManager hm = plugin.getHomeManager();
        Home home = hm.getHome(player.getUniqueId(), name);
        if (home == null) {
            msg.send(player, "home.unknown", Map.of("name", name));
            return true;
        }

        hm.deleteHome(player.getUniqueId(), name);
        hm.saveAll();
        msg.send(player, "home.deleted", Map.of("name", home.getName()));
        return true;
    }
}
