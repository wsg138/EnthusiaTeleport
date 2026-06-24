package org.enthusia.teleport.rtp;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.config.PluginConfig;
import org.enthusia.teleport.teleport.SafeLocationFinder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class RtpManager {

    private final EnthusiaTeleportPlugin plugin;
    private final File file;
    private final Map<UUID, Integer> uses = new HashMap<>();
    private final Queue<RtpSearch> queuedSearches = new ArrayDeque<>();
    private final List<RtpSearch> activeSearches = new ArrayList<>();
    private final List<RecentRtp> recentRtp = new ArrayList<>();

    private boolean dirty;
    private boolean saveInProgress;
    private int chunkRequestsThisSecond;
    private long chunkRequestSecond;

    public RtpManager(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "rtp_uses.yml");
        load();
    }

    public void reload() {
        load();
        queuedSearches.clear();
        activeSearches.clear();
        chunkRequestsThisSecond = 0;
        chunkRequestSecond = 0L;
    }

    public void saveAll() {
        dirty = true;
        plugin.getPerformanceMonitor().increment("yaml.rtp.queued");
    }

    public void flushIfDirtyAsync() {
        if (!dirty) {
            plugin.getPerformanceMonitor().increment("yaml.rtp.skipped");
            return;
        }
        if (saveInProgress) {
            plugin.getPerformanceMonitor().increment("yaml.rtp.coalesced");
            return;
        }
        Map<UUID, Integer> snapshot = new HashMap<>(uses);
        dirty = false;
        saveInProgress = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean success = writeSnapshot(snapshot);
            Bukkit.getScheduler().runTask(plugin, () -> {
                saveInProgress = false;
                plugin.getPerformanceMonitor().increment(success ? "yaml.rtp.flushed" : "yaml.rtp.failed");
                if (dirty) {
                    flushIfDirtyAsync();
                }
            });
        });
    }

    public void flushBlocking() {
        if (!dirty && !saveInProgress) {
            plugin.getPerformanceMonitor().increment("yaml.rtp.skipped");
            return;
        }
        dirty = false;
        writeSnapshot(new HashMap<>(uses));
        saveInProgress = false;
        plugin.getPerformanceMonitor().increment("yaml.rtp.flushed");
    }

    private boolean writeSnapshot(Map<UUID, Integer> snapshot) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> entry : snapshot.entrySet()) {
            yaml.set(entry.getKey().toString(), entry.getValue());
        }

        try {
            file.getParentFile().mkdirs();
            yaml.save(file);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save rtp_uses.yml: " + exception.getMessage());
            return false;
        }
    }

    public int getUses(UUID uuid) {
        return uses.getOrDefault(uuid, 0);
    }

    public void incrementUse(UUID uuid) {
        uses.put(uuid, getUses(uuid) + 1);
        dirty = true;
        plugin.getPerformanceMonitor().increment("yaml.rtp.queued");
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

    public void enqueue(Player player) {
        PluginConfig.RtpSettings settings = plugin.getPluginConfigManager().current().rtp();
        if (!settings.enabled()) {
            plugin.getMessages().send(player, "rtp.disabled");
            return;
        }
        RtpSearch search = new RtpSearch(player.getUniqueId(), System.currentTimeMillis());
        if (!settings.queue().enabled()) {
            if (activeSearches.size() < settings.queue().maxActiveSearches()) {
                activeSearches.add(search);
                plugin.getPerformanceMonitor().increment("rtp.direct_started");
            } else {
                queuedSearches.add(search);
                plugin.getPerformanceMonitor().increment("rtp.direct_deferred");
            }
        } else {
            queuedSearches.add(search);
            plugin.getPerformanceMonitor().increment("rtp.queued");
        }
        plugin.getMessages().send(player, "rtp.search-queued");
    }

    public void tickQueue() {
        PluginConfig.RtpSettings settings = plugin.getPluginConfigManager().current().rtp();
        expireRecent(settings);
        resetChunkBudgetIfNeeded();

        while (activeSearches.size() < settings.queue().maxActiveSearches() && !queuedSearches.isEmpty()) {
            activeSearches.add(queuedSearches.poll());
        }

        plugin.getPerformanceMonitor().add("rtp.queue_size", queuedSearches.size());
        plugin.getPerformanceMonitor().add("rtp.active_searches", activeSearches.size());

        int checksRemaining = settings.queue().maxLocationChecksPerTick();
        Iterator<RtpSearch> iterator = activeSearches.iterator();
        while (iterator.hasNext()) {
            RtpSearch search = iterator.next();
            if (search.removeRequested()) {
                iterator.remove();
                plugin.getPerformanceMonitor().increment(search.removalCounter());
                plugin.getPerformanceMonitor().increment("rtp.queue_removals");
                continue;
            }
            Player player = Bukkit.getPlayer(search.playerId());
            if (player == null || !player.isOnline()) {
                iterator.remove();
                plugin.getPerformanceMonitor().increment("rtp.fail.offline");
                plugin.getPerformanceMonitor().increment("rtp.queue_removals");
                continue;
            }
            if (timedOut(search, settings)) {
                iterator.remove();
                plugin.getMessages().send(player, "rtp.search-timeout");
                plugin.getPerformanceMonitor().increment("rtp.fail.timeout");
                plugin.getPerformanceMonitor().increment(search.waitingForChunk() ? "rtp.fail.chunk_timeout" : "rtp.fail.search_timeout");
                if (search.waitingForChunk()) {
                    plugin.getPerformanceMonitor().increment("rtp.fail.stuck_waiting_search");
                }
                plugin.getPerformanceMonitor().increment("rtp.queue_removals");
                continue;
            }
            if (search.waitingForChunk()) {
                continue;
            }
            if (search.attempts() >= settings.safety().maxAttemptsPerPlayer()) {
                iterator.remove();
                plugin.getMessages().send(player, "teleport.safe-fallback-failed");
                plugin.getPerformanceMonitor().increment("rtp.fail.max_attempts");
                plugin.getPerformanceMonitor().increment("rtp.queue_removals");
                continue;
            }
            if (checksRemaining <= 0 || !tryConsumeChunkBudget(settings)) {
                break;
            }
            checksRemaining--;
            search.incrementAttempts();
            if (requestCandidate(search, player, settings) == CandidateRequestStatus.FAILED_REMOVE) {
                iterator.remove();
                plugin.getPerformanceMonitor().increment("rtp.queue_removals");
            }
        }
    }

    private CandidateRequestStatus requestCandidate(RtpSearch search, Player player, PluginConfig.RtpSettings settings) {
        World world = Bukkit.getWorld(settings.world());
        if (world == null) {
            plugin.getLogger().warning("RTP world not found: " + settings.world());
            plugin.getMessages().send(player, "teleport.safe-fallback-failed");
            plugin.getPerformanceMonitor().increment("rtp.fail.world_missing");
            plugin.getPerformanceMonitor().increment("rtp.fail.failed_world");
            return CandidateRequestStatus.FAILED_REMOVE;
        }

        int minX = Math.min(settings.minX(), settings.maxX());
        int maxX = Math.max(settings.minX(), settings.maxX());
        int minZ = Math.min(settings.minZ(), settings.maxZ());
        int maxZ = Math.max(settings.minZ(), settings.maxZ());
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int x = random.nextInt(minX, maxX + 1);
        int z = random.nextInt(minZ, maxZ + 1);

        if (!passesCheapSpacing(world, x, z, settings)) {
            plugin.getPerformanceMonitor().increment("rtp.fail.spacing");
            return CandidateRequestStatus.CONTINUE;
        }

        search.setWaitingForChunk(true);
        plugin.getPerformanceMonitor().increment("rtp.chunk_requests");
        world.getChunkAtAsync(x >> 4, z >> 4).whenComplete((Chunk chunk, Throwable throwable) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    search.setWaitingForChunk(false);
                    if (!activeSearches.contains(search)) {
                        return;
                    }
                    if (throwable != null || chunk == null) {
                        plugin.getPerformanceMonitor().increment("rtp.fail.chunk");
                        return;
                    }
                    validateCandidate(search, player, world, x, z, settings);
                }));
        return CandidateRequestStatus.CONTINUE;
    }

    private void validateCandidate(RtpSearch search, Player player, World world, int x, int z, PluginConfig.RtpSettings settings) {
        if (!player.isOnline()) {
            search.requestRemoval("rtp.fail.offline");
            return;
        }
        int y = world.getHighestBlockYAt(x, z) + 1;
        Location candidate = new Location(world, x + 0.5D, y, z + 0.5D, player.getLocation().getYaw(), player.getLocation().getPitch());
        SafeLocationFinder safeFinder = new SafeLocationFinder(settings.safety().safeSearchRadius());

        Location destination = safeFinder.isSafeTeleportLocation(candidate)
                ? candidate
                : safeFinder.findSafeTeleportLocation(candidate);

        plugin.getPerformanceMonitor().increment("rtp.attempts");
        if (destination == null || !passesFinalSpacing(destination, settings)) {
            plugin.getPerformanceMonitor().increment("rtp.fail.unsafe");
            return;
        }

        rememberRecent(destination);
        plugin.getTeleportManager().startTeleport(
                player,
                destination,
                false,
                null,
                "teleport.warmup-start",
                () -> incrementUse(player.getUniqueId())
        );
        search.requestRemoval("rtp.completed");
    }

    private boolean passesCheapSpacing(World world, int x, int z, PluginConfig.RtpSettings settings) {
        if (nearWorldBorder(world, x, z)) {
            return false;
        }
        Location spawn = plugin.getSpawnManager().getSpawnLocation();
        if (spawn != null && spawn.getWorld() != null && spawn.getWorld().equals(world)
                && distanceSq2d(spawn.getX(), spawn.getZ(), x, z) < square(settings.spacing().minDistanceFromSpawn())) {
            return false;
        }
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getWorld().equals(world)) {
                continue;
            }
            if (distanceSq2d(other.getLocation().getX(), other.getLocation().getZ(), x, z) < square(settings.spacing().minDistanceFromPlayers())) {
                return false;
            }
        }
        return true;
    }

    private boolean passesFinalSpacing(Location location, PluginConfig.RtpSettings settings) {
        for (RecentRtp recent : recentRtp) {
            if (!recent.worldName().equals(location.getWorld().getName())) {
                continue;
            }
            if (distanceSq2d(recent.x(), recent.z(), location.getX(), location.getZ()) < square(settings.spacing().minDistanceFromRecentRtp())) {
                return false;
            }
        }
        return true;
    }

    private boolean nearWorldBorder(World world, int x, int z) {
        double halfSize = world.getWorldBorder().getSize() / 2.0D;
        Location center = world.getWorldBorder().getCenter();
        double margin = 16.0D;
        return Math.abs(x - center.getX()) > halfSize - margin
                || Math.abs(z - center.getZ()) > halfSize - margin;
    }

    private void rememberRecent(Location location) {
        if (location.getWorld() == null) {
            return;
        }
        recentRtp.add(new RecentRtp(location.getWorld().getName(), location.getX(), location.getZ(), System.currentTimeMillis()));
    }

    private void expireRecent(PluginConfig.RtpSettings settings) {
        long cutoff = System.currentTimeMillis() - settings.spacing().recentRtpMemoryMinutes() * 60_000L;
        recentRtp.removeIf(recent -> recent.timestamp() < cutoff);
    }

    private boolean timedOut(RtpSearch search, PluginConfig.RtpSettings settings) {
        return System.currentTimeMillis() - search.startedAt() > settings.queue().timeoutSeconds() * 1000L;
    }

    private void resetChunkBudgetIfNeeded() {
        long second = System.currentTimeMillis() / 1000L;
        if (second != chunkRequestSecond) {
            chunkRequestSecond = second;
            chunkRequestsThisSecond = 0;
        }
    }

    private boolean tryConsumeChunkBudget(PluginConfig.RtpSettings settings) {
        if (chunkRequestsThisSecond >= settings.queue().maxChunkLoadRequestsPerSecond()) {
            plugin.getPerformanceMonitor().increment("rtp.chunk_budget_limited");
            return false;
        }
        chunkRequestsThisSecond++;
        return true;
    }

    private double distanceSq2d(double ax, double az, double bx, double bz) {
        double dx = ax - bx;
        double dz = az - bz;
        return dx * dx + dz * dz;
    }

    private double square(double value) {
        return value <= 0.0D ? 0.0D : value * value;
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
        dirty = false;
    }

    private static final class RtpSearch {
        private final UUID playerUuid;
        private final long startedAtMillis;
        private int attemptCount;
        private boolean waitingOnChunk;
        private boolean removalRequested;
        private String removalCounterName = "rtp.queue_removals";

        private RtpSearch(UUID playerId, long startedAt) {
            this.playerUuid = playerId;
            this.startedAtMillis = startedAt;
        }

        private UUID playerId() {
            return playerUuid;
        }

        private long startedAt() {
            return startedAtMillis;
        }

        private int attempts() {
            return attemptCount;
        }

        private void incrementAttempts() {
            attemptCount++;
        }

        private boolean waitingForChunk() {
            return waitingOnChunk;
        }

        private void setWaitingForChunk(boolean waitingForChunk) {
            this.waitingOnChunk = waitingForChunk;
        }

        private boolean removeRequested() {
            return removalRequested;
        }

        private String removalCounter() {
            return removalCounterName;
        }

        private void requestRemoval(String removalCounter) {
            this.removalRequested = true;
            this.removalCounterName = removalCounter;
        }
    }

    private enum CandidateRequestStatus {
        CONTINUE,
        FAILED_REMOVE
    }

    private record RecentRtp(String worldName, double x, double z, long timestamp) {
    }
}
