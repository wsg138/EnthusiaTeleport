package org.enthusia.teleport.command;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.log.MessageLogManager;
import org.enthusia.teleport.log.MessageLogManager.CachedQuery;
import org.enthusia.teleport.log.MessageLogManager.LogEntry;
import org.enthusia.teleport.util.Messages;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MsgLogCommand implements CommandExecutor {

    private static final int PAGE_SIZE = 8;
    private static final long CACHE_TTL_MS = 10 * 60 * 1000L;
    private static final long CONTEXT_WINDOW_MS = 5 * 60 * 1000L;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final EnthusiaTeleportPlugin plugin;
    private final MessageLogManager logManager;

    public MsgLogCommand(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
        this.logManager = plugin.getMessageLogManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages msg = plugin.getMessages();

        if (!(sender instanceof Player player)) {
            msg.send(sender, "generic.no-console");
            return true;
        }

        if (!player.hasPermission("enthusia.teleport.msglog")) {
            msg.send(player, "generic.no-permission");
            return true;
        }

        if (args.length == 0) {
            msg.send(player, "msglog.usage");
            return true;
        }

        if (args[0].equalsIgnoreCase("view")) {
            return handleView(player, Arrays.copyOfRange(args, 1, args.length), msg);
        }

        long windowMillis = parseDuration(args[0]);
        if (windowMillis <= 0) {
            msg.send(player, "msglog.usage");
            return true;
        }

        int page = 1;
        int index = 1;
        if (args.length >= 2 && isNumber(args[1])) {
            page = Math.max(1, Integer.parseInt(args[1]));
            index = 2;
        }

        String from = null;
        String to = null;
        String contains = null;
        while (index < args.length) {
            String token = args[index];
            if (token.equalsIgnoreCase("--from") && index + 1 < args.length) {
                from = args[index + 1];
                index += 2;
                continue;
            }
            if (token.equalsIgnoreCase("--to") && index + 1 < args.length) {
                to = args[index + 1];
                index += 2;
                continue;
            }
            if (token.equalsIgnoreCase("--contains") && index + 1 < args.length) {
                StringBuilder builder = new StringBuilder();
                index++;
                while (index < args.length && !args[index].startsWith("--")) {
                    if (!builder.isEmpty()) builder.append(' ');
                    builder.append(args[index]);
                    index++;
                }
                contains = builder.toString();
                continue;
            }
            msg.send(player, "msglog.usage");
            return true;
        }

        long now = System.currentTimeMillis();
        long start = now - windowMillis;
        List<LogEntry> results = logManager.query(start, now, from, to, contains);
        logManager.cacheQuery(player.getUniqueId(), results);

        if (results.isEmpty()) {
            msg.send(player, "msglog.empty");
            return true;
        }

        int totalPages = (int) Math.ceil(results.size() / (double) PAGE_SIZE);
        page = Math.min(page, totalPages);

        String header = msg.rawOr("msglog.header", "&8[&bMsgLog&8] &7Last &e{window}&7 &8(Page {page}/{pages})");
        header = header.replace("{window}", args[0])
                .replace("{page}", String.valueOf(page))
                .replace("{pages}", String.valueOf(totalPages));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', header));

        int startIndex = (page - 1) * PAGE_SIZE;
        int endIndex = Math.min(results.size(), startIndex + PAGE_SIZE);
        for (int i = startIndex; i < endIndex; i++) {
            LogEntry entry = results.get(i);
            String line = formatEntry(entry, i + 1, false);
            TextComponent comp = new TextComponent(ChatColor.translateAlternateColorCodes('&', line));
            comp.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/msglog view " + (i + 1)));
            player.spigot().sendMessage(comp);
        }

        return true;
    }

    private boolean handleView(Player player, String[] args, Messages msg) {
        if (args.length < 1 || !isNumber(args[0])) {
            msg.send(player, "msglog.view-usage");
            return true;
        }

        CachedQuery cached = logManager.getCachedQuery(player.getUniqueId());
        if (cached == null || System.currentTimeMillis() - cached.createdAt > CACHE_TTL_MS) {
            msg.send(player, "msglog.cache-expired");
            return true;
        }

        int index = Integer.parseInt(args[0]) - 1;
        if (index < 0 || index >= cached.entries.size()) {
            msg.send(player, "msglog.invalid-index");
            return true;
        }

        LogEntry entry = cached.entries.get(index);
        List<LogEntry> context = logManager.context(entry.timestamp, CONTEXT_WINDOW_MS, 10);

        String header = msg.rawOr("msglog.context-header", "&8[&bMsgLog&8] &7Context around &e#{index}");
        header = header.replace("{index}", String.valueOf(index + 1));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', header));

        for (LogEntry ctx : context) {
            boolean highlight = ctx.timestamp == entry.timestamp
                    && Objects.equals(ctx.sender, entry.sender)
                    && Objects.equals(ctx.message, entry.message);
            String line = formatEntry(ctx, -1, highlight);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', line));
        }
        return true;
    }

    private String formatEntry(LogEntry entry, int index, boolean highlight) {
        String time = TIME_FORMAT.format(Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.systemDefault()));
        String prefix = highlight ? "&e> " : "&8  ";
        String idx = index > 0 ? "&8#" + index + " " : "";
        if ("chat".equalsIgnoreCase(entry.type)) {
            return prefix + idx + "&7[" + time + "] &aChat &7" + entry.sender + "&8: &f" + entry.message;
        }
        String targets = entry.recipients == null || entry.recipients.isEmpty()
                ? "?"
                : String.join(",", entry.recipients);
        return prefix + idx + "&7[" + time + "] &bMsg &e" + entry.sender + " &7→ &e" + targets + "&8: &f" + entry.message;
    }

    private long parseDuration(String token) {
        if (token == null || token.length() < 2) return -1;
        char suffix = Character.toLowerCase(token.charAt(token.length() - 1));
        String num = token.substring(0, token.length() - 1);
        if (!isNumber(num)) return -1;
        long value = Long.parseLong(num);
        return switch (suffix) {
            case 's' -> value * 1000L;
            case 'm' -> value * 60_000L;
            case 'h' -> value * 3_600_000L;
            case 'd' -> value * 86_400_000L;
            default -> -1;
        };
    }

    private boolean isNumber(String value) {
        if (value == null || value.isEmpty()) return false;
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) return false;
        }
        return true;
    }
}
