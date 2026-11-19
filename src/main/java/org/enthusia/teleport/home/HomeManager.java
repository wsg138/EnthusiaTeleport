package org.enthusia.teleport.home;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.config.PluginConfig;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
public final class HomeManager {

    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final EnthusiaTeleportPlugin plugin;
    private final File file;
    private final Map<UUID, Map<String, Home>> homes = new ConcurrentHashMap<>();
    private final Object writeLock = new Object();
    // Issued on the server thread. A blocking flush supersedes all older workers and callbacks.
    private volatile long latestWrite;
    private boolean preserveOriginal;
    private boolean dirty;
    private boolean saveInProgress;

    public HomeManager(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "homes.yml");
        if (!load()) {
            throw new IllegalStateException("Cannot safely open homes.yml. See the server log.");
        }
    }

    public void reload() {
        if (!reloadSafely()) {
            plugin.getLogger().warning("Home reload skipped. Current homes are still in memory.");
        }
    }

    public boolean reloadSafely() {
        return flushCurrentHomes() && load();
    }

    public void saveAll() {
        dirty = true;
        plugin.getPerformanceMonitor().increment("yaml.homes.queued");
    }

    public void flushIfDirtyAsync() {
        if (!dirty) {
            plugin.getPerformanceMonitor().increment("yaml.homes.skipped");
            return;
        }
        if (saveInProgress) {
            plugin.getPerformanceMonitor().increment("yaml.homes.coalesced");
            return;
        }
        Map<UUID, Map<String, Home>> snapshot = snapshot();
        long request = ++latestWrite;
        dirty = false;
        saveInProgress = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean success = persist(request, snapshot);
            if (request == latestWrite && plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> finishAsync(request, success));
            }
        });
    }

    private void finishAsync(long request, boolean success) {
        if (request != latestWrite) {
            return;
        }
        saveInProgress = false;
        dirty |= !success;
        plugin.getPerformanceMonitor().increment(success ? "yaml.homes.flushed" : "yaml.homes.failed");
        if (success && dirty) {
            flushIfDirtyAsync();
        }
    }

    public void flushBlocking() {
        flushCurrentHomes();
    }

    private boolean flushCurrentHomes() {
        if (!dirty && !saveInProgress) {
            plugin.getPerformanceMonitor().increment("yaml.homes.skipped");
            return true;
        }
        boolean success = persist(++latestWrite, snapshot());
        dirty = !success;
        saveInProgress = false;
        plugin.getPerformanceMonitor().increment(success ? "yaml.homes.flushed" : "yaml.homes.failed");
        return success;
    }

    private boolean persist(long request, Map<UUID, Map<String, Home>> snapshot) {
        synchronized (writeLock) {
            if (request != latestWrite) {
                return false;
            }
            Path temporary = null;
            try {
                if (preserveOriginal) {
                    backupUnreadableHomesFile();
                }
                temporary = Files.createTempFile(file.getParentFile().toPath(), "homes-", ".tmp");
                serialize(snapshot).save(temporary.toFile());
                try {
                    Files.move(temporary, file.toPath(), StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                return true;
            } catch (IOException exception) {
                plugin.getLogger().warning("Failed to save homes.yml: " + exception.getMessage());
                return false;
            } finally {
                if (temporary != null) {
                    try {
                        Files.deleteIfExists(temporary);
                    } catch (IOException cleanupFailure) {
                        plugin.getLogger().warning("Failed to clean up " + temporary + ": " + cleanupFailure.getMessage());
                    }
                }
            }
        }
    }

    private YamlConfiguration serialize(Map<UUID, Map<String, Home>> snapshot) {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, Home>> entry : snapshot.entrySet()) {
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

        return config;
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
        dirty = true;
    }

    public void deleteHome(UUID owner, String name) {
        if (name == null) {
            return;
        }
        getMap(owner).remove(normalizeName(name));
        dirty = true;
    }

    public void clearHomes(UUID owner) {
        getMap(owner).clear();
        dirty = true;
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

    private boolean load() {
        YamlConfiguration config = new YamlConfiguration();
        try {
            Files.createDirectories(file.getParentFile().toPath());
            if (Files.notExists(file.toPath())) {
                Files.createFile(file.toPath());
                dirty = !homes.isEmpty();
                preserveOriginal = false;
                return true;
            }
            config.load(file);
        } catch (InvalidConfigurationException malformed) {
            preserveOriginal = true;
            try {
                backupUnreadableHomesFile();
            } catch (IOException backupFailure) {
                plugin.getLogger().severe("Cannot preserve malformed homes.yml: " + backupFailure.getMessage());
                return false;
            }
            // On hot reload keep the last known good state. On first startup it is empty.
            dirty = !homes.isEmpty();
            return true;
        } catch (IOException failure) {
            preserveOriginal = true;
            plugin.getLogger().severe("Cannot read homes.yml: " + failure.getMessage());
            return false;
        }

        preserveOriginal = false;
        homes.clear();
        boolean repaired = false;

        for (String key : config.getKeys(false)) {
            UUID owner = parseOwnerId(key);
            if (owner == null) {
                continue;
            }

            ConfigurationSection ownerSection = config.getConfigurationSection(key);
            if (ownerSection == null) {
                continue;
            }

            LoadResult result = loadOwnerHomes(owner, ownerSection);
            repaired |= result.repaired();
            if (!result.homes().isEmpty()) {
                putLoadedHomes(owner, result.homes());
            }
        }

        dirty = repaired;
        return !repaired || flushCurrentHomes();
    }

    private UUID parseOwnerId(String key) {
        try {
            return UUID.fromString(key);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private LoadResult loadOwnerHomes(UUID owner, ConfigurationSection ownerSection) {
        List<Home> loadedHomes = new ArrayList<>();
        boolean repaired = false;
        for (String homeKey : ownerSection.getKeys(false)) {
            ConfigurationSection homeSection = ownerSection.getConfigurationSection(homeKey);
            if (homeSection == null) {
                continue;
            }

            HomeLoad homeLoad = loadHome(owner, homeKey, homeSection);
            repaired |= homeLoad.repaired();
            if (homeLoad.home() != null) {
                loadedHomes.add(homeLoad.home());
            }
        }
        loadedHomes.sort(Comparator.comparingLong(Home::getCreatedAt).thenComparing(Home::getName, String.CASE_INSENSITIVE_ORDER));
        return new LoadResult(loadedHomes, repaired);
    }

    private HomeLoad loadHome(UUID owner, String homeKey, ConfigurationSection homeSection) {
        String displayName = homeSection.getString("name", homeKey);
        String normalized = normalizeName(displayName);
        if (normalized.isEmpty() || normalized.contains(".")) {
            return HomeLoad.repairedOnly();
        }
        String worldName = homeSection.getString("world");
        if (worldName == null || worldName.isBlank()) {
            return HomeLoad.empty();
        }

        long createdAt = homeSection.getLong("created", 0L);
        boolean repaired = false;
        if (createdAt <= 0L) {
            createdAt = System.currentTimeMillis();
            repaired = true;
        }

        return new HomeLoad(new Home(
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
        ), repaired);
    }

    private void putLoadedHomes(UUID owner, List<Home> loadedHomes) {
        Map<String, Home> ownerHomes = homes.computeIfAbsent(owner, unused -> new LinkedHashMap<>());
        for (Home home : loadedHomes) {
            ownerHomes.put(home.getKey(), home);
        }
    }

    private Map<String, Home> getMap(UUID owner) {
        return homes.computeIfAbsent(owner, unused -> new LinkedHashMap<>());
    }

    private Map<UUID, Map<String, Home>> snapshot() {
        Map<UUID, Map<String, Home>> copy = new LinkedHashMap<>();
        for (Map.Entry<UUID, Map<String, Home>> entry : homes.entrySet()) {
            copy.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
        return copy;
    }

    private void backupUnreadableHomesFile() throws IOException {
        String timestamp = LocalDateTime.now().format(BACKUP_TIMESTAMP).replace(':', '-');
        File backup = new File(file.getParentFile(), "homes-unreadable-" + timestamp + ".yml");
        int suffix = 1;
        while (backup.exists()) {
            backup = new File(file.getParentFile(), "homes-unreadable-" + timestamp + "-" + suffix + ".yml");
            suffix++;
        }

        if (!Files.isRegularFile(file.toPath())) {
            throw new IOException("Cannot back up homes.yml as a regular file.");
        }
        Files.copy(file.toPath(), backup.toPath());
        preserveOriginal = false;
        plugin.getLogger().warning("Preserved unreadable homes.yml in " + backup.getName());
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private record LoadResult(List<Home> homes, boolean repaired) {
    }

    private record HomeLoad(Home home, boolean repaired) {
        private static HomeLoad empty() {
            return new HomeLoad(null, false);
        }

        private static HomeLoad repairedOnly() {
            return new HomeLoad(null, true);
        }
    }
}
