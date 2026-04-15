package org.enthusia.teleport.log;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MessageLogManager {

    public static class LogEntry {
        public final long timestamp;
        public final String type; // "msg" or "chat"
        public final String sender;
        public final List<String> recipients;
        public final String message;

        public LogEntry(long timestamp, String type, String sender, List<String> recipients, String message) {
            this.timestamp = timestamp;
            this.type = type;
            this.sender = sender;
            this.recipients = recipients;
            this.message = message;
        }
    }

    public static class CachedQuery {
        public final List<LogEntry> entries;
        public final long createdAt;

        public CachedQuery(List<LogEntry> entries, long createdAt) {
            this.entries = entries;
            this.createdAt = createdAt;
        }
    }

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final EnthusiaTeleportPlugin plugin;
    private final Path logDir;
    private final Object writeLock = new Object();
    private final Map<UUID, CachedQuery> lastQueryByViewer = new HashMap<>();

    public MessageLogManager(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
        this.logDir = plugin.getDataFolder().toPath().resolve("logs");
    }

    public void logMsg(Player sender, List<Player> recipients, String message) {
        List<String> names = recipients.stream()
                .map(Player::getName)
                .filter(Objects::nonNull)
                .toList();
        writeEntry(new LogEntry(System.currentTimeMillis(), "msg", sender.getName(), names, message));
    }

    public void logChat(Player sender, String message) {
        writeEntry(new LogEntry(System.currentTimeMillis(), "chat", sender.getName(), Collections.emptyList(), message));
    }

    public CachedQuery getCachedQuery(UUID viewerId) {
        return lastQueryByViewer.get(viewerId);
    }

    public void cacheQuery(UUID viewerId, List<LogEntry> entries) {
        lastQueryByViewer.put(viewerId, new CachedQuery(entries, System.currentTimeMillis()));
    }

    public void clearCache() {
        lastQueryByViewer.clear();
    }

    public List<LogEntry> query(long startMillis,
                                long endMillis,
                                String from,
                                String to,
                                String contains) {
        List<LogEntry> out = new ArrayList<>();
        String fromLower = from != null ? from.toLowerCase(Locale.ROOT) : null;
        String toLower = to != null ? to.toLowerCase(Locale.ROOT) : null;
        String containsLower = contains != null ? contains.toLowerCase(Locale.ROOT) : null;

        for (Path file : listFilesBetween(startMillis, endMillis)) {
            readFile(file, entry -> {
                if (entry.timestamp < startMillis || entry.timestamp > endMillis) return;
                if (!"msg".equalsIgnoreCase(entry.type)) return;
                if (fromLower != null && !entry.sender.toLowerCase(Locale.ROOT).equals(fromLower)) return;
                if (toLower != null && (entry.recipients == null
                        || entry.recipients.stream().noneMatch(r -> r.equalsIgnoreCase(toLower)))) return;
                if (containsLower != null && (entry.message == null
                        || !entry.message.toLowerCase(Locale.ROOT).contains(containsLower))) return;
                out.add(entry);
            });
        }
        out.sort(Comparator.comparingLong(e -> e.timestamp));
        return out;
    }

    public List<LogEntry> context(long centerMillis, long windowMillis, int limit) {
        long start = centerMillis - windowMillis;
        long end = centerMillis + windowMillis;
        List<LogEntry> entries = new ArrayList<>();
        for (Path file : listFilesBetween(start, end)) {
            readFile(file, entry -> {
                if (entry.timestamp >= start && entry.timestamp <= end) {
                    entries.add(entry);
                }
            });
        }
        entries.sort(Comparator.comparingLong(e -> e.timestamp));
        if (entries.size() <= limit) return entries;

        int centerIndex = 0;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).timestamp >= centerMillis) {
                centerIndex = i;
                break;
            }
        }
        int half = limit / 2;
        int startIdx = Math.max(0, centerIndex - half);
        int endIdx = Math.min(entries.size(), startIdx + limit);
        return entries.subList(startIdx, endIdx);
    }

    private void writeEntry(LogEntry entry) {
        try {
            Files.createDirectories(logDir);
            Path file = logDir.resolve("msg-" + LocalDate.now().format(DATE_FORMAT) + ".log");
            String line = encode(entry);
            synchronized (writeLock) {
                try (BufferedWriter writer = Files.newBufferedWriter(
                        file,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                )) {
                    writer.write(line);
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write msg log: " + e.getMessage());
        }
    }

    private void readFile(Path file, java.util.function.Consumer<LogEntry> consumer) {
        if (!Files.exists(file)) return;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                LogEntry entry = decode(line);
                if (entry != null) consumer.accept(entry);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to read msg log: " + file.getFileName() + " (" + e.getMessage() + ")");
        }
    }

    private List<Path> listFilesBetween(long startMillis, long endMillis) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate start = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate();
        LocalDate end = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate();

        List<Path> files = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            Path file = logDir.resolve("msg-" + date.format(DATE_FORMAT) + ".log");
            files.add(file);
        }
        return files;
    }

    private String encode(LogEntry entry) {
        String recipients = entry.recipients == null || entry.recipients.isEmpty()
                ? ""
                : String.join(",", entry.recipients);
        String payload = Base64.getEncoder().encodeToString(
                entry.message == null ? new byte[0] : entry.message.getBytes(StandardCharsets.UTF_8)
        );
        return entry.timestamp + "|" + entry.type + "|" + sanitize(entry.sender) + "|" + sanitize(recipients) + "|" + payload;
    }

    private LogEntry decode(String line) {
        String[] parts = line.split("\\|", 5);
        if (parts.length < 5) return null;
        try {
            long ts = Long.parseLong(parts[0]);
            String type = parts[1];
            String sender = parts[2];
            String recipientsRaw = parts[3];
            String message = new String(Base64.getDecoder().decode(parts[4]), StandardCharsets.UTF_8);
            List<String> recipients = recipientsRaw.isEmpty()
                    ? Collections.emptyList()
                    : Arrays.asList(recipientsRaw.split(","));
            return new LogEntry(ts, type, sender, recipients, message);
        } catch (Exception e) {
            return null;
        }
    }

    private String sanitize(String value) {
        if (value == null) return "";
        return value.replace("|", "").trim();
    }
}
