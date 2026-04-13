package org.enthusia.teleport.command;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.home.HomeManager;
import org.enthusia.teleport.teleport.SafeLocationFinder;
import org.enthusia.teleport.teleport.TeleportManager;
import org.enthusia.teleport.util.Messages;

import java.util.Map;

public class SetHomeCommand implements CommandExecutor {

    private final EnthusiaTeleportPlugin plugin;

    public SetHomeCommand(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages msg = plugin.getMessages();

        if (!(sender instanceof Player player)) {
            msg.send(sender, "generic.no-console");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("§cUsage: /sethome <name>");
            return true;
        }

        String name = args[0].toLowerCase();

        HomeManager hm = plugin.getHomeManager();

        if (hm.isOverLimit(player)) {
            plugin.getHomeGuiManager().openLimitGui(player);
            msg.send(player, "home.limit-select.required");
            return true;
        }

        // Duplicate name check
        if (hm.getHome(player.getUniqueId(), name) != null) {
            msg.send(player, "home.set.duplicate", Map.of("name", name));
            return true;
        }

        // Home limit check
        int limit = hm.getHomeLimit(player);
        int current = hm.getHomeCount(player.getUniqueId());
        if (current >= limit) {
            msg.send(player, "home.limit-reached", Map.of("limit", String.valueOf(limit)));
            return true;
        }

        // Safety check
        TeleportManager tp = plugin.getTeleportManager();
        SafeLocationFinder finder = tp.getSafeFinder();
        if (!finder.isSafeHomeLocation(player.getLocation())) {
            msg.send(player, "home.unsafe-current-location");
            return true;
        }

        hm.setHome(player, name);
        hm.saveAll();
        msg.send(player, "home.set.success", Map.of("name", name));
        return true;
    }
}
