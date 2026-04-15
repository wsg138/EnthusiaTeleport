package org.enthusia.teleport.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.enthusia.teleport.EnthusiaTeleportPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class PluginConfigManager {

    private final EnthusiaTeleportPlugin plugin;
    private volatile PluginConfig current;

    public PluginConfigManager(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        config.options().copyDefaults(true);
        plugin.saveConfig();
        current = parse(config);
    }

    public PluginConfig current() {
        return current;
    }

    private PluginConfig parse(FileConfiguration config) {
        return new PluginConfig(
                new PluginConfig.TeleportSettings(
                        config.getDouble("teleport.warmup-seconds", 5.0D),
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
                        Math.max(1, config.getInt("rtp.max-attempts", 30))
                )
        );
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
