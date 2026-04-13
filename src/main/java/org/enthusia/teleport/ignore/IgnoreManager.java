package org.enthusia.teleport.ignore;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.enthusia.teleport.EnthusiaTeleportPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class IgnoreManager {

    private final EnthusiaTeleportPlugin plugin;

    private File file;
    private FileConfiguration config;

    // receiver -> set of senders they are ignoring
    private final Map<UUID, Set<UUID>> ignoring = new HashMap<>();

    public IgnoreManager(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        file = new File(plugin.getDataFolder(), "ignore.yml");
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException ignored) {}
        }

        config = YamlConfiguration.loadConfiguration(file);
        ignoring.clear();

        for (String key : config.getKeys(false)) {
            UUID receiver;
            try {
                receiver = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                continue;
            }

            List<String> list = config.getStringList(key);
            Set<UUID> senders = new HashSet<>();
            for (String s : list) {
                try {
                    senders.add(UUID.fromString(s));
                } catch (IllegalArgumentException ignored) {}
            }
            ignoring.put(receiver, senders);
        }
    }

    public void reload() {
        load();
    }

    public void saveAll() {
        for (Map.Entry<UUID, Set<UUID>> entry : ignoring.entrySet()) {
            UUID receiver = entry.getKey();
            Set<UUID> senders = entry.getValue();
            List<String> list = new ArrayList<>();
            for (UUID s : senders) {
                list.add(s.toString());
            }
            config.set(receiver.toString(), list);
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save ignore.yml: " + e.getMessage());
        }
    }

    private Set<UUID> getSet(UUID receiver) {
        return ignoring.computeIfAbsent(receiver, k -> new HashSet<>());
    }

    /**
     * Returns true if receiver is ignoring sender.
     */
    public boolean isIgnoring(UUID receiver, UUID sender) {
        return getSet(receiver).contains(sender);
    }

    public void setIgnoring(UUID receiver, UUID sender, boolean ignore) {
        Set<UUID> set = getSet(receiver);
        if (ignore) {
            set.add(sender);
        } else {
            set.remove(sender);
        }
    }

    /**
     * All players this receiver is currently ignoring.
     */
    public Set<UUID> getIgnored(UUID receiver) {
        return new HashSet<>(getSet(receiver));
    }
}
