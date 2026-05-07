package org.enthusia.teleport.player;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.enthusia.teleport.EnthusiaTeleportPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class OfflineNameCache implements Listener {

    private final EnthusiaTeleportPlugin plugin;
    private volatile List<String> knownNames = Collections.emptyList();

    public OfflineNameCache(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    public void refresh() {
        List<String> names = new ArrayList<>();
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            String name = player.getName();
            if (name != null && (player.isOnline() || player.hasPlayedBefore())) {
                names.add(name);
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        knownNames = List.copyOf(names);
        plugin.getPerformanceMonitor().increment("tab_cache.refreshes");
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
