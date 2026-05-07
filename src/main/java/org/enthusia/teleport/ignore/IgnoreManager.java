package org.enthusia.teleport.ignore;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.enthusia.teleport.EnthusiaTeleportPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class IgnoreManager {

    private final EnthusiaTeleportPlugin plugin;
    private final File file;
    private final Map<UUID, Set<UUID>> ignoring = new HashMap<>();
    private boolean dirty;
    private boolean saveInProgress;

    public IgnoreManager(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "ignore.yml");
        load();
    }

    public void reload() {
        load();
    }

    public void saveAll() {
        dirty = true;
        plugin.getPerformanceMonitor().increment("yaml.ignore.queued");
    }

    public void flushIfDirtyAsync() {
        if (!dirty) {
            plugin.getPerformanceMonitor().increment("yaml.ignore.skipped");
            return;
        }
        if (saveInProgress) {
            plugin.getPerformanceMonitor().increment("yaml.ignore.coalesced");
            return;
        }
        Map<UUID, Set<UUID>> snapshot = snapshot();
        dirty = false;
        saveInProgress = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean success = writeSnapshot(snapshot);
            Bukkit.getScheduler().runTask(plugin, () -> {
                saveInProgress = false;
                plugin.getPerformanceMonitor().increment(success ? "yaml.ignore.flushed" : "yaml.ignore.failed");
                if (dirty) {
                    flushIfDirtyAsync();
                }
            });
        });
    }

    public void flushBlocking() {
        if (!dirty && !saveInProgress) {
            plugin.getPerformanceMonitor().increment("yaml.ignore.skipped");
            return;
        }
        Map<UUID, Set<UUID>> snapshot = snapshot();
        dirty = false;
        writeSnapshot(snapshot);
        saveInProgress = false;
        plugin.getPerformanceMonitor().increment("yaml.ignore.flushed");
    }

    private boolean writeSnapshot(Map<UUID, Set<UUID>> snapshot) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Set<UUID>> entry : snapshot.entrySet()) {
            List<String> ignoredSenders = new ArrayList<>();
            for (UUID sender : entry.getValue()) {
                ignoredSenders.add(sender.toString());
            }
            yaml.set(entry.getKey().toString(), ignoredSenders);
        }

        try {
            file.getParentFile().mkdirs();
            yaml.save(file);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save ignore.yml: " + exception.getMessage());
            return false;
        }
    }

    public boolean isIgnoring(UUID receiver, UUID sender) {
        return getSet(receiver).contains(sender);
    }

    public void setIgnoring(UUID receiver, UUID sender, boolean ignore) {
        Set<UUID> set = getSet(receiver);
        if (ignore) {
            set.add(sender);
            dirty = true;
            return;
        }

        set.remove(sender);
        dirty = true;
        if (set.isEmpty()) {
            ignoring.remove(receiver);
        }
    }

    public Set<UUID> getIgnored(UUID receiver) {
        return new HashSet<>(getSet(receiver));
    }

    private void load() {
        ignoring.clear();
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException ignored) {
            }
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            UUID receiver;
            try {
                receiver = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            Set<UUID> senders = new HashSet<>();
            for (String rawSender : yaml.getStringList(key)) {
                try {
                    senders.add(UUID.fromString(rawSender));
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (!senders.isEmpty()) {
                ignoring.put(receiver, senders);
            }
        }
    }

    private Set<UUID> getSet(UUID receiver) {
        return ignoring.computeIfAbsent(receiver, unused -> new HashSet<>());
    }

    private Map<UUID, Set<UUID>> snapshot() {
        Map<UUID, Set<UUID>> copy = new HashMap<>();
        for (Map.Entry<UUID, Set<UUID>> entry : ignoring.entrySet()) {
            copy.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        return copy;
    }
}
