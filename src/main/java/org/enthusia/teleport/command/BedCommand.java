package org.enthusia.teleport.command;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.home.BedHome;
import org.enthusia.teleport.home.BedHomeManager;
import org.enthusia.teleport.teleport.TeleportManager;
import org.enthusia.teleport.util.Messages;

import java.util.Collection;
import java.util.Locale;

/**
 * Teleports players to persistent named bed homes and manages those homes.
 */
public class BedCommand implements CommandExecutor {

    private final EnthusiaTeleportPlugin plugin;

    public BedCommand(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages msg = plugin.getMessages();

        if (!(sender instanceof Player player)) {
            msg.send(sender, "generic.no-console");
            return true;
        }

        BedHomeManager beds = plugin.getBedHomeManager();
        beds.ensureMigrated(player);

        if (args.length == 0) {
            BedHome mostRecent = beds.getMostRecentBed(player.getUniqueId());
            if (mostRecent == null) {
                sendOrFallback(msg, player, "bed.not-set", "&cYou do not have a bed home set.");
                return true;
            }
            teleportToBed(player, mostRecent);
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "list" -> listBeds(player);
            case "delete", "del", "remove" -> deleteBed(player, args);
            case "rename" -> renameBed(player, args);
            case "help" -> showUsage(player);
            default -> teleportNamedBed(player, args[0]);
        };
    }

    private boolean teleportNamedBed(Player player, String name) {
        BedHome home = plugin.getBedHomeManager().getBed(player.getUniqueId(), name);
        if (home == null) {
            player.sendMessage(plugin.getMessages().color("&cUnknown bed home: &e" + name + "&c."));
            return true;
        }
        teleportToBed(player, home);
        return true;
    }

    private void teleportToBed(Player player, BedHome home) {
        BedHomeManager beds = plugin.getBedHomeManager();
        Messages msg = plugin.getMessages();
        if (!beds.isBedPresent(home)) {
            beds.deleteBed(player.getUniqueId(), home.getName());
            beds.saveAll();
            player.sendMessage(msg.color("&cYour bed (&e" + home.getName() + "&c) is missing."));
            return;
        }

        Location location = home.toLocation();
        if (location == null || location.getWorld() == null) {
            sendOrFallback(msg, player, "bed.missing", "&cYour bed is missing or unavailable.");
            return;
        }

        TeleportManager tpMgr = plugin.getTeleportManager();
        if (msg.raw("bed.warmup-start") == null) {
            player.sendMessage(msg.color("&eTeleporting to your bed. Don't move."));
        }
        tpMgr.startTeleport(player, location, true, null, "bed.warmup-start");
    }

    private boolean listBeds(Player player) {
        Collection<BedHome> homes = plugin.getBedHomeManager().getBeds(player.getUniqueId());
        if (homes.isEmpty()) {
            player.sendMessage(plugin.getMessages().color("&cYou do not have any bed homes set."));
            return true;
        }

        BedHome mostRecent = plugin.getBedHomeManager().getMostRecentBed(player.getUniqueId());
        player.sendMessage(plugin.getMessages().color("&6Your bed homes:"));
        for (BedHome home : homes) {
            String lastUsed = mostRecent != null && mostRecent.getKey().equals(home.getKey()) ? " &a(last used)" : "";
            player.sendMessage(plugin.getMessages().color(
                    "&7- &e" + home.getName() + " &7(" + home.getWorldName() + " "
                            + home.getBedX() + " " + home.getBedY() + " " + home.getBedZ() + ")" + lastUsed
            ));
        }
        return true;
    }

    private boolean deleteBed(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.getMessages().color("&cUsage: &e/bed delete <name>"));
            return true;
        }

        BedHome removed = plugin.getBedHomeManager().deleteBed(player.getUniqueId(), args[1]);
        if (removed == null) {
            player.sendMessage(plugin.getMessages().color("&cUnknown bed home: &e" + args[1] + "&c."));
            return true;
        }

        plugin.getBedHomeManager().saveAll();
        player.sendMessage(plugin.getMessages().color("&aDeleted bed home &e" + removed.getName() + "&a."));
        return true;
    }

    private boolean renameBed(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(plugin.getMessages().color("&cUsage: &e/bed rename <old_name> <new_name>"));
            return true;
        }

        BedHomeManager.RenameResult result = plugin.getBedHomeManager()
                .renameBed(player.getUniqueId(), args[1], args[2]);
        switch (result) {
            case SUCCESS -> {
                plugin.getBedHomeManager().saveAll();
                player.sendMessage(plugin.getMessages().color(
                        "&aRenamed bed home &e" + args[1] + " &ato &e" + args[2] + "&a."
                ));
            }
            case NOT_FOUND -> player.sendMessage(plugin.getMessages().color(
                    "&cUnknown bed home: &e" + args[1] + "&c."
            ));
            case INVALID_NAME -> player.sendMessage(plugin.getMessages().color(
                    "&cBed names cannot be empty, contain dots, or use a /bed subcommand name."
            ));
            case DUPLICATE -> player.sendMessage(plugin.getMessages().color(
                    "&cYou already have a bed home named &e" + args[2] + "&c."
            ));
        }
        return true;
    }

    private boolean showUsage(Player player) {
        player.sendMessage(plugin.getMessages().color("&e/bed &7- teleport to your most recently used bed"));
        player.sendMessage(plugin.getMessages().color("&e/bed <name> &7- teleport to a named bed"));
        player.sendMessage(plugin.getMessages().color("&e/bed list &7- list your bed homes"));
        player.sendMessage(plugin.getMessages().color("&e/bed delete <name> &7- delete a bed home"));
        player.sendMessage(plugin.getMessages().color("&e/bed rename <old> <new> &7- rename a bed home"));
        return true;
    }

    private void sendOrFallback(Messages msg, Player player, String key, String fallback) {
        String raw = msg.raw(key);
        if (raw == null || raw.isEmpty()) {
            player.sendMessage(msg.color(fallback));
        } else {
            msg.send(player, key);
        }
    }
}
