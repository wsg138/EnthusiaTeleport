package org.enthusia.teleport.task;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.config.PluginConfig;

import java.util.ArrayList;
import java.util.List;

public class TaskCoordinator {

    private final EnthusiaTeleportPlugin plugin;
    private final List<BukkitTask> tasks = new ArrayList<>();

    public TaskCoordinator(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    public void restart() {
        cancelAll();
        PluginConfig config = plugin.getPluginConfigManager().current();

        scheduleRepeating("dirty-save-flush", 20L, Math.max(1, config.persistence().flushIntervalSeconds()) * 20L, () -> {
            plugin.getHomeManager().flushIfDirtyAsync();
            plugin.getIgnoreManager().flushIfDirtyAsync();
            plugin.getRtpManager().flushIfDirtyAsync();
            plugin.getLastLocationManager().flushIfDirtyAsync();
        });

        scheduleRepeating("log-queue-flush", 20L, Math.max(1, config.logging().flushIntervalSeconds()) * 20L, () -> {
            plugin.getMessageLogManager().flushQueuedAsync();
            plugin.getAdminLogManager().flushQueuedAsync();
        });

        scheduleRepeating("rtp-queue", 1L, 1L, () -> plugin.getRtpManager().tickQueue());

        if (config.lastLocationBackstop().repairEnabled()) {
            long intervalTicks = Math.max(1, config.lastLocationBackstop().intervalMinutes()) * 60L * 20L;
            scheduleRepeating("last-location-backstop", intervalTicks, intervalTicks, () -> plugin.getLastLocationManager().startBackstopScan());
            scheduleRepeating("last-location-backstop-tick", 1L, 1L, () -> plugin.getLastLocationManager().tickBackstopScan());
        }

        long offlineNameRefreshTicks = Math.max(1, config.tabCache().offlineNameCacheRefreshMinutes()) * 60L * 20L;
        scheduleRepeating("offline-name-cache", 20L, offlineNameRefreshTicks,
                () -> plugin.getOfflineNameCache().refresh());

        if (config.debug().performanceEnabled()) {
            scheduleRepeating("performance-log", 20L, Math.max(30, config.debug().performanceLogIntervalSeconds()) * 20L,
                    () -> plugin.getPerformanceMonitor().logSummaryIfEnabled());
        }

        plugin.getPerformanceMonitor().add("reload.task_restarts", tasks.size());
        plugin.getLogger().info("Restarted " + tasks.size() + " EnthusiaTeleport scheduled tasks.");
    }

    public void cancelAll() {
        int cancelled = 0;
        for (BukkitTask task : tasks) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
                cancelled++;
            }
        }
        tasks.clear();
        if (cancelled > 0) {
            plugin.getPerformanceMonitor().add("reload.tasks_cancelled", cancelled);
        }
    }

    private void scheduleRepeating(String name, long delayTicks, long periodTicks, Runnable runnable) {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                runnable.run();
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Scheduled task '" + name + "' failed: " + exception.getMessage());
            }
        }, delayTicks, periodTicks);
        tasks.add(task);
    }
}
