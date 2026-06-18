package org.enthusia.teleport.command;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.home.Home;
import org.enthusia.teleport.home.HomeManager;
import org.enthusia.teleport.util.Messages;

import java.util.Collection;

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
}
