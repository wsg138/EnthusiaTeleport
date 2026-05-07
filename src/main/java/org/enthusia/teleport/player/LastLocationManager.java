package org.enthusia.teleport.player;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.enthusia.teleport.EnthusiaTeleportPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LastLocationManager implements Listener {

    private final EnthusiaTeleportPlugin plugin;
    private final File file;
    private final Map<UUID, SavedLocation> lastLocations = new HashMap<>();
    private final java.util.Queue<UUID> backstopQueue = new java.util.ArrayDeque<>();
    private boolean dirty;
    private boolean saveInProgress;

    public LastLocationManager(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "last-locations.yml");
        load();
    }

    public void reload() {
        load();
    }

    public void saveOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            record(player);
        }
        saveAll();
    }

    public void saveOnlinePlayersBlocking() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            record(player);
        }
        flushBlocking();
    }

    public void saveAll() {
        dirty = true;
        plugin.getPerformanceMonitor().increment("yaml.last_locations.queued");
    }

    public void flushIfDirtyAsync() {
        if (!dirty) {
            plugin.getPerformanceMonitor().increment("yaml.last_locations.skipped");
            return;
        }
        if (saveInProgress) {
            plugin.getPerformanceMonitor().increment("yaml.last_locations.coalesced");
            return;
        }
        Map<UUID, SavedLocation> snapshot = new HashMap<>(lastLocations);
        dirty = false;
        saveInProgress = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean success = writeSnapshot(snapshot);
            Bukkit.getScheduler().runTask(plugin, () -> {
                saveInProgress = false;
                plugin.getPerformanceMonitor().increment(success ? "yaml.last_locations.flushed" : "yaml.last_locations.failed");
                if (dirty) {
                    flushIfDirtyAsync();
                }
            });
        });
    }

    public void flushBlocking() {
        if (!dirty && !saveInProgress) {
            plugin.getPerformanceMonitor().increment("yaml.last_locations.skipped");
            return;
        }
        dirty = false;
        writeSnapshot(new HashMap<>(lastLocations));
        saveInProgress = false;
        plugin.getPerformanceMonitor().increment("yaml.last_locations.flushed");
    }

    private boolean writeSnapshot(Map<UUID, SavedLocation> snapshot) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, SavedLocation> entry : snapshot.entrySet()) {
            SavedLocation saved = entry.getValue();
            ConfigurationSection section = yaml.createSection(entry.getKey().toString());
            section.set("world", saved.worldName());
            section.set("x", saved.x());
            section.set("y", saved.y());
            section.set("z", saved.z());
            section.set("yaw", saved.yaw());
            section.set("pitch", saved.pitch());
            section.set("updated-at", saved.updatedAt());
        }

        try {
            file.getParentFile().mkdirs();
            yaml.save(file);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save last-locations.yml: " + exception.getMessage());
            return false;
        }
    }

    public Location getLastLocation(OfflinePlayer player) {
        if (player == null) {
            return null;
        }
        SavedLocation saved = lastLocations.get(player.getUniqueId());
        if (saved == null) {
            return null;
        }
        World world = Bukkit.getWorld(saved.worldName());
        if (world == null) {
            return null;
        }
        return new Location(world, saved.x(), saved.y(), saved.z(), saved.yaw(), saved.pitch());
    }

    public void record(Player player) {
        if (player == null) {
            return;
        }
        Location location = player.getLocation();
        if (location.getWorld() == null) {
            return;
        }
        lastLocations.put(player.getUniqueId(), new SavedLocation(
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch(),
                System.currentTimeMillis()
        ));
        dirty = true;
    }

    public void startBackstopScan() {
        backstopQueue.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            backstopQueue.add(player.getUniqueId());
        }
        plugin.getPerformanceMonitor().add("last_location.backstop_queued", backstopQueue.size());
    }

    public void tickBackstopScan() {
        int limit = plugin.getPluginConfigManager().current().lastLocationBackstop().maxPlayersPerTick();
        int scanned = 0;
        while (scanned < limit && !backstopQueue.isEmpty()) {
            UUID playerId = backstopQueue.poll();
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                record(player);
                scanned++;
            }
        }
        if (scanned > 0) {
            plugin.getPerformanceMonitor().add("last_location.players_scanned", scanned);
            if (plugin.getPluginConfigManager().current().lastLocationBackstop().debugLogging()) {
                plugin.getLogger().info("Last-location backstop recorded " + scanned + " player(s).");
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        record(event.getPlayer());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        record(event.getPlayer());
    }

    private void load() {
        lastLocations.clear();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            UUID playerId;
            try {
                playerId = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            ConfigurationSection section = yaml.getConfigurationSection(key);
            if (section == null) {
                continue;
            }

            String world = section.getString("world");
            if (world == null || world.isBlank()) {
                continue;
            }

            lastLocations.put(playerId, new SavedLocation(
                    world,
                    section.getDouble("x"),
                    section.getDouble("y"),
                    section.getDouble("z"),
                    (float) section.getDouble("yaw"),
                    (float) section.getDouble("pitch"),
                    section.getLong("updated-at", 0L)
            ));
        }
        dirty = false;
    }

    private record SavedLocation(
            String worldName,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            long updatedAt
    ) {
    }
}
