package org.enthusia.teleport.player;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.enthusia.teleport.EnthusiaTeleportPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OfflineNameCache implements Listener {

    private static final Pattern USERCACHE_NAME_PATTERN = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");

    private final EnthusiaTeleportPlugin plugin;
    private final AtomicBoolean refreshRunning = new AtomicBoolean(false);
    private volatile List<String> knownNames = Collections.emptyList();

    public OfflineNameCache(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    public void refresh() {
        if (!refreshRunning.compareAndSet(false, true)) {
            plugin.getPerformanceMonitor().increment("tab_cache.refresh_coalesced");
            return;
        }

        long startedNanos = System.nanoTime();
        List<String> onlineNames = Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(Objects::nonNull)
                .toList();
        List<String> previousNames = knownNames;
        File userCache = new File(Bukkit.getWorldContainer(), "usercache.json");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Set<String> names = new LinkedHashSet<>(previousNames);
                names.addAll(onlineNames);
                if (userCache.isFile()) {
                    readUserCacheNames(userCache, names);
                } else {
                    plugin.getPerformanceMonitor().increment("tab_cache.usercache_missing");
                }

                List<String> sorted = new ArrayList<>(names);
                sorted.sort(String.CASE_INSENSITIVE_ORDER);
                knownNames = List.copyOf(sorted);
                plugin.getPerformanceMonitor().increment("tab_cache.refreshes");
                plugin.getPerformanceMonitor().add("tab_cache.names_loaded", sorted.size());
                long elapsedMillis = (System.nanoTime() - startedNanos) / 1_000_000L;
                plugin.getPerformanceMonitor().add("tab_cache.refresh_duration_ms", elapsedMillis);
                if (plugin.getPluginConfigManager().current().debug().performanceEnabled()) {
                    plugin.getLogger().info("Offline name cache refreshed " + sorted.size() + " name(s) in " + elapsedMillis + "ms.");
                }
            } finally {
                refreshRunning.set(false);
            }
        });
    }

    private void readUserCacheNames(File userCache, Set<String> names) {
        try {
            String json = Files.readString(userCache.toPath(), StandardCharsets.UTF_8);
            Matcher matcher = USERCACHE_NAME_PATTERN.matcher(json);
            while (matcher.find()) {
                String name = matcher.group(1);
                if (name != null && !name.isBlank()) {
                    names.add(name);
                }
            }
        } catch (IOException exception) {
            plugin.getPerformanceMonitor().increment("tab_cache.refresh_failed");
            plugin.getLogger().warning("Failed to read usercache.json for tab cache: " + exception.getMessage());
        }
    }

    public List<String> suggest(String prefix, boolean offlineOnly) {
        String lower = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return knownNames.stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lower))
                .filter(name -> !offlineOnly || Bukkit.getPlayerExact(name) == null)
                .filter(Objects::nonNull)
                .toList();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        String name = event.getPlayer().getName();
        List<String> current = knownNames;
        if (current.stream().anyMatch(existing -> existing.equalsIgnoreCase(name))) {
            return;
        }
        List<String> updated = new ArrayList<>(current);
        updated.add(name);
        updated.sort(String.CASE_INSENSITIVE_ORDER);
        knownNames = List.copyOf(updated);
    }
}
