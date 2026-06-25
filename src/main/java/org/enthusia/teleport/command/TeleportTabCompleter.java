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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.enthusia.teleport.command.CommandStrings.ignoresEqualCase;

public class TeleportTabCompleter implements TabCompleter {
    private static final int NO_ARGUMENTS = 0;
    private static final int FIRST_ARGUMENT = 1;
    private static final int SECOND_ARGUMENT = 2;
    private static final int THIRD_ARGUMENT = 3;
    private static final int FOURTH_ARGUMENT = 4;
    private static final List<String> MSGLOG_FILTER_OPTIONS = Arrays.asList("--from", "--to", "--contains");
    private static final List<String> MSGLOG_DURATION_OPTIONS = Arrays.asList("10m", "30m", "1h", "6h", "1d", "view");
    private static final List<String> ETELEPORT_OPTIONS = Arrays.asList("reload", "performance", "homes");
    private static final List<String> ETELEPORT_HOME_ACTIONS = Arrays.asList("clear", "del", "tp");

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
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "tpa", "tpask", "tpahere", "invsee", "inventorysee", "endersee", "enderview" ->
                    tabPlayers(args, 0);
            case "tpaccept", "tpadeny" -> tabIncomingRequests(sender, args, 0);
            case "tpignore" -> tabTpIgnore(sender, args);
            case "home" -> tabHome(sender, args);
            case "delhome" -> tabDelHome(sender, args);
            case "msg" -> tabMsgTargets(args);
            case "msglog" -> tabMsgLog(args);
            case "tppos" -> tabTppos(args);
            case "tpo" -> tabOfflinePlayers(args);
            case "eteleport" -> tabEteleport(args);
            case "ahome", "adminhome" -> tabAdminHome(args);
            default -> Collections.emptyList();
        };
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private List<String> tabPlayers(String[] args, int argIndex) {
        if (args.length == NO_ARGUMENTS || args.length - 1 != argIndex) return Collections.emptyList();

        String prefix = args[argIndex].toLowerCase(Locale.ROOT);

        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    private List<String> tabMsgTargets(String[] args) {
        if (args.length != FIRST_ARGUMENT) return Collections.emptyList();

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

        if (args.length == FIRST_ARGUMENT) {
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

        if (args.length == FIRST_ARGUMENT) {
            // /home <name>
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return hm.getHomes(player.getUniqueId()).stream()
                    .map(Home::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }

        if (args.length == SECOND_ARGUMENT) {
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

        if (args.length != FIRST_ARGUMENT) return Collections.emptyList();

        String prefix = args[0].toLowerCase(Locale.ROOT);
        HomeManager hm = plugin.getHomeManager();

        return hm.getHomes(player.getUniqueId()).stream()
                .map(Home::getName)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    private List<String> tabTppos(String[] args) {
        // Only suggest world names for 4th argument
        if (args.length != FOURTH_ARGUMENT) return Collections.emptyList();

        String prefix = args[3].toLowerCase(Locale.ROOT);
        return Bukkit.getWorlds().stream()
                .map(World::getName)
                .filter(w -> w.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    private List<String> tabMsgLog(String[] args) {
        if (args.length == FIRST_ARGUMENT) {
            return MSGLOG_DURATION_OPTIONS;
        }

        if (args.length >= SECOND_ARGUMENT && ignoresEqualCase(args[0], "view")) {
            return Collections.emptyList();
        }

        String last = args[args.length - 1];
        if (isPartialMsgLogFilter(last)) {
            return suggestMsgLogFilters(last);
        }

        if (isPlayerFilterValue(args)) {
            return plugin.getOfflineNameCache().suggest(last, false);
        }

        if (args.length == SECOND_ARGUMENT && last.chars().allMatch(Character::isDigit)) {
            return Collections.singletonList("2");
        }

        if (!last.startsWith("--")) {
            return MSGLOG_FILTER_OPTIONS;
        }

        return Collections.emptyList();
    }

    private boolean isPartialMsgLogFilter(String value) {
        return MSGLOG_FILTER_OPTIONS.stream().anyMatch(option -> option.startsWith(value));
    }

    private List<String> suggestMsgLogFilters(String prefix) {
        return MSGLOG_FILTER_OPTIONS.stream()
                .filter(option -> option.startsWith(prefix))
                .collect(Collectors.toList());
    }

    private boolean isPlayerFilterValue(String[] args) {
        if (args.length < SECOND_ARGUMENT) {
            return false;
        }
        String previous = args[args.length - 2];
        return ignoresEqualCase(previous, "--from") || ignoresEqualCase(previous, "--to");
    }

    private List<String> tabOfflinePlayers(String[] args) {
        if (args.length != FIRST_ARGUMENT) return Collections.emptyList();

        String prefix = args[0].toLowerCase(Locale.ROOT);
        return plugin.getOfflineNameCache().suggest(prefix, true);
    }

    private List<String> tabIncomingRequests(CommandSender sender, String[] args, int argIndex) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }
        if (args.length == NO_ARGUMENTS || args.length - 1 != argIndex) return Collections.emptyList();

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
        if (args.length == FIRST_ARGUMENT) {
            return filterOptions(ETELEPORT_OPTIONS, args[0]);
        }

        if (args.length < SECOND_ARGUMENT || !ignoresEqualCase(args[0], "homes")) {
            return Collections.emptyList();
        }

        if (args.length == SECOND_ARGUMENT) {
            return filterOptions(ETELEPORT_HOME_ACTIONS, args[1]);
        }

        if (args.length == THIRD_ARGUMENT) {
            return plugin.getOfflineNameCache().suggest(args[2].toLowerCase(Locale.ROOT), false);
        }

        if (args.length == FOURTH_ARGUMENT && isHomeSpecificAdminAction(args[1])) {
            return tabAdminHomeNames(args[2], args[3]);
        }

        return Collections.emptyList();
    }

    private List<String> filterOptions(List<String> options, String prefix) {
        String normalizedPrefix = prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.startsWith(normalizedPrefix))
                .collect(Collectors.toList());
    }

    private boolean isHomeSpecificAdminAction(String action) {
        return ignoresEqualCase(action, "del") || ignoresEqualCase(action, "tp");
    }

    private List<String> tabAdminHomeNames(String playerName, String homePrefix) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target == null || (!target.isOnline() && !target.hasPlayedBefore())) {
            return Collections.emptyList();
        }

        String prefix = homePrefix.toLowerCase(Locale.ROOT);
        HomeManager hm = plugin.getHomeManager();
        return hm.getHomes(target.getUniqueId()).stream()
                .map(Home::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    private List<String> tabAdminHome(String[] args) {
        if (args.length != FIRST_ARGUMENT) return Collections.emptyList();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return plugin.getOfflineNameCache().suggest(prefix, false);
    }
}
