package org.enthusia.teleport.home;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.config.PluginConfig;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class HomeManager {

    private final EnthusiaTeleportPlugin plugin;
    private final File file;
    private final Map<UUID, Map<String, Home>> homes = new HashMap<>();

    public HomeManager(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "homes.yml");
        load();
    }

    public void reload() {
        load();
    }

    public void saveAll() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, Home>> entry : homes.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }

            ConfigurationSection ownerSection = config.createSection(entry.getKey().toString());
            for (Home home : entry.getValue().values()) {
                ConfigurationSection homeSection = ownerSection.createSection(home.getKey());
                homeSection.set("name", home.getName());
                homeSection.set("world", home.getWorldName());
                homeSection.set("x", home.getX());
                homeSection.set("y", home.getY());
                homeSection.set("z", home.getZ());
                homeSection.set("yaw", (double) home.getYaw());
                homeSection.set("pitch", (double) home.getPitch());
                homeSection.set("created", home.getCreatedAt());
            }
        }

        try {
            file.getParentFile().mkdirs();
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save homes.yml: " + exception.getMessage());
        }
    }

    public Collection<Home> getHomes(UUID owner) {
        return new ArrayList<>(getMap(owner).values());
    }

    public int getHomeCount(UUID owner) {
        return getMap(owner).size();
    }

    public Home getHome(UUID owner, String name) {
        if (name == null) {
            return null;
        }
        return getMap(owner).get(normalizeName(name));
    }

    public void setHome(Player player, String name) {
        String key = normalizeName(name);
        long createdAt = System.currentTimeMillis();
        Home existing = getMap(player.getUniqueId()).get(key);
        if (existing != null) {
            createdAt = existing.getCreatedAt();
        }

        Home home = new Home(
                player.getUniqueId(),
                key,
                existing != null ? existing.getName() : name,
                player.getWorld().getName(),
                player.getLocation().getX(),
                player.getLocation().getY(),
                player.getLocation().getZ(),
                player.getLocation().getYaw(),
                player.getLocation().getPitch(),
                createdAt
        );
        getMap(player.getUniqueId()).put(key, home);
    }

    public void deleteHome(UUID owner, String name) {
        if (name == null) {
            return;
        }
        getMap(owner).remove(normalizeName(name));
    }

    public void clearHomes(UUID owner) {
        getMap(owner).clear();
    }

    public int getHomeLimit(Player player) {
        PluginConfig.HomeSettings settings = plugin.getPluginConfigManager().current().homes();
        int max = settings.defaultMax();
        for (Map.Entry<String, Integer> entry : settings.rankLimits().entrySet()) {
            if (player.hasPermission(entry.getKey()) && entry.getValue() > max) {
                max = entry.getValue();
            }
        }
        return max;
    }

    public boolean isOverLimit(Player player) {
        return getHomeCount(player.getUniqueId()) > getHomeLimit(player);
    }

    private void load() {
        homes.clear();
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException ignored) {
            }
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        boolean dirty = false;

        for (String key : config.getKeys(false)) {
            UUID owner;
            try {
                owner = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            ConfigurationSection ownerSection = config.getConfigurationSection(key);
            if (ownerSection == null) {
                continue;
            }

            List<Home> loadedHomes = new ArrayList<>();
            for (String homeKey : ownerSection.getKeys(false)) {
                ConfigurationSection homeSection = ownerSection.getConfigurationSection(homeKey);
                if (homeSection == null) {
                    continue;
                }

                String displayName = homeSection.getString("name", homeKey);
                String normalized = normalizeName(displayName);
                String worldName = homeSection.getString("world");
                if (worldName == null || worldName.isBlank()) {
                    continue;
                }

                long createdAt = homeSection.getLong("created", 0L);
                if (createdAt <= 0L) {
                    createdAt = System.currentTimeMillis();
                    dirty = true;
                }

                loadedHomes.add(new Home(
                        owner,
                        normalized,
                        displayName,
                        worldName,
                        homeSection.getDouble("x"),
                        homeSection.getDouble("y"),
                        homeSection.getDouble("z"),
                        (float) homeSection.getDouble("yaw"),
                        (float) homeSection.getDouble("pitch"),
                        createdAt
                ));
            }

            if (loadedHomes.isEmpty()) {
                continue;
            }

            loadedHomes.sort(Comparator.comparingLong(Home::getCreatedAt).thenComparing(Home::getName, String.CASE_INSENSITIVE_ORDER));
            Map<String, Home> ownerHomes = homes.computeIfAbsent(owner, unused -> new LinkedHashMap<>());
            for (Home home : loadedHomes) {
                ownerHomes.put(home.getKey(), home);
            }
        }

        if (dirty) {
            saveAll();
        }
    }

    private Map<String, Home> getMap(UUID owner) {
        return homes.computeIfAbsent(owner, unused -> new LinkedHashMap<>());
    }

    private String normalizeName(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
