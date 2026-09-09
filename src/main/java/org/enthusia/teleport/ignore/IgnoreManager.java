package org.enthusia.teleport.ignore;

import org.bukkit.configuration.file.YamlConfiguration;
import org.enthusia.teleport.EnthusiaTeleportPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Persists /tpignore state.
 *
 * Ignore changes are deliberately written synchronously and atomically because they are
 * infrequent, tiny, player-driven mutations. This avoids the stale async-write race where
 * an older snapshot could overwrite a newer shutdown/reload save and make ignores appear
 * to reset after a restart.
 */
public class IgnoreManager {

    private final EnthusiaTeleportPlugin plugin;
    private final File file;
    private final File tempFile;
    private final Object ioLock = new Object();
    private final Map<UUID, Set<UUID>> ignoring = new HashMap<>();
    private boolean dirty;

    public IgnoreManager(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "ignore.yml");
        this.tempFile = new File(plugin.getDataFolder(), "ignore.yml.tmp");
        load();
    }

    public void reload() {
        // reloadPlugin() flushes first, so loading here cannot discard an unsaved toggle.
        load();
    }

    public void saveAll() {
        dirty = true;
        plugin.getPerformanceMonitor().increment("yaml.ignore.queued");
    }

    /**
     * Kept for the existing scheduler contract. Ignore data is intentionally flushed on the
     * calling thread instead of starting an async writer; the file is small and this removes
     * shutdown/reload write races entirely.
     */
    public void flushIfDirtyAsync() {
        if (!dirty) {
            plugin.getPerformanceMonitor().increment("yaml.ignore.skipped");
            return;
        }
        flushBlocking();
    }

    public void flushBlocking() {
        if (!dirty) {
            plugin.getPerformanceMonitor().increment("yaml.ignore.skipped");
            return;
        }

        Map<UUID, Set<UUID>> snapshot = snapshot();
        if (writeSnapshot(snapshot)) {
            dirty = false;
            plugin.getPerformanceMonitor().increment("yaml.ignore.flushed");
        } else {
            // Keep dirty=true so the next scheduled flush or shutdown retries the exact state.
            plugin.getPerformanceMonitor().increment("yaml.ignore.failed");
        }
    }

    private boolean writeSnapshot(Map<UUID, Set<UUID>> snapshot) {
        synchronized (ioLock) {
            YamlConfiguration yaml = new YamlConfiguration();
            for (Map.Entry<UUID, Set<UUID>> entry : snapshot.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    continue;
                }
                List<String> ignoredSenders = new ArrayList<>();
                for (UUID sender : entry.getValue()) {
                    ignoredSenders.add(sender.toString());
                }
                yaml.set(entry.getKey().toString(), ignoredSenders);
            }

            try {
                file.getParentFile().mkdirs();
                yaml.save(tempFile);
                moveTempIntoPlace();
                return true;
            } catch (IOException exception) {
                plugin.getLogger().warning("Failed to save ignore.yml: " + exception.getMessage());
                return false;
            }
        }
    }

    private void moveTempIntoPlace() throws IOException {
        try {
            Files.move(
                    tempFile.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public boolean isIgnoring(UUID receiver, UUID sender) {
        Set<UUID> set = ignoring.get(receiver);
        return set != null && set.contains(sender);
    }

    public void setIgnoring(UUID receiver, UUID sender, boolean ignore) {
        boolean changed;
        if (ignore) {
            changed = getSet(receiver).add(sender);
        } else {
            Set<UUID> set = ignoring.get(receiver);
            changed = set != null && set.remove(sender);
            if (set != null && set.isEmpty()) {
                ignoring.remove(receiver);
            }
        }

        if (!changed) {
            return;
        }

        dirty = true;
        plugin.getPerformanceMonitor().increment("yaml.ignore.queued");

        // Persist the toggle before returning from the command. If this write fails, dirty stays
        // true and the scheduler/onDisable path retries it rather than silently losing the change.
        flushBlocking();
    }

    public Set<UUID> getIgnored(UUID receiver) {
        Set<UUID> set = ignoring.get(receiver);
        return set == null ? new HashSet<>() : new HashSet<>(set);
    }

    private void load() {
        synchronized (ioLock) {
            ignoring.clear();
            ensureFileExists();

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
                        // Ignore malformed individual UUIDs without discarding the owner's list.
                    }
                }
                if (!senders.isEmpty()) {
                    ignoring.put(receiver, senders);
                }
            }
            dirty = false;
        }
    }

    private void ensureFileExists() {
        if (file.exists()) {
            return;
        }
        try {
            file.getParentFile().mkdirs();
            file.createNewFile();
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to create ignore.yml: " + exception.getMessage());
        }
    }

    private Set<UUID> getSet(UUID receiver) {
        return ignoring.computeIfAbsent(receiver, unused -> new HashSet<>());
    }

    private Map<UUID, Set<UUID>> snapshot() {
        Map<UUID, Set<UUID>> copy = new HashMap<>();
        for (Map.Entry<UUID, Set<UUID>> entry : ignoring.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                copy.put(entry.getKey(), new HashSet<>(entry.getValue()));
            }
        }
        return copy;
    }
}
