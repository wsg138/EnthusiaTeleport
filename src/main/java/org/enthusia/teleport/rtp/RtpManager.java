package org.enthusia.teleport.rtp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.teleport.SafeLocationFinder;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class RtpManager {

    private final EnthusiaTeleportPlugin plugin;

    private File file;
    private FileConfiguration config;

    // uuid -> used count
    private final Map<UUID, Integer> uses = new HashMap<>();

    public RtpManager(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        file = new File(plugin.getDataFolder(), "rtp_uses.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException ignored) {}
        }
        config = YamlConfiguration.loadConfiguration(file);
        uses.clear();

        for (String key : config.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                int count = config.getInt(key, 0);
                uses.put(uuid, count);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void reload() {
        load();
    }

    public void saveAll() {
        for (Map.Entry<UUID, Integer> entry : uses.entrySet()) {
            config.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save rtp_uses.yml: " + e.getMessage());
        }
    }

    public int getUses(UUID uuid) {
        return uses.getOrDefault(uuid, 0);
    }

    public void incrementUse(UUID uuid) {
        uses.put(uuid, getUses(uuid) + 1);
    }

    public int getLimit(Player player) {
        int base = plugin.getConfig().getInt("rtp.max-uses-default", 0);
        int max = base;

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("rtp.rank-limits");
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

    /**
     * Returns true if the player can still /rtp.
     * Limit < 0 means unlimited.
     */
    public boolean canUse(Player player) {
        int limit = getLimit(player);
        if (limit < 0) return true; // unlimited
        return getUses(player.getUniqueId()) < limit;
    }

    /**
     * Finds a random safe location within configured bounds,
     * or null if no safe spot found within max-attempts.
     */
    public Location findRandomLocation(Player player) {
        if (!plugin.getConfig().getBoolean("rtp.enabled", true)) {
            return null;
        }

        String worldName = plugin.getConfig().getString("rtp.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("RTP world not found: " + worldName);
            return null;
        }

        int minX = plugin.getConfig().getInt("rtp.min-x", -7500);
        int maxX = plugin.getConfig().getInt("rtp.max-x", 7500);
        int minZ = plugin.getConfig().getInt("rtp.min-z", -7500);
        int maxZ = plugin.getConfig().getInt("rtp.max-z", 7500);
        int maxAttempts = plugin.getConfig().getInt("rtp.max-attempts", 30);

        SafeLocationFinder safeFinder = plugin.getTeleportManager().getSafeFinder();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (int i = 0; i < maxAttempts; i++) {
            int x = rng.nextInt(minX, maxX + 1);
            int z = rng.nextInt(minZ, maxZ + 1);

            int y = world.getHighestBlockYAt(x, z) + 1;
            Location base = new Location(
                    world,
                    x + 0.5,
                    y,
                    z + 0.5,
                    player.getLocation().getYaw(),
                    player.getLocation().getPitch()
            );

            Location safe = safeFinder.findSafeTeleportLocation(base);
            if (safe != null) {
                return safe;
            }
        }

        return null;
    }
}
