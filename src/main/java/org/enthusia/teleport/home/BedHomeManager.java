package org.enthusia.teleport.home;

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.enthusia.teleport.EnthusiaTeleportPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Owns persistent, named bed homes independently of Minecraft's vanilla respawn point.
 * Respawn-anchor changes are deliberately ignored; only successful BED spawn changes
 * create or refresh a bed home.
 */
public final class BedHomeManager implements Listener {

    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss");
    private static final Set<String> RESERVED_NAMES = Set.of("list", "delete", "del", "remove", "rename", "help");
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final int BED_SEARCH_HORIZONTAL_RADIUS = 3;
    private static final int BED_SEARCH_VERTICAL_RADIUS = 2;
    private static final double RECENT_BED_MAX_DISTANCE_SQUARED = 64.0D;

    private final EnthusiaTeleportPlugin plugin;
    private final File file;
    private final File tempFile;
    private final Object ioLock = new Object();
    private final Map<UUID, Map<String, BedHome>> beds = new ConcurrentHashMap<>();
    private final Map<UUID, List<BedBreakNotice>> pendingBreakNotices = new ConcurrentHashMap<>();
    private final Map<UUID, BedBlockKey> recentBedInteractions = new HashMap<>();

    private long mutationVersion;
    private long persistedVersion;
    private volatile long newestRequestedWriteVersion;
    private volatile boolean saveInProgress;

    public BedHomeManager(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "beds.yml");
        this.tempFile = new File(plugin.getDataFolder(), "beds.yml.tmp");
        load();
    }

    public void reload() {
        recentBedInteractions.clear();
        load();
    }

    public Collection<BedHome> getBeds(UUID owner) {
        List<BedHome> result = new ArrayList<>(getMap(owner).values());
        result.sort(Comparator.comparing(BedHome::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public BedHome getBed(UUID owner, String name) {
        if (name == null) {
            return null;
        }
        return getMap(owner).get(normalizeName(name));
    }

    public BedHome getMostRecentBed(UUID owner) {
        return getMap(owner).values().stream()
                .max(Comparator.comparingLong(BedHome::getLastUsedAt)
                        .thenComparingLong(BedHome::getCreatedAt))
                .orElse(null);
    }

    public BedHome deleteBed(UUID owner, String name) {
        if (name == null) {
            return null;
        }
        BedHome removed = getMap(owner).remove(normalizeName(name));
        if (removed != null) {
            markDirty();
        }
        return removed;
    }

    public RenameResult renameBed(UUID owner, String oldName, String newName) {
        String oldKey = normalizeName(oldName);
        String newKey = normalizeName(newName);
        if (!isValidName(newName)) {
            return RenameResult.INVALID_NAME;
        }

        Map<String, BedHome> ownerBeds = getMap(owner);
        BedHome existing = ownerBeds.get(oldKey);
        if (existing == null) {
            return RenameResult.NOT_FOUND;
        }
        if (!oldKey.equals(newKey) && ownerBeds.containsKey(newKey)) {
            return RenameResult.DUPLICATE;
        }

        BedHome renamed = copyWithName(existing, newKey, newName.trim());
        ownerBeds.remove(oldKey);
        ownerBeds.put(newKey, renamed);
        markDirty();
        return RenameResult.SUCCESS;
    }

    public boolean isValidName(String name) {
        if (name == null) {
            return false;
        }
        String trimmed = name.trim();
        String normalized = normalizeName(trimmed);
        return VALID_NAME.matcher(trimmed).matches() && !RESERVED_NAMES.contains(normalized);
    }

    /**
     * Returns false only when the bed's world is loaded and the saved bed block is no longer there.
     * An unavailable/unloaded world is preserved so a temporary world-load problem cannot delete data.
     */
    public boolean isBedPresent(BedHome home) {
        World world = Bukkit.getWorld(home.getWorldName());
        if (world == null) {
            return true;
        }
        BedBlockKey key = canonicalBed(world.getBlockAt(home.getBedX(), home.getBedY(), home.getBedZ()));
        return key != null && home.isAt(key.worldName(), key.x(), key.y(), key.z());
    }

    /**
     * Resolves a bed teleport from live state. This is safe to call again when a teleport warmup completes.
     */
    public Location resolveTeleportLocation(UUID owner, String bedKey) {
        BedHome current = getBed(owner, bedKey);
        if (current == null || !isBedPresent(current)) {
            return null;
        }
        return current.toLocation();
    }

    public void saveAll() {
        if (mutationVersion <= persistedVersion) {
            markDirty();
        }
        plugin.getPerformanceMonitor().increment("yaml.beds.queued");
    }

    public void flushIfDirtyAsync() {
        if (mutationVersion <= persistedVersion) {
            plugin.getPerformanceMonitor().increment("yaml.beds.skipped");
            return;
        }
        if (saveInProgress) {
            plugin.getPerformanceMonitor().increment("yaml.beds.coalesced");
            return;
        }

        PersistenceSnapshot snapshot = snapshot();
        newestRequestedWriteVersion = Math.max(newestRequestedWriteVersion, snapshot.version());
        saveInProgress = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            WriteResult result = writeSnapshot(snapshot);
            try {
                Bukkit.getScheduler().runTask(plugin, () -> finishAsyncWrite(snapshot, result));
            } catch (RuntimeException ignored) {
                // Plugin shutdown may reject the callback. flushBlocking() fences stale writes on disable.
                saveInProgress = false;
            }
        });
    }

    public void flushBlocking() {
        if (mutationVersion <= persistedVersion && !saveInProgress) {
            plugin.getPerformanceMonitor().increment("yaml.beds.skipped");
            return;
        }

        PersistenceSnapshot snapshot = snapshot();
        newestRequestedWriteVersion = Math.max(newestRequestedWriteVersion, snapshot.version());
        WriteResult result = writeSnapshot(snapshot);
        saveInProgress = false;

        if (result == WriteResult.WRITTEN) {
            persistedVersion = Math.max(persistedVersion, snapshot.version());
            plugin.getPerformanceMonitor().increment("yaml.beds.flushed");
        } else if (result == WriteResult.SKIPPED_STALE) {
            plugin.getPerformanceMonitor().increment("yaml.beds.coalesced");
        } else {
            plugin.getPerformanceMonitor().increment("yaml.beds.failed");
        }
    }

    private void finishAsyncWrite(PersistenceSnapshot snapshot, WriteResult result) {
        saveInProgress = false;
        if (result == WriteResult.WRITTEN) {
            persistedVersion = Math.max(persistedVersion, snapshot.version());
            plugin.getPerformanceMonitor().increment("yaml.beds.flushed");
        } else if (result == WriteResult.SKIPPED_STALE) {
            plugin.getPerformanceMonitor().increment("yaml.beds.coalesced");
        } else {
            plugin.getPerformanceMonitor().increment("yaml.beds.failed");
            return;
        }

        if (mutationVersion > persistedVersion) {
            flushIfDirtyAsync();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        BedBlockKey key = canonicalBed(event.getBed());
        if (key == null) {
            return;
        }

        UUID playerId = event.getPlayer().getUniqueId();
        recentBedInteractions.put(playerId, key);
        Bukkit.getScheduler().runTaskLater(plugin, () -> recentBedInteractions.remove(playerId, key), 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnSet(PlayerSetSpawnEvent event) {
        if (event.getCause() != PlayerSetSpawnEvent.Cause.BED || event.getLocation() == null) {
            return;
        }

        UUID playerId = event.getPlayer().getUniqueId();
        BedBlockKey recent = recentBedInteractions.remove(playerId);
        BedBlockKey bedKey = recent != null && isNear(recent, event.getLocation())
                ? recent
                : findUniqueBedNear(event.getLocation());

        if (bedKey == null) {
            plugin.getLogger().warning("Could not unambiguously identify the bed while saving a bed home for "
                    + event.getPlayer().getName() + ". No bed home was changed.");
            return;
        }

        upsertBed(event.getPlayer(), bedKey, event.getLocation(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedBreak(BlockBreakEvent event) {
        handleDestroyedBed(canonicalBed(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedBurn(BlockBurnEvent event) {
        handleDestroyedBed(canonicalBed(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        handleDestroyedBlocks(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        handleDestroyedBlocks(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> deliverPendingBreakNotices(player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        recentBedInteractions.remove(event.getPlayer().getUniqueId());
    }

    private void upsertBed(Player player, BedBlockKey bedKey, Location teleportLocation, boolean announceNew) {
        Map<String, BedHome> ownerBeds = getMap(player.getUniqueId());
        BedHome existing = findBedAt(player.getUniqueId(), bedKey);
        long now = System.currentTimeMillis();

        if (existing != null) {
            BedHome refreshed = new BedHome(
                    existing.getOwner(),
                    existing.getKey(),
                    existing.getName(),
                    bedKey.worldName(),
                    bedKey.x(), bedKey.y(), bedKey.z(),
                    teleportLocation.getX(), teleportLocation.getY(), teleportLocation.getZ(),
                    teleportLocation.getYaw(), teleportLocation.getPitch(),
                    existing.getCreatedAt(), now
            );
            ownerBeds.put(existing.getKey(), refreshed);
            markDirty();
            return;
        }

        String name = nextDefaultName(ownerBeds);
        String key = normalizeName(name);
        BedHome bedHome = new BedHome(
                player.getUniqueId(), key, name,
                bedKey.worldName(), bedKey.x(), bedKey.y(), bedKey.z(),
                teleportLocation.getX(), teleportLocation.getY(), teleportLocation.getZ(),
                teleportLocation.getYaw(), teleportLocation.getPitch(),
                now, now
        );
        ownerBeds.put(key, bedHome);
        markDirty();

        if (announceNew) {
            player.sendMessage(plugin.getMessages().color(
                    "&aSaved this bed as &e(" + name + ")&a. Rename it with &e/bed rename " + name + " <new_name>&a."
            ));
        }
    }

    private BedHome findBedAt(UUID owner, BedBlockKey key) {
        for (BedHome home : getMap(owner).values()) {
            if (home.isAt(key.worldName(), key.x(), key.y(), key.z())) {
                return home;
            }
        }
        return null;
    }

    private String nextDefaultName(Map<String, BedHome> ownerBeds) {
        if (!ownerBeds.containsKey("bed")) {
            return "bed";
        }
        int suffix = 2;
        while (ownerBeds.containsKey("bed" + suffix)) {
            suffix++;
        }
        return "bed" + suffix;
    }

    private BedHome copyWithName(BedHome source, String key, String name) {
        return new BedHome(
                source.getOwner(), key, name, source.getWorldName(),
                source.getBedX(), source.getBedY(), source.getBedZ(),
                source.getX(), source.getY(), source.getZ(),
                source.getYaw(), source.getPitch(),
                source.getCreatedAt(), source.getLastUsedAt()
        );
    }

    private void handleDestroyedBlocks(List<Block> blocks) {
        Set<BedBlockKey> handled = new HashSet<>();
        for (Block block : blocks) {
            BedBlockKey key = canonicalBed(block);
            if (key != null && handled.add(key)) {
                handleDestroyedBed(key);
            }
        }
    }

    private void handleDestroyedBed(BedBlockKey key) {
        if (key == null) {
            return;
        }

        List<BrokenBed> brokenBeds = new ArrayList<>();
        for (Map.Entry<UUID, Map<String, BedHome>> ownerEntry : beds.entrySet()) {
            Iterator<Map.Entry<String, BedHome>> iterator = ownerEntry.getValue().entrySet().iterator();
            while (iterator.hasNext()) {
                BedHome home = iterator.next().getValue();
                if (home.isAt(key.worldName(), key.x(), key.y(), key.z())) {
                    iterator.remove();
                    brokenBeds.add(new BrokenBed(ownerEntry.getKey(), home.getName()));
                }
            }
        }

        if (brokenBeds.isEmpty()) {
            return;
        }

        long brokenAt = System.currentTimeMillis();
        for (BrokenBed brokenBed : brokenBeds) {
            Player online = Bukkit.getPlayer(brokenBed.owner());
            if (online != null && online.isOnline()) {
                sendBrokenMessage(online, brokenBed.name(), null);
            } else {
                pendingBreakNotices
                        .computeIfAbsent(brokenBed.owner(), unused -> new ArrayList<>())
                        .add(new BedBreakNotice(brokenBed.name(), brokenAt));
            }
        }

        markDirty();
        // Destruction notifications are important enough to enqueue persistence immediately.
        flushIfDirtyAsync();
    }

    private void deliverPendingBreakNotices(Player player) {
        if (!player.isOnline()) {
            return;
        }

        List<BedBreakNotice> notices = pendingBreakNotices.remove(player.getUniqueId());
        if (notices == null || notices.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        for (BedBreakNotice notice : notices) {
            sendBrokenMessage(player, notice.name(), formatElapsed(now - notice.brokenAt()));
        }
        markDirty();
        flushIfDirtyAsync();
    }

    private void sendBrokenMessage(Player player, String name, String elapsed) {
        if (elapsed == null) {
            player.sendMessage(plugin.getMessages().color("&cYour bed (&e" + name + "&c) was broken."));
            return;
        }
        player.sendMessage(plugin.getMessages().color(
                "&cYour bed (&e" + name + "&c) was broken &e" + elapsed + "&c ago."
        ));
    }

    private String formatElapsed(long millis) {
        long seconds = Math.max(1L, millis / 1000L);
        if (seconds < 60L) {
            return amount(seconds, "second");
        }

        long minutes = seconds / 60L;
        if (minutes < 60L) {
            return amount(minutes, "minute");
        }

        long hours = minutes / 60L;
        if (hours < 24L) {
            long remainderMinutes = minutes % 60L;
            return remainderMinutes == 0L
                    ? amount(hours, "hour")
                    : amount(hours, "hour") + " " + amount(remainderMinutes, "minute");
        }

        long days = hours / 24L;
        long remainderHours = hours % 24L;
        return remainderHours == 0L
                ? amount(days, "day")
                : amount(days, "day") + " " + amount(remainderHours, "hour");
    }

    private String amount(long value, String unit) {
        return value + " " + unit + (value == 1L ? "" : "s");
    }

    private boolean isNear(BedBlockKey bed, Location location) {
        World world = location.getWorld();
        if (world == null || !bed.worldName().equals(world.getName())) {
            return false;
        }
        double dx = bed.x() + 0.5D - location.getX();
        double dy = bed.y() + 0.5D - location.getY();
        double dz = bed.z() + 0.5D - location.getZ();
        return dx * dx + dy * dy + dz * dz <= RECENT_BED_MAX_DISTANCE_SQUARED;
    }

    private BedBlockKey findUniqueBedNear(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }

        Set<BedBlockKey> candidates = new HashSet<>();
        int centerX = location.getBlockX();
        int centerY = location.getBlockY();
        int centerZ = location.getBlockZ();
        for (int y = centerY - BED_SEARCH_VERTICAL_RADIUS; y <= centerY + BED_SEARCH_VERTICAL_RADIUS; y++) {
            for (int x = centerX - BED_SEARCH_HORIZONTAL_RADIUS; x <= centerX + BED_SEARCH_HORIZONTAL_RADIUS; x++) {
                for (int z = centerZ - BED_SEARCH_HORIZONTAL_RADIUS; z <= centerZ + BED_SEARCH_HORIZONTAL_RADIUS; z++) {
                    BedBlockKey candidate = canonicalBed(world.getBlockAt(x, y, z));
                    if (candidate != null) {
                        candidates.add(candidate);
                    }
                }
            }
        }

        return candidates.size() == 1 ? candidates.iterator().next() : null;
    }

    private BedBlockKey canonicalBed(Block block) {
        if (!(block.getBlockData() instanceof Bed bedData)) {
            return null;
        }

        Block head = bedData.getPart() == Bed.Part.HEAD
                ? block
                : block.getRelative(bedData.getFacing());
        return new BedBlockKey(head.getWorld().getName(), head.getX(), head.getY(), head.getZ());
    }

    private Map<String, BedHome> getMap(UUID owner) {
        return beds.computeIfAbsent(owner, unused -> new LinkedHashMap<>());
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private void markDirty() {
        mutationVersion++;
    }

    private void load() {
        beds.clear();
        pendingBreakNotices.clear();
        recentBedInteractions.clear();
        saveInProgress = false;
        mutationVersion = 0L;
        persistedVersion = 0L;
        newestRequestedWriteVersion = 0L;

        ensureFileExists();
        if (!file.exists() || file.length() == 0L) {
            return;
        }

        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            File backup = backupUnreadableFile();
            plugin.getLogger().severe("Could not load beds.yml. Bed homes were left empty for this startup and the unreadable "
                    + "file was moved to " + backup.getName() + ". Cause: " + exception.getMessage());
            return;
        }

        loadBeds(config.getConfigurationSection("beds"));
        loadNotices(config.getConfigurationSection("notifications"));
    }

    private void loadBeds(ConfigurationSection root) {
        if (root == null) {
            return;
        }

        for (String ownerKey : root.getKeys(false)) {
            UUID owner = parseUuid(ownerKey);
            ConfigurationSection ownerSection = root.getConfigurationSection(ownerKey);
            if (owner == null || ownerSection == null) {
                continue;
            }

            Map<String, BedHome> ownerBeds = getMap(owner);
            for (String bedKey : ownerSection.getKeys(false)) {
                ConfigurationSection section = ownerSection.getConfigurationSection(bedKey);
                if (section == null) {
                    continue;
                }
                BedHome home = loadBed(owner, bedKey, section);
                if (home != null) {
                    ownerBeds.put(home.getKey(), home);
                }
            }
        }
    }

    private BedHome loadBed(UUID owner, String fallbackKey, ConfigurationSection section) {
        String name = section.getString("name", fallbackKey);
        String key = normalizeName(name);
        String world = section.getString("world");
        if (!isValidName(name) || world == null || world.isBlank()) {
            plugin.getLogger().warning("Ignoring invalid bed home entry '" + fallbackKey + "' for " + owner + ".");
            return null;
        }

        long createdAt = Math.max(1L, section.getLong("created", System.currentTimeMillis()));
        long lastUsedAt = Math.max(createdAt, section.getLong("last-used", createdAt));
        return new BedHome(
                owner, key, name, world,
                section.getInt("bed-x"), section.getInt("bed-y"), section.getInt("bed-z"),
                section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw"), (float) section.getDouble("pitch"),
                createdAt, lastUsedAt
        );
    }

    private void loadNotices(ConfigurationSection root) {
        if (root == null) {
            return;
        }

        for (String ownerKey : root.getKeys(false)) {
            UUID owner = parseUuid(ownerKey);
            ConfigurationSection ownerSection = root.getConfigurationSection(ownerKey);
            if (owner == null || ownerSection == null) {
                continue;
            }

            List<BedBreakNotice> notices = new ArrayList<>();
            for (String noticeKey : ownerSection.getKeys(false)) {
                ConfigurationSection section = ownerSection.getConfigurationSection(noticeKey);
                if (section == null) {
                    continue;
                }
                String name = section.getString("name");
                long brokenAt = section.getLong("broken", 0L);
                if (name != null && !name.isBlank() && brokenAt > 0L) {
                    notices.add(new BedBreakNotice(name, brokenAt));
                }
            }
            notices.sort(Comparator.comparingLong(BedBreakNotice::brokenAt));
            if (!notices.isEmpty()) {
                pendingBreakNotices.put(owner, notices);
            }
        }
    }

    private UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void ensureFileExists() {
        if (file.exists()) {
            return;
        }
        try {
            file.getParentFile().mkdirs();
            file.createNewFile();
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to create beds.yml: " + exception.getMessage());
        }
    }

    private File backupUnreadableFile() {
        String timestamp = LocalDateTime.now().format(BACKUP_TIMESTAMP);
        File backup = new File(file.getParentFile(), "beds-unreadable-" + timestamp + ".yml");
        int suffix = 1;
        while (backup.exists()) {
            backup = new File(file.getParentFile(), "beds-unreadable-" + timestamp + "-" + suffix++ + ".yml");
        }

        try {
            file.getParentFile().mkdirs();
            Files.move(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            file.createNewFile();
        } catch (IOException exception) {
            plugin.getLogger().severe("Failed to move unreadable beds.yml: " + exception.getMessage());
        }
        return backup;
    }

    private PersistenceSnapshot snapshot() {
        Map<UUID, Map<String, BedHome>> bedCopy = new LinkedHashMap<>();
        for (Map.Entry<UUID, Map<String, BedHome>> entry : beds.entrySet()) {
            bedCopy.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }

        Map<UUID, List<BedBreakNotice>> noticeCopy = new LinkedHashMap<>();
        for (Map.Entry<UUID, List<BedBreakNotice>> entry : pendingBreakNotices.entrySet()) {
            noticeCopy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return new PersistenceSnapshot(mutationVersion, bedCopy, noticeCopy);
    }

    private WriteResult writeSnapshot(PersistenceSnapshot snapshot) {
        synchronized (ioLock) {
            if (snapshot.version() < newestRequestedWriteVersion) {
                return WriteResult.SKIPPED_STALE;
            }

            YamlConfiguration config = new YamlConfiguration();
            ConfigurationSection bedsRoot = config.createSection("beds");
            for (Map.Entry<UUID, Map<String, BedHome>> ownerEntry : snapshot.beds().entrySet()) {
                if (ownerEntry.getValue().isEmpty()) {
                    continue;
                }
                ConfigurationSection ownerSection = bedsRoot.createSection(ownerEntry.getKey().toString());
                for (BedHome home : ownerEntry.getValue().values()) {
                    ConfigurationSection section = ownerSection.createSection(home.getKey());
                    section.set("name", home.getName());
                    section.set("world", home.getWorldName());
                    section.set("bed-x", home.getBedX());
                    section.set("bed-y", home.getBedY());
                    section.set("bed-z", home.getBedZ());
                    section.set("x", home.getX());
                    section.set("y", home.getY());
                    section.set("z", home.getZ());
                    section.set("yaw", (double) home.getYaw());
                    section.set("pitch", (double) home.getPitch());
                    section.set("created", home.getCreatedAt());
                    section.set("last-used", home.getLastUsedAt());
                }
            }

            ConfigurationSection noticesRoot = config.createSection("notifications");
            for (Map.Entry<UUID, List<BedBreakNotice>> ownerEntry : snapshot.notices().entrySet()) {
                if (ownerEntry.getValue().isEmpty()) {
                    continue;
                }
                ConfigurationSection ownerSection = noticesRoot.createSection(ownerEntry.getKey().toString());
                int index = 0;
                for (BedBreakNotice notice : ownerEntry.getValue()) {
                    ConfigurationSection section = ownerSection.createSection(String.valueOf(index++));
                    section.set("name", notice.name());
                    section.set("broken", notice.brokenAt());
                }
            }

            try {
                file.getParentFile().mkdirs();
                config.save(tempFile);
                moveTempIntoPlace();
                return WriteResult.WRITTEN;
            } catch (IOException exception) {
                plugin.getLogger().warning("Failed to save beds.yml: " + exception.getMessage());
                return WriteResult.FAILED;
            }
        }
    }

    private void moveTempIntoPlace() throws IOException {
        try {
            Files.move(
                    tempFile.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public enum RenameResult {
        SUCCESS,
        NOT_FOUND,
        INVALID_NAME,
        DUPLICATE
    }

    private enum WriteResult {
        WRITTEN,
        SKIPPED_STALE,
        FAILED
    }

    private record BedBlockKey(String worldName, int x, int y, int z) {
    }

    private record BedBreakNotice(String name, long brokenAt) {
    }

    private record BrokenBed(UUID owner, String name) {
    }

    private record PersistenceSnapshot(
            long version,
            Map<UUID, Map<String, BedHome>> beds,
            Map<UUID, List<BedBreakNotice>> notices
    ) {
    }
}
