package org.enthusia.teleport.command;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.teleport.TeleportManager;
import org.enthusia.teleport.util.Messages;

import java.util.Map;

public class TpCommand implements CommandExecutor {

    private final EnthusiaTeleportPlugin plugin;

    public TpCommand(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages msg = plugin.getMessages();

        if (sender instanceof Player player && !player.hasPermission("enthusia.teleport.tp")) {
            msg.send(sender, "generic.no-permission");
            return true;
        }

        if (args.length == 0) {
            msg.send(sender, "tp.usage");
            return true;
        }

        if (args.length == 1 && sender instanceof Player playerSender) {
            String targetName = normalizeName(args[0]);
            Player target = findPlayer(targetName);
            if (target == null) {
                msg.send(sender, "tp.player-not-found", Map.of("target", targetName));
                return true;
            }
            if (target.equals(playerSender)) {
                msg.send(sender, "tp.self");
                return true;
            }
            teleportToPlayer(sender, playerSender, target, msg);
            return true;
        }

        if (tryHandleCoordinates(sender, args, msg)) {
            return true;
        }

        return handlePlayerTeleport(sender, args, msg);
    }

    private boolean tryHandleCoordinates(CommandSender sender, String[] args, Messages msg) {
        int coordStart = -1;
        boolean hasWorld = false;

        if (args.length >= 3 && areNumbers(args[0], args[1], args[2])) {
            coordStart = 0;
            hasWorld = args.length >= 4;
        } else if (args.length >= 5 && areNumbers(args[args.length - 4], args[args.length - 3], args[args.length - 2])) {
            coordStart = args.length - 4;
            hasWorld = true;
        } else if (args.length >= 4 && areNumbers(args[args.length - 3], args[args.length - 2], args[args.length - 1])) {
            coordStart = args.length - 3;
            hasWorld = false;
        }

        if (coordStart == -1) return false;

        if (coordStart == 0) {
            if (!(sender instanceof Player player)) {
                msg.send(sender, "generic.no-console");
                return true;
            }
            teleportToCoords(sender, player, args, coordStart, hasWorld, msg);
            return true;
        }

        String name = joinArgs(args, 0, coordStart);
        Player target = findPlayer(name);
        if (target == null) {
            msg.send(sender, "tp.player-not-found", Map.of("target", name));
            return true;
        }

        teleportToCoords(sender, target, args, coordStart, hasWorld, msg);
        return true;
    }

    private boolean handlePlayerTeleport(CommandSender sender, String[] args, Messages msg) {
        String fullName = normalizeName(joinArgs(args, 0, args.length));
        Player directTarget = findPlayer(fullName);

        if (sender instanceof Player playerSender) {
            if (directTarget != null) {
                if (directTarget.equals(playerSender)) {
                    msg.send(sender, "tp.self");
                    return true;
                }
                teleportToPlayer(sender, playerSender, directTarget, msg);
                return true;
            }

            SplitMatch match = findTwoPlayerSplit(args);
            if (match == null) {
                msg.send(sender, "tp.player-not-found", Map.of("target", fullName));
                return true;
            }
            if (match.ambiguous) {
                msg.send(sender, "tp.ambiguous");
                return true;
            }
            if (match.from.equals(match.to)) {
                msg.send(sender, "tp.self");
                return true;
            }
            teleportToPlayer(sender, match.from, match.to, msg);
            return true;
        }

        SplitMatch match = findTwoPlayerSplit(args);
        if (match == null) {
            msg.send(sender, "tp.usage");
            return true;
        }
        if (match.ambiguous) {
            msg.send(sender, "tp.ambiguous");
            return true;
        }
        if (match.from.equals(match.to)) {
            msg.send(sender, "tp.self");
            return true;
        }
        teleportToPlayer(sender, match.from, match.to, msg);
        return true;
    }

    private void teleportToPlayer(CommandSender sender, Player from, Player to, Messages msg) {
        TeleportManager tpMgr = plugin.getTeleportManager();
        tpMgr.startTeleport(
                from,
                to.getLocation(),
                true,
                to,
                "teleport.warmup-start",
                null,
                TeleportManager.TeleportFlags.instant()
        );

        if (!sender.equals(from)) {
            msg.send(sender, "tp.teleported-other",
                    Map.of("player", from.getName(), "target", to.getName()));
        }
    }

    private void teleportToCoords(CommandSender sender,
                                  Player target,
                                  String[] args,
                                  int coordStart,
                                  boolean hasWorld,
                                  Messages msg) {
        double x;
        double y;
        double z;
        try {
            x = Double.parseDouble(args[coordStart]);
            y = Double.parseDouble(args[coordStart + 1]);
            z = Double.parseDouble(args[coordStart + 2]);
        } catch (NumberFormatException e) {
            msg.send(sender, "tp.usage");
            return;
        }

        World world;
        if (hasWorld) {
            world = Bukkit.getWorld(args[args.length - 1]);
            if (world == null) {
                msg.send(sender, "tp.invalid-world", Map.of("world", args[args.length - 1]));
                return;
            }
        } else if (sender instanceof Player senderPlayer) {
            world = senderPlayer.getWorld();
        } else {
            world = target.getWorld();
        }

        Location loc = new Location(world, x, y, z, target.getLocation().getYaw(), target.getLocation().getPitch());
        TeleportManager tpMgr = plugin.getTeleportManager();
        tpMgr.startTeleport(
                target,
                loc,
                false,
                null,
                "teleport.warmup-start",
                null,
                TeleportManager.TeleportFlags.instant()
        );

        if (!sender.equals(target)) {
            msg.send(sender, "tp.teleported-other-coords",
                    Map.of("player", target.getName(),
                            "x", String.valueOf(x),
                            "y", String.valueOf(y),
                            "z", String.valueOf(z),
                            "world", world.getName()));
        }
    }

    private Player findPlayer(String name) {
        if (name == null || name.isEmpty()) return null;
        Player exact = Bukkit.getPlayerExact(name);
        if (exact != null) return exact;
        Player partial = Bukkit.getPlayer(name);
        if (partial != null) return partial;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(name)) {
                return player;
            }
        }
        return null;
    }

    private SplitMatch findTwoPlayerSplit(String[] args) {
        SplitMatch match = null;
        for (int i = 1; i < args.length; i++) {
            String left = joinArgs(args, 0, i);
            String right = joinArgs(args, i, args.length);
            Player from = findPlayer(left);
            Player to = findPlayer(right);
            if (from == null || to == null) continue;
            if (match != null) {
                match.ambiguous = true;
                return match;
            }
            match = new SplitMatch(from, to, false);
        }
        return match;
    }

    private String joinArgs(String[] args, int start, int endExclusive) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < endExclusive; i++) {
            if (i > start) builder.append(' ');
            builder.append(args[i]);
        }
        return builder.toString();
    }

    private String normalizeName(String name) {
        if (name == null) return null;
        String trimmed = name.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private boolean areNumbers(String a, String b, String c) {
        return isNumber(a) && isNumber(b) && isNumber(c);
    }

    private boolean isNumber(String value) {
        if (value == null || value.isEmpty()) return false;
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static class SplitMatch {
        final Player from;
        final Player to;
        boolean ambiguous;

        SplitMatch(Player from, Player to, boolean ambiguous) {
            this.from = from;
            this.to = to;
            this.ambiguous = ambiguous;
        }
    }
}
