package org.enthusia.teleport.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.home.Home;
import org.enthusia.teleport.home.HomeManager;
import org.enthusia.teleport.request.TeleportRequestManager;

import java.util.*;
import java.util.stream.Collectors;

import static org.enthusia.teleport.command.CommandStrings.ignoresEqualCase;

public class TeleportTabCompleter implements TabCompleter {

    private final EnthusiaTeleportPlugin plugin;

    public TeleportTabCompleter(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {
        String name = command.getName();

        // TPA / tpask / tpahere
        if (ignoresEqualCase(name, "tpa")
                || ignoresEqualCase(name, "tpask")
                || ignoresEqualCase(name, "tpahere")) {
            return tabPlayers(sender, args, 0);
        }

        // /tpaccept [player], /tpadeny [player]
        if (ignoresEqualCase(name, "tpaccept") || ignoresEqualCase(name, "tpadeny")) {
            return tabIncomingRequests(sender, args, 0);
        }

        // /tpignore <player|list>
        if (ignoresEqualCase(name, "tpignore")) {
            return tabTpIgnore(sender, args);
        }

        // Homes
        if (ignoresEqualCase(name, "home")) {
            return tabHome(sender, args);
        }
        if (ignoresEqualCase(name, "delhome")) {
            return tabDelHome(sender, args);
        }
        if (ignoresEqualCase(name, "sethome")) {
            return Collections.emptyList();
        }

        // Inventory viewing
        if (ignoresEqualCase(name, "invsee")
                || ignoresEqualCase(name, "inventorysee")
                || ignoresEqualCase(name, "endersee")
                || ignoresEqualCase(name, "enderview")) {
            return tabPlayers(sender, args, 0);
        }

        // /msg <player[,player2,...]>
        if (ignoresEqualCase(name, "msg")) {
            return tabMsgTargets(sender, args);
        }

        // /msglog <duration> [page] [--from <player>] [--to <player>] [--contains <text>]
        if (ignoresEqualCase(name, "msglog")) {
            return tabMsgLog(args);
        }

        // /tppos <x> <y> <z> [world]
        if (ignoresEqualCase(name, "tppos")) {
            return tabTppos(sender, args);
        }

        // /tpo <offline-player>
        if (ignoresEqualCase(name, "tpo")) {
            return tabOfflinePlayers(args);
        }

        // /eteleport reload
        if (ignoresEqualCase(name, "eteleport")) {
            return tabEteleport(args);
        }
        if (ignoresEqualCase(name, "ahome") || ignoresEqualCase(name, "adminhome")) {
            return tabAdminHome(args);
        }

        // /rtp, /top, /spawn, /tpacancel -> no args, so nothing special
        return Collections.emptyList();
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private List<String> tabPlayers(CommandSender sender, String[] args, int argIndex) {
        if (args.length == 0 || args.length - 1 != argIndex) return Collections.emptyList();

        String prefix = args[argIndex].toLowerCase(Locale.ROOT);

        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    private List<String> tabMsgTargets(CommandSender sender, String[] args) {
        if (args.length != 1) return Collections.emptyList();

        String raw = args[0];
        int lastComma = raw.lastIndexOf(',');

        String base = lastComma >= 0 ? raw.substring(0, lastComma + 1) : "";
        String prefix = lastComma >= 0 ? raw.substring(lastComma + 1).trim() : raw;

        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        String finalBase = base;
        Set<String> already = new HashSet<>();
        if (lastComma >= 0) {
            String[] parts = raw.substring(0, lastComma).split(",");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    already.add(trimmed.toLowerCase(Locale.ROOT));
                }
            }
        }

        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> !already.contains(name.toLowerCase(Locale.ROOT)))
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lowerPrefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .map(name -> finalBase + name)
                .collect(Collectors.toList());
    }

    private List<String> tabTpIgnore(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> suggestions = new ArrayList<>();

            // "list" option
            if ("list".startsWith(prefix)) {
                suggestions.add("list");
            }

            // player names
            suggestions.addAll(
                    Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                            .sorted(String.CASE_INSENSITIVE_ORDER)
                            .collect(Collectors.toList())
            );

            return suggestions;
        }

        return Collections.emptyList();
    }

    private List<String> tabHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }

        HomeManager hm = plugin.getHomeManager();

        if (args.length == 1) {
            // /home <name>
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return hm.getHomes(player.getUniqueId()).stream()
                    .map(Home::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            // /home <name> <force?>
            String second = args[1].toLowerCase(Locale.ROOT);
            if ("force".startsWith(second)) {
                return Collections.singletonList("force");
            }
        }

        return Collections.emptyList();
    }

    private List<String> tabDelHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }

        if (args.length != 1) return Collections.emptyList();

        String prefix = args[0].toLowerCase(Locale.ROOT);
        HomeManager hm = plugin.getHomeManager();

        return hm.getHomes(player.getUniqueId()).stream()
                .map(Home::getName)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    private List<String> tabTppos(CommandSender sender, String[] args) {
        // Only suggest world names for 4th argument
        if (args.length != 4) return Collections.emptyList();

        String prefix = args[3].toLowerCase(Locale.ROOT);
        return Bukkit.getWorlds().stream()
                .map(World::getName)
                .filter(w -> w.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    private List<String> tabMsgLog(String[] args) {
        if (args.length == 1) {
            return Arrays.asList("10m", "30m", "1h", "6h", "1d", "view");
        }

        if (args.length >= 2 && ignoresEqualCase(args[0], "view")) {
            return Collections.emptyList();
        }

        String last = args[args.length - 1];
        if ("--from".startsWith(last) || "--to".startsWith(last) || "--contains".startsWith(last)) {
            return Arrays.asList("--from", "--to", "--contains").stream()
                    .filter(opt -> opt.startsWith(last))
                    .collect(Collectors.toList());
        }

        if (args.length >= 2) {
            String prev = args[args.length - 2];
            if (ignoresEqualCase(prev, "--from") || ignoresEqualCase(prev, "--to")) {
                return plugin.getOfflineNameCache().suggest(last, false);
            }
        }

        if (args.length == 2) {
            if (last.chars().allMatch(Character::isDigit)) {
                return Collections.singletonList("2");
            }
        }

        if (!last.startsWith("--")) {
            return Arrays.asList("--from", "--to", "--contains");
        }

        return Collections.emptyList();
    }

    private List<String> tabOfflinePlayers(String[] args) {
        if (args.length != 1) return Collections.emptyList();

        String prefix = args[0].toLowerCase(Locale.ROOT);
        return plugin.getOfflineNameCache().suggest(prefix, true);
    }

    private List<String> tabIncomingRequests(CommandSender sender, String[] args, int argIndex) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }
        if (args.length == 0 || args.length - 1 != argIndex) return Collections.emptyList();

        String prefix = args[argIndex].toLowerCase(Locale.ROOT);
        TeleportRequestManager reqMgr = plugin.getRequestManager();

        return reqMgr.getIncomingRequests(player).stream()
                .map(req -> Bukkit.getPlayer(req.getSender()))
                .filter(Objects::nonNull)
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    private List<String> tabEteleport(String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> options = Arrays.asList("reload", "performance", "homes");
            return options.stream()
                    .filter(opt -> opt.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        if (args.length >= 2 && ignoresEqualCase(args[0], "homes")) {
            String actionPrefix = args[1].toLowerCase(Locale.ROOT);
            if (args.length == 2) {
                List<String> actions = Arrays.asList("clear", "del", "tp");
                return actions.stream()
                        .filter(opt -> opt.startsWith(actionPrefix))
                        .collect(Collectors.toList());
            }

            if (args.length == 3) {
                String prefix = args[2].toLowerCase(Locale.ROOT);
                return plugin.getOfflineNameCache().suggest(prefix, false);
            }

            if (args.length == 4 && (ignoresEqualCase(args[1], "del") || ignoresEqualCase(args[1], "tp"))) {
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
                if (target == null || (!target.isOnline() && !target.hasPlayedBefore())) {
                    return Collections.emptyList();
                }
                String prefix = args[3].toLowerCase(Locale.ROOT);
                HomeManager hm = plugin.getHomeManager();
                return hm.getHomes(target.getUniqueId()).stream()
                        .map(Home::getName)
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }

    private List<String> tabAdminHome(String[] args) {
        if (args.length != 1) return Collections.emptyList();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return plugin.getOfflineNameCache().suggest(prefix, false);
    }
}
