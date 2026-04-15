package org.enthusia.teleport.config;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record PluginConfig(
        TeleportSettings teleport,
        CombatSettings combat,
        HomeSettings homes,
        SpawnSettings spawn,
        RtpSettings rtp
) {

    public record TeleportSettings(
            double warmupSeconds,
            int cooldownSeconds,
            int requestExpirySeconds,
            int safeSearchRadius,
            int backMax,
            Set<String> blockedTargetWorlds
    ) {
    }

    public record CombatSettings(
            boolean enabled,
            int tagSeconds
    ) {
    }

    public record HomeSettings(
            int defaultMax,
            Map<String, Integer> rankLimits
    ) {
    }

    public record SpawnSettings(
            boolean useConfiguredSpawn,
            String world,
            double x,
            double y,
            double z,
            boolean centerOnBlock,
            float yaw,
            float pitch,
            boolean firstJoinTeleportEnabled,
            boolean firstJoinSetBedSpawn,
            boolean firstJoinKitEnabled,
            boolean firstJoinKitClearInventory,
            List<KitItem> starterKitItems,
            boolean respawnOverrideEnabled
    ) {
    }

    public record KitItem(
            String material,
            int amount
    ) {
    }

    public record RtpSettings(
            boolean enabled,
            String world,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int maxUsesDefault,
            Map<String, Integer> rankLimits,
            int maxAttempts
    ) {
    }
}
