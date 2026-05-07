package org.enthusia.teleport.config;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record PluginConfig(
        int configVersion,
        TeleportSettings teleport,
        CombatSettings combat,
        HomeSettings homes,
        SpawnSettings spawn,
        RtpSettings rtp,
        PersistenceSettings persistence,
        LoggingSettings logging,
        MsgLogSettings msgLog,
        LastLocationBackstopSettings lastLocationBackstop,
        TabCacheSettings tabCache,
        DebugSettings debug
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
            int maxAttempts,
            QueueSettings queue,
            SpacingSettings spacing,
            SafetySettings safety
    ) {
    }

    public record QueueSettings(
            boolean enabled,
            int maxActiveSearches,
            int maxLocationChecksPerTick,
            int maxChunkLoadRequestsPerSecond,
            int timeoutSeconds
    ) {
    }

    public record SpacingSettings(
            double minDistanceFromSpawn,
            double minDistanceFromPlayers,
            double minDistanceFromRecentRtp,
            int recentRtpMemoryMinutes
    ) {
    }

    public record SafetySettings(
            int maxAttemptsPerPlayer,
            int safeSearchRadius
    ) {
    }

    public record PersistenceSettings(
            int flushIntervalSeconds
    ) {
    }

    public record LoggingSettings(
            int flushIntervalSeconds,
            int maxQueueSize
    ) {
    }

    public record MsgLogSettings(
            int maxDaysScanned,
            int maxResults,
            int timeoutSeconds
    ) {
    }

    public record LastLocationBackstopSettings(
            int intervalMinutes,
            int maxPlayersPerTick,
            boolean repairEnabled,
            boolean debugLogging
    ) {
    }

    public record TabCacheSettings(
            int refreshIntervalSeconds
    ) {
    }

    public record DebugSettings(
            boolean performanceEnabled,
            int performanceLogIntervalSeconds
    ) {
    }
}
