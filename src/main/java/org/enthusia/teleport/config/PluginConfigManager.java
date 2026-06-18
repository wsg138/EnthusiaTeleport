package org.enthusia.teleport.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.enthusia.teleport.EnthusiaTeleportPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class PluginConfigManager {

    public static final int CURRENT_CONFIG_VERSION = 3;
    public static final int CURRENT_MESSAGES_VERSION = 3;

    private final EnthusiaTeleportPlugin plugin;
    private volatile PluginConfig current;

    public PluginConfigManager(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        migrateYaml("config.yml", CURRENT_CONFIG_VERSION);
        migrateYaml("messages.yml", CURRENT_MESSAGES_VERSION);
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        current = parse(config);
    }

    public PluginConfig current() {
        return current;
    }

    private PluginConfig parse(FileConfiguration config) {
        return new PluginConfig(
                config.getInt("config-version", CURRENT_CONFIG_VERSION),
                new PluginConfig.TeleportSettings(
                        config.getDouble("teleport.warmup-seconds", 5.0D),
                        Math.max(0.0D, config.getDouble("teleport.movement-cancel-distance", 0.35D)),
                        Math.max(0, config.getInt("teleport.cooldown-seconds", 60)),
                        Math.max(1, config.getInt("teleport.request-expiry-seconds", 60)),
                        Math.max(1, config.getInt("teleport.safe-search-radius", 4)),
                        Math.max(0, config.getInt("teleport.back-max", 10)),
                        parseBlockedWorlds(config)
                ),
                new PluginConfig.CombatSettings(
                        config.getBoolean("combat.enabled", true),
                        Math.max(0, config.getInt("combat.tag-seconds", 30))
                ),
                new PluginConfig.HomeSettings(
                        Math.max(0, config.getInt("homes.default-max", 2)),
                        parseIntMap(config.getConfigurationSection("homes.rank-limits"))
                ),
                new PluginConfig.SpawnSettings(
                        config.getBoolean("spawn.use-configured-spawn", true),
                        config.getString("spawn.world", "world"),
                        config.getDouble("spawn.x", 0.5D),
                        config.getDouble("spawn.y", 64.0D),
                        config.getDouble("spawn.z", 0.5D),
                        config.getBoolean("spawn.center-on-block", true),
                        (float) config.getDouble("spawn.yaw", 0.0D),
                        (float) config.getDouble("spawn.pitch", 0.0D),
                        config.getBoolean("spawn.first-join.teleport-to-spawn", true),
                        config.getBoolean("spawn.first-join.set-bed-spawn", true),
                        config.getBoolean("spawn.first-join.starter-kit.enabled", true),
                        config.getBoolean("spawn.first-join.starter-kit.clear-inventory", false),
                        parseKitItems(config.getConfigurationSection("spawn.first-join.starter-kit")),
                        config.getBoolean("spawn.respawn.override-to-configured-spawn", true)
                ),
                new PluginConfig.RtpSettings(
                        config.getBoolean("rtp.enabled", true),
                        config.getString("rtp.world", "world"),
                        config.getInt("rtp.min-x", -7500),
                        config.getInt("rtp.max-x", 7500),
                        config.getInt("rtp.min-z", -7500),
                        config.getInt("rtp.max-z", 7500),
                        config.getInt("rtp.max-uses-default", 0),
                        parseIntMap(config.getConfigurationSection("rtp.rank-limits")),
                        Math.max(1, config.getInt("rtp.max-attempts", 30)),
                        new PluginConfig.QueueSettings(
                                config.getBoolean("rtp.queue.enabled", true),
                                Math.max(1, config.getInt("rtp.queue.max-active-searches", 2)),
                                Math.max(1, config.getInt("rtp.queue.max-location-checks-per-tick", 3)),
                                Math.max(1, config.getInt("rtp.queue.max-chunk-load-requests-per-second", 5)),
                                Math.max(1, config.getInt("rtp.queue.timeout-seconds", 30))
                        ),
                        new PluginConfig.SpacingSettings(
                                Math.max(0.0D, config.getDouble("rtp.spacing.min-distance-from-spawn", 0.0D)),
                                Math.max(0.0D, config.getDouble("rtp.spacing.min-distance-from-players", 0.0D)),
                                Math.max(0.0D, config.getDouble("rtp.spacing.min-distance-from-recent-rtp", 0.0D)),
                                Math.max(1, config.getInt("rtp.spacing.recent-rtp-memory-minutes", 120))
                        ),
                        new PluginConfig.SafetySettings(
                                Math.max(1, config.getInt("rtp.safety.max-attempts-per-player", config.getInt("rtp.max-attempts", 30))),
                                Math.max(1, config.getInt("rtp.safety.safe-search-radius", config.getInt("teleport.safe-search-radius", 4)))
                        )
                ),
                new PluginConfig.PersistenceSettings(
                        Math.max(1, config.getInt("persistence.flush-interval-seconds", 30))
                ),
                new PluginConfig.LoggingSettings(
                        Math.max(1, config.getInt("logging.flush-interval-seconds", 5)),
                        Math.max(100, config.getInt("logging.max-queue-size", 5000))
                ),
                new PluginConfig.MsgLogSettings(
                        Math.max(1, config.getInt("msglog.max-days-scanned", 7)),
                        Math.max(1, config.getInt("msglog.max-results", 500)),
                        Math.max(1, config.getInt("msglog.timeout-seconds", 10))
                ),
                new PluginConfig.LastLocationBackstopSettings(
                        Math.max(1, config.getInt("last-location-backstop.interval-minutes", 5)),
                        Math.max(1, config.getInt("last-location-backstop.max-players-per-tick", 10)),
                        config.getBoolean("last-location-backstop.repair-enabled", true),
                        config.getBoolean("last-location-backstop.debug-logging", false)
                ),
                new PluginConfig.TabCacheSettings(
                        parseOfflineNameCacheRefreshMinutes(config)
                ),
                new PluginConfig.DebugSettings(
                        config.getBoolean("debug.performance.enabled", false),
                        Math.max(30, config.getInt("debug.performance.log-interval-seconds", 300))
                )
        );
    }

    private void migrateYaml(String resourceName, int currentVersion) {
        File file = new File(plugin.getDataFolder(), resourceName);
        if (!file.exists()) {
            plugin.saveResource(resourceName, false);
            plugin.getLogger().info("Created default " + resourceName + ".");
            return;
        }

        YamlConfiguration user = YamlConfiguration.loadConfiguration(file);
        YamlConfiguration defaults = loadDefaults(resourceName);
        if (defaults == null) {
            plugin.getLogger().warning("Could not load default " + resourceName + " for migration.");
            return;
        }

        int oldVersion = user.getInt("config-version", 1);
        boolean changed = false;
        List<String> addedKeys = new ArrayList<>();

        if (oldVersion < currentVersion) {
            if (!backup(file, resourceName, oldVersion, currentVersion)) {
                plugin.getLogger().warning("Could not safely back up " + resourceName + "; migration skipped.");
                return;
            }
            user.set("config-version", currentVersion);
            changed = true;
        }

        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) {
                continue;
            }
            if (!user.contains(key)) {
                user.set(key, defaults.get(key));
                addedKeys.add(key);
                changed = true;
            }
        }

        if (!changed) {
            return;
        }

        try {
            user.save(file);
            if (oldVersion < currentVersion) {
                plugin.getLogger().info("Migrated " + resourceName + " from version " + oldVersion + " to " + currentVersion + ".");
            }
            if (!addedKeys.isEmpty()) {
                plugin.getLogger().info("Added " + addedKeys.size() + " missing key(s) to " + resourceName + ": " + String.join(", ", addedKeys));
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save migrated " + resourceName + ": " + exception.getMessage());
        }
    }

    private YamlConfiguration loadDefaults(String resourceName) {
        try (InputStream stream = plugin.getResource(resourceName)) {
            if (stream == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to read default " + resourceName + ": " + exception.getMessage());
            return null;
        }
    }

    private boolean backup(File file, String resourceName, int oldVersion, int newVersion) {
        File backup = new File(plugin.getDataFolder(), resourceName + ".v" + oldVersion + "-to-v" + newVersion + ".bak");
        int suffix = 1;
        while (backup.exists()) {
            backup = new File(plugin.getDataFolder(), resourceName + ".v" + oldVersion + "-to-v" + newVersion + "." + suffix + ".bak");
            suffix++;
        }
        try {
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            plugin.getLogger().info("Backed up " + resourceName + " to " + backup.getName() + ".");
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to back up " + resourceName + ": " + exception.getMessage());
            return false;
        }
    }

    private Set<String> parseBlockedWorlds(FileConfiguration config) {
        Set<String> blocked = new LinkedHashSet<>();
        for (String world : config.getStringList("teleport.blocked-target-worlds")) {
            if (world == null) {
                continue;
            }
            String normalized = world.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty()) {
                blocked.add(normalized);
            }
        }
        return blocked;
    }

    private Map<String, Integer> parseIntMap(ConfigurationSection section) {
        Map<String, Integer> values = new LinkedHashMap<>();
        if (section == null) {
            return values;
        }
        for (String key : section.getKeys(false)) {
            values.put(key, section.getInt(key));
        }
        return values;
    }

    private int parseOfflineNameCacheRefreshMinutes(FileConfiguration config) {
        if (config.contains("tab-cache.offline-name-cache-refresh-minutes")) {
            return Math.max(1, config.getInt("tab-cache.offline-name-cache-refresh-minutes", 15));
        }
        if (config.contains("tab-cache.refresh-interval-seconds")) {
            int seconds = Math.max(60, config.getInt("tab-cache.refresh-interval-seconds", 900));
            return Math.max(1, (int) Math.ceil(seconds / 60.0D));
        }
        return 15;
    }

    private List<PluginConfig.KitItem> parseKitItems(ConfigurationSection section) {
        List<PluginConfig.KitItem> items = new ArrayList<>();
        if (section == null) {
            return items;
        }
        for (Map<?, ?> rawEntry : section.getMapList("items")) {
            Object material = rawEntry.get("material");
            if (!(material instanceof String materialName) || materialName.isBlank()) {
                continue;
            }
            int amount = 1;
            Object rawAmount = rawEntry.get("amount");
            if (rawAmount instanceof Number number) {
                amount = number.intValue();
            } else if (rawAmount instanceof String rawString) {
                try {
                    amount = Integer.parseInt(rawString);
                } catch (NumberFormatException ignored) {
                    amount = 1;
                }
            }
            items.add(new PluginConfig.KitItem(materialName.trim(), Math.max(1, amount)));
        }
        return items;
    }
}
