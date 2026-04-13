package org.enthusia.teleport.command;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.teleport.TeleportManager;
import org.enthusia.teleport.util.Messages;

import java.util.Map;

public class TpoCommand implements CommandExecutor {

    private final EnthusiaTeleportPlugin plugin;

    public TpoCommand(EnthusiaTeleportPlugin plugin) {
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
            msg.send(player, "tpo.usage");
            return true;
        }

        String targetName = args[0];
        boolean force = args.length >= 2 && args[1].equalsIgnoreCase("force");
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (target.isOnline()) {
            msg.send(player, "tpo.target-online", Map.of("target", target.getName()));
            return true;
        }

        if (!target.hasPlayedBefore()) {
            msg.send(player, "tpo.player-not-found", Map.of("target", targetName));
            return true;
        }

        Location loc = target.getLocation();
        if (loc == null || loc.getWorld() == null) {
            if (!force) {
                String raw = msg.rawOr(
                        "tpo.unknown-location-warning",
                        "&cNo logout location for &e{target}&c. &e[Teleport to spawn]"
                );
                raw = raw.replace("{target}", target.getName());
                String colored = ChatColor.translateAlternateColorCodes('&', raw);
                TextComponent comp = new TextComponent(colored);
                comp.setClickEvent(new ClickEvent(
                        ClickEvent.Action.RUN_COMMAND,
                        "/tpo " + target.getName() + " force"
                ));
                player.spigot().sendMessage(comp);
                return true;
            }

            loc = plugin.getSpawnManager().getSpawnLocation();
            if (loc == null || loc.getWorld() == null) {
                msg.send(player, "tpo.no-spawn");
                return true;
            }
        }

        TeleportManager tpMgr = plugin.getTeleportManager();
        tpMgr.startTeleport(
                player,
                loc,
                true,
                null,
                "teleport.warmup-start",
                null,
                TeleportManager.TeleportFlags.instant()
        );

        msg.send(player, "tpo.success", Map.of(
                "target", target.getName(),
                "world", loc.getWorld().getName()
        ));
        return true;
    }
}
