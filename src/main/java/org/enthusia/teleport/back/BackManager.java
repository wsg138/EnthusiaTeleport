package org.enthusia.teleport.back;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BackManager {

    private final EnthusiaTeleportPlugin plugin;
    private final Map<UUID, Deque<Location>> history = new HashMap<>();
    private int maxEntries;

    public BackManager(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.maxEntries = Math.max(0, plugin.getConfig().getInt("teleport.back-max", 10));
    }

    public void record(Player player, Location from) {
        if (player == null || from == null || from.getWorld() == null) return;
        if (maxEntries <= 0) return;

        UUID id = player.getUniqueId();
        Deque<Location> stack = history.computeIfAbsent(id, k -> new ArrayDeque<>());

        Location snapshot = from.clone();
        Location currentTop = stack.peekFirst();
        if (currentTop != null && sameBlock(currentTop, snapshot)) {
            return;
        }

        stack.addFirst(snapshot);
        while (stack.size() > maxEntries) {
            stack.removeLast();
        }
    }

    public Location peek(Player player) {
        if (player == null) return null;
        Deque<Location> stack = history.get(player.getUniqueId());
        if (stack == null || stack.isEmpty()) return null;
        Location top = stack.peekFirst();
        return top != null ? top.clone() : null;
    }

    public void pop(Player player) {
        if (player == null) return;
        Deque<Location> stack = history.get(player.getUniqueId());
        if (stack == null) return;
        stack.pollFirst();
        if (stack.isEmpty()) {
            history.remove(player.getUniqueId());
        }
    }

    public void remove(UUID playerId) {
        if (playerId == null) return;
        history.remove(playerId);
    }

    private boolean sameBlock(Location a, Location b) {
        if (a == null || b == null) return false;
        if (a.getWorld() == null || b.getWorld() == null) return false;
        if (!a.getWorld().equals(b.getWorld())) return false;
        return a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }
}
