package org.enthusia.teleport.rtp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.config.PluginConfig;
import org.enthusia.teleport.teleport.SafeLocationFinder;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.configuration.file.YamlConfiguration;

public class RtpManager {

    private final EnthusiaTeleportPlugin plugin;
    private final File file;
    private final Map<UUID, Integer> uses = new HashMap<>();

    public RtpManager(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "rtp_uses.yml");
        load();
    }

    public void reload() {
        load();
    }

    public void saveAll() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> entry : uses.entrySet()) {
            yaml.set(entry.getKey().toString(), entry.getValue());
        }

        try {
            file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save rtp_uses.yml: " + exception.getMessage());
        }
    }

    public int getUses(UUID uuid) {
        return uses.getOrDefault(uuid, 0);
    }

    public void incrementUse(UUID uuid) {
        uses.put(uuid, getUses(uuid) + 1);
    }

    public int getLimit(Player player) {
        PluginConfig.RtpSettings settings = plugin.getPluginConfigManager().current().rtp();
        int max = settings.maxUsesDefault();
        for (Map.Entry<String, Integer> entry : settings.rankLimits().entrySet()) {
            if (player.hasPermission(entry.getKey()) && entry.getValue() > max) {
                max = entry.getValue();
            }
        }
        return max;
    }

    public boolean canUse(Player player) {
        int limit = getLimit(player);
        return limit < 0 || getUses(player.getUniqueId()) < limit;
    }

    public Location findRandomLocation(Player player) {
        PluginConfig.RtpSettings settings = plugin.getPluginConfigManager().current().rtp();
        if (!settings.enabled()) {
            return null;
        }

        World world = Bukkit.getWorld(settings.world());
        if (world == null) {
            plugin.getLogger().warning("RTP world not found: " + settings.world());
            return null;
        }

        int minX = Math.min(settings.minX(), settings.maxX());
        int maxX = Math.max(settings.minX(), settings.maxX());
        int minZ = Math.min(settings.minZ(), settings.maxZ());
        int maxZ = Math.max(settings.minZ(), settings.maxZ());
        SafeLocationFinder safeFinder = plugin.getTeleportManager().getSafeFinder();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int attempt = 0; attempt < settings.maxAttempts(); attempt++) {
            int x = random.nextInt(minX, maxX + 1);
            int z = random.nextInt(minZ, maxZ + 1);
            int y = world.getHighestBlockYAt(x, z) + 1;

            Location candidate = new Location(
                    world,
                    x + 0.5D,
                    y,
                    z + 0.5D,
                    player.getLocation().getYaw(),
                    player.getLocation().getPitch()
            );

            if (safeFinder.isSafeTeleportLocation(candidate)) {
                return candidate;
            }

            Location nearbySafe = safeFinder.findSafeTeleportLocation(candidate);
            if (nearbySafe != null) {
                return nearbySafe;
            }
        }

        return null;
    }

    private void load() {
        uses.clear();
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException ignored) {
            }
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                uses.put(UUID.fromString(key), Math.max(0, yaml.getInt(key, 0)));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
