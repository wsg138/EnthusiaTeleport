package org.enthusia.teleport.debug;

import org.enthusia.teleport.EnthusiaTeleportPlugin;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class PerformanceMonitor {

    private final EnthusiaTeleportPlugin plugin;
    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

    public PerformanceMonitor(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    public void increment(String key) {
        add(key, 1L);
    }

    public void add(String key, long amount) {
        if (key == null || key.isBlank() || amount == 0L) {
            return;
        }
        counters.computeIfAbsent(key, unused -> new AtomicLong()).addAndGet(amount);
    }

    public long get(String key) {
        AtomicLong value = counters.get(key);
        return value == null ? 0L : value.get();
    }

    public Map<String, Long> snapshot() {
        return counters.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().get(),
                        (left, right) -> left,
                        java.util.LinkedHashMap::new
                ));
    }

    public String summary() {
        return snapshot().entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    public void logSummaryIfEnabled() {
        if (!plugin.getPluginConfigManager().current().debug().performanceEnabled()) {
            return;
        }
        String summary = summary();
        if (summary.isEmpty()) {
            plugin.getLogger().info("[Performance] No counters recorded yet.");
            return;
        }
        plugin.getLogger().info("[Performance] " + summary);
    }
}
