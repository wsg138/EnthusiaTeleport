package org.enthusia.teleport.home;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class HomeManager {

    private final EnthusiaTeleportPlugin plugin;

    private File file;
    private FileConfiguration config;

    // owner -> (name -> home)
    private final Map<UUID, Map<String, Home>> homes = new HashMap<>();

    public HomeManager(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        file = new File(plugin.getDataFolder(), "homes.yml");
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException ignored) {}
        }

        config = YamlConfiguration.loadConfiguration(file);
        homes.clear();
        boolean dirty = false;

        for (String key : config.getKeys(false)) {
            UUID owner;
            try {
                owner = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                continue;
            }

            ConfigurationSection sec = config.getConfigurationSection(key);
            if (sec == null) continue;

            Map<String, Home> map = homes.computeIfAbsent(owner, k -> new LinkedHashMap<>());
            List<Home> loadedHomes = new ArrayList<>();

            for (String homeName : sec.getKeys(false)) {
                ConfigurationSection hSec = sec.getConfigurationSection(homeName);
                if (hSec == null) continue;

                String worldName = hSec.getString("world");
                double x = hSec.getDouble("x");
                double y = hSec.getDouble("y");
                double z = hSec.getDouble("z");
                float yaw = (float) hSec.getDouble("yaw");
                float pitch = (float) hSec.getDouble("pitch");
                long createdAt = hSec.getLong("created", 0L);
                if (createdAt <= 0L) {
                    createdAt = System.currentTimeMillis();
                    dirty = true;
                }

                String lower = homeName.toLowerCase(Locale.ROOT);
                loadedHomes.add(new Home(owner, lower, worldName, x, y, z, yaw, pitch, createdAt));
            }

            if (!loadedHomes.isEmpty()) {
                loadedHomes.sort(Comparator.comparingLong(Home::getCreatedAt)
                        .thenComparing(Home::getName));
                for (Home home : loadedHomes) {
                    map.put(home.getName(), home);
                }
            }
        }

        if (dirty) {
            saveAll();
        }
    }

    public void reload() {
        load();
    }

    public void saveAll() {
        config = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, Home>> entry : homes.entrySet()) {
            UUID owner = entry.getKey();
            String ownerKey = owner.toString();

            Map<String, Home> map = entry.getValue();
            if (map.isEmpty()) {
                continue;
            }

            ConfigurationSection sec = config.createSection(ownerKey);
            for (Home home : map.values()) {
                String name = home.getName();
                ConfigurationSection hSec = sec.createSection(name);

                hSec.set("world", home.getWorldName());
                hSec.set("x", home.getX());
                hSec.set("y", home.getY());
                hSec.set("z", home.getZ());
                hSec.set("yaw", (double) home.getYaw());
                hSec.set("pitch", (double) home.getPitch());
                hSec.set("created", home.getCreatedAt());
            }
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save homes.yml: " + e.getMessage());
        }
    }

    private Map<String, Home> getMap(UUID owner) {
        return homes.computeIfAbsent(owner, k -> new LinkedHashMap<>());
    }

    public Collection<Home> getHomes(UUID owner) {
        return new ArrayList<>(getMap(owner).values());
    }

    public int getHomeCount(UUID owner) {
        return getMap(owner).size();
    }

    public Home getHome(UUID owner, String name) {
        if (name == null) return null;
        return getMap(owner).get(name.toLowerCase(Locale.ROOT));
    }

    public void setHome(Player player, String name) {
        UUID owner = player.getUniqueId();
        String lower = name.toLowerCase(Locale.ROOT);
        String worldName = player.getWorld().getName();

        double x = player.getLocation().getX();
        double y = player.getLocation().getY();
        double z = player.getLocation().getZ();
        float yaw = player.getLocation().getYaw();
        float pitch = player.getLocation().getPitch();
        long createdAt = System.currentTimeMillis();

        Home home = new Home(owner, lower, worldName, x, y, z, yaw, pitch, createdAt);
        getMap(owner).put(lower, home);
    }

    public void deleteHome(UUID owner, String name) {
        if (name == null) return;
        Map<String, Home> map = getMap(owner);
        map.remove(name.toLowerCase(Locale.ROOT));
    }

    public void clearHomes(UUID owner) {
        Map<String, Home> map = getMap(owner);
        map.clear();
    }

    /**
     * Default + rank-based limits from config.
     */
    public int getHomeLimit(Player player) {
        FileConfiguration cfg = plugin.getConfig();
        int max = cfg.getInt("homes.default-max", 2);

        ConfigurationSection section = cfg.getConfigurationSection("homes.rank-limits");
        if (section != null) {
            for (String perm : section.getKeys(false)) {
                int value = section.getInt(perm);
                if (player.hasPermission(perm) && value > max) {
                    max = value;
                }
            }
        }

        return max;
    }

    public boolean isOverLimit(Player player) {
        return getHomeCount(player.getUniqueId()) > getHomeLimit(player);
    }
}
