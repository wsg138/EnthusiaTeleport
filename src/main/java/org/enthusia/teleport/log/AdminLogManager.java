package org.enthusia.teleport.log;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.home.Home;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AdminLogManager {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final EnthusiaTeleportPlugin plugin;
    private final Path logDir;
    private final Object writeLock = new Object();
    private final Queue<AdminLogEntry> queue = new ConcurrentLinkedQueue<>();

    public AdminLogManager(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
        this.logDir = plugin.getDataFolder().toPath().resolve("logs");
    }

    public void logHomeTeleport(CommandSender actor, OfflinePlayer target, Home home, Location dest) {
        queueEntry("home_teleport", actor, target, home, dest);
    }

    public void logHomeDelete(CommandSender actor, OfflinePlayer target, Home home) {
        Location loc = home != null ? home.toLocation() : null;
        queueEntry("home_delete", actor, target, home, loc);
    }

    private void queueEntry(String action, CommandSender actor, OfflinePlayer target, Home home, Location loc) {
        long now = System.currentTimeMillis();
        String iso = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).format(TS_FORMAT);
        String actorName = actor != null ? actor.getName() : "unknown";
        String targetName = target != null && target.getName() != null ? target.getName() : "unknown";
        String homeName = home != null ? home.getName() : "";
        String world = loc != null && loc.getWorld() != null ? loc.getWorld().getName() : "";
        String x = loc != null ? format(loc.getX()) : "";
        String y = loc != null ? format(loc.getY()) : "";
        String z = loc != null ? format(loc.getZ()) : "";

        String line = now + "|" + iso + "|" + sanitize(action) + "|" + sanitize(actorName)
                + "|" + sanitize(targetName) + "|" + sanitize(homeName)
                + "|" + sanitize(world) + "|" + x + "|" + y + "|" + z;

        int maxQueueSize = plugin.getPluginConfigManager().current().logging().maxQueueSize();
        if (queue.size() >= maxQueueSize) {
            plugin.getPerformanceMonitor().increment("logs.admin.dropped");
            return;
        }
        queue.add(new AdminLogEntry(now, line));
        plugin.getPerformanceMonitor().increment("logs.admin.queued");
    }

    public void flushQueuedAsync() {
        List<AdminLogEntry> drained = drainQueue();
        if (drained.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> writeEntries(drained));
    }

    public void flushBlocking() {
        List<AdminLogEntry> drained = drainQueue();
        if (!drained.isEmpty()) {
            writeEntries(drained);
        }
    }

    private List<AdminLogEntry> drainQueue() {
        List<AdminLogEntry> drained = new ArrayList<>();
        AdminLogEntry entry;
        while ((entry = queue.poll()) != null) {
            drained.add(entry);
        }
        plugin.getPerformanceMonitor().add("logs.admin.queue_size", queue.size());
        return drained;
    }

    private void writeEntries(List<AdminLogEntry> entries) {
        try {
            Files.createDirectories(logDir);
            synchronized (writeLock) {
                java.util.Map<Path, List<String>> byFile = new java.util.LinkedHashMap<>();
                ZoneId zone = ZoneId.systemDefault();
                for (AdminLogEntry entry : entries) {
                    LocalDate date = Instant.ofEpochMilli(entry.timestamp()).atZone(zone).toLocalDate();
                    Path file = logDir.resolve("admin-" + date.format(DATE_FORMAT) + ".log");
                    byFile.computeIfAbsent(file, unused -> new ArrayList<>()).add(entry.line());
                }
                for (java.util.Map.Entry<Path, List<String>> fileEntry : byFile.entrySet()) {
                    try (BufferedWriter writer = Files.newBufferedWriter(
                            fileEntry.getKey(),
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND
                    )) {
                        for (String line : fileEntry.getValue()) {
                            writer.write(line);
                            writer.newLine();
                        }
                    }
                }
            }
            plugin.getPerformanceMonitor().add("logs.admin.flushed", entries.size());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write admin log: " + e.getMessage());
            plugin.getPerformanceMonitor().add("logs.admin.failed", entries.size());
        }
    }

    private String sanitize(String value) {
        if (value == null) return "";
        return value.replace("|", "").trim();
    }

    private String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private record AdminLogEntry(long timestamp, String line) {
    }
}
