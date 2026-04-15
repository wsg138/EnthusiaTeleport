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

    public void saveAll() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, SavedLocation> entry : lastLocations.entrySet()) {
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
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save last-locations.yml: " + exception.getMessage());
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
