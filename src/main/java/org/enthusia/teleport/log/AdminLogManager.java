package org.enthusia.teleport.log;

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

public class AdminLogManager {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final EnthusiaTeleportPlugin plugin;
    private final Path logDir;
    private final Object writeLock = new Object();

    public AdminLogManager(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
        this.logDir = plugin.getDataFolder().toPath().resolve("logs");
    }

    public void logHomeTeleport(CommandSender actor, OfflinePlayer target, Home home, Location dest) {
        writeEntry("home_teleport", actor, target, home, dest);
    }

    public void logHomeDelete(CommandSender actor, OfflinePlayer target, Home home) {
        Location loc = home != null ? home.toLocation() : null;
        writeEntry("home_delete", actor, target, home, loc);
    }

    private void writeEntry(String action, CommandSender actor, OfflinePlayer target, Home home, Location loc) {
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

        try {
            Files.createDirectories(logDir);
            Path file = logDir.resolve("admin-" + LocalDate.now().format(DATE_FORMAT) + ".log");
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
            plugin.getLogger().warning("Failed to write admin log: " + e.getMessage());
        }
    }

    private String sanitize(String value) {
        if (value == null) return "";
        return value.replace("|", "").trim();
    }

    private String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }
}
