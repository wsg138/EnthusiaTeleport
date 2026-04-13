package org.enthusia.teleport.command;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.home.HomeManager;
import org.enthusia.teleport.util.Messages;

import java.util.Map;

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

        HomeManager hm = plugin.getHomeManager();
        if (hm.isOverLimit(player)) {
            plugin.getHomeGuiManager().openLimitGui(player);
            msg.send(player, "home.limit-select.required");
            return true;
        }

        // /home opens GUI
        if (args.length == 0) {
            if (hm.getHomeCount(player.getUniqueId()) == 0) {
                msg.send(player, "home.no-homes");
                return true;
            }
            plugin.getHomeGuiManager().openHomeGui(player);
            return true;
        }

        // /home <name> [force]
        String name = args[0].toLowerCase();
        boolean force = args.length >= 2 && args[1].equalsIgnoreCase("force");

        plugin.getHomeGuiManager().teleportToHome(player, name, force);
        return true;
    }
}
