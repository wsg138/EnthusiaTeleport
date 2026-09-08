package org.enthusia.teleport.home;

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.configuration.ConfigurationSection;
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
import org.enthusia.teleport.EnthusiaTeleportPlugin;

import java.io.File;
import java.io.IOException;
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

/**
 * Owns persistent, named bed homes independently of Minecraft's current respawn point.
 *
 * <p>This separation is intentional: using a respawn anchor is allowed to update the
 * vanilla respawn point without replacing the player's saved bed destinations.</p>
 */
public final class BedHomeManager implements Listener {

    private static final Set<String> RESERVED_NAMES = Set.of("list", "delete", "del", "remove", "rename", "help");

    private final EnthusiaTeleportPlugin plugin;
    private final File file;
    private final Map<UUID, Map<String, BedHome>> beds = new ConcurrentHashMap<>();
    private final Map<UUID, List<BedBreakNotice>> pendingBreakNotices = new ConcurrentHashMap<>();
    private final Set<UUID> migratedOwners = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BedBlockKey> recentBedInteractions = new HashMap<>();
    private boolean dirty;
    private boolean saveInProgress;

    public BedHomeManager(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "beds.yml");
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
                .max(Comparator.comparingLong(BedHome::getLastUsedAt))
                .orElse(null);
    }

    public BedHome deleteBed(UUID owner, String name) {
        if (name == null) {
            return null;
        }
        BedHome removed = getMap(owner).remove(normalizeName(name));
        if (removed != null) {
            dirty = true;
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
        dirty = true;
        return RenameResult.SUCCESS;
    }

    public boolean isValidName(String name) {
        String normalized = normalizeName(name);
        return !normalized.isEmpty() && !normalized.contains(".") && !RESERVED_NAMES.contains(normalized);
    }

    public boolean isBedPresent(BedHome home) {
        World world = Bukkit.getWorld(home.getWorldName());
        if (world == null) {
            return true;
        }
        BedBlockKey key = canonicalBed(world.getBlockAt(home.getBedX(), home.getBedY(), home.getBedZ()));
        return key != null && home.isAt(key.worldName(), key.x(), key.y(), key.z());
    }

    public void saveAll() {
        dirty = true;
        plugin.getPerformanceMonitor().increment("yaml.beds.queued");
    }

    public void flushIfDirtyAsync() {
        if (!dirty) {
            plugin.getPerformanceMonitor().increment("yaml.beds.skipped");
            return;
        }
        if (saveInProgress) {
            plugin.getPerformanceMonitor().increment("yaml.beds.coalesced");
            return;
        }

        PersistenceSnapshot snapshot = snapshot();
        dirty = false;
        saveInProgress = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean success = writeSnapshot(snapshot);
            Bukkit.getScheduler().runTask(plugin, () -> {
                saveInProgress = false;
                plugin.getPerformanceMonitor().increment(success ? "yaml.beds.flushed" : "yaml.beds.failed");
                if (dirty) {
                    flushIfDirtyAsync();
                }
            });
        });
    }

    public void flushBlocking() {
        if (!dirty && !saveInProgress) {
            plugin.getPerformanceMonitor().increment("yaml.beds.skipped");
            return;
        }
        PersistenceSnapshot snapshot = snapshot();
        dirty = false;
        writeSnapshot(snapshot);
        saveInProgress = false;
        plugin.getPerformanceMonitor().increment("yaml.beds.flushed");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        BedBlockKey key = canonicalBed(event.getBed());
        if (key == null) {
            return;
        }

        UUID playerId = event.getPlayer().getUniqueId();
        recentBedInteractions.put(playerId, key);
        Bukkit.getScheduler().runTaskLater(plugin, () -> recentBedInteractions.remove(playerId, key), 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnSet(PlayerSetSpawnEvent event) {
        if (event.getCause() != PlayerSetSpawnEvent.Cause.BED || event.getLocation() == null) {
            return;
        }

        UUID playerId = event.getPlayer().getUniqueId();
        BedBlockKey bedKey = recentBedInteractions.remove(playerId);
        if (bedKey == null) {
            bedKey = findBedNear(event.getLocation());
        }
        if (bedKey == null) {
            plugin.getLogger().warning("Could not identify bed block while saving a bed home for " + event.getPlayer().getName());
            return;
        }

        upsertBed(event.getPlayer(), bedKey, event.getLocation(), true);
        migratedOwners.add(playerId);
        dirty = true;
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
        migrateLegacyBed(player);

        Bukkit.getScheduler().runTask(plugin, () -> deliverPendingBreakNotices(player));
    }

    private void migrateLegacyBed(Player player) {
        UUID owner = player.getUniqueId();
        if (!migratedOwners.add(owner)) {
            return;
        }

        Location legacySpawn = player.getBedSpawnLocation();
        if (legacySpawn != null) {
            BedBlockKey bedKey = findBedNear(legacySpawn);
            if (bedKey != null && findBedAt(owner, bedKey) == null) {
                upsertBed(player, bedKey, legacySpawn, false);
            }
        }
        dirty = true;
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
            dirty = true;
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
        dirty = true;

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
                Map.Entry<String, BedHome> bedEntry = iterator.next();
                BedHome home = bedEntry.getValue();
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
        dirty = true;
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
        dirty = true;
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

    private BedBlockKey findBedNear(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }

        BedBlockKey best = null;
        double bestDistance = Double.MAX_VALUE;
        int centerX = location.getBlockX();
        int centerY = location.getBlockY();
        int centerZ = location.getBlockZ();
        for (int y = centerY - 1; y <= centerY + 1; y++) {
            for (int x = centerX - 2; x <= centerX + 2; x++) {
                for (int z = centerZ - 2; z <= centerZ + 2; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    BedBlockKey candidate = canonicalBed(block);
                    if (candidate == null) {
                        continue;
                    }
                    double distance = block.getLocation().add(0.5D, 0.5D, 0.5D).distanceSquared(location);
                    if (distance < bestDistance) {
                        best = candidate;
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
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

    private void load() {
        beds.clear();
        pendingBreakNotices.clear();
        migratedOwners.clear();
        ensureFileExists();

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        loadBeds(config.getConfigurationSection("beds"));
        loadNotices(config.getConfigurationSection("notifications"));
        for (String rawOwner : config.getStringList("migrated")) {
            UUID owner = parseUuid(rawOwner);
            if (owner != null) {
                migratedOwners.add(owner);
            }
        }
        dirty = false;
        saveInProgress = false;
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
            return null;
        }

        long createdAt = section.getLong("created", System.currentTimeMillis());
        long lastUsedAt = section.getLong("last-used", createdAt);
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

    private PersistenceSnapshot snapshot() {
        Map<UUID, Map<String, BedHome>> bedCopy = new LinkedHashMap<>();
        for (Map.Entry<UUID, Map<String, BedHome>> entry : beds.entrySet()) {
            bedCopy.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }

        Map<UUID, List<BedBreakNotice>> noticeCopy = new LinkedHashMap<>();
        for (Map.Entry<UUID, List<BedBreakNotice>> entry : pendingBreakNotices.entrySet()) {
            noticeCopy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return new PersistenceSnapshot(bedCopy, noticeCopy, new HashSet<>(migratedOwners));
    }

    private boolean writeSnapshot(PersistenceSnapshot snapshot) {
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

        List<String> migrated = snapshot.migratedOwners().stream()
                .map(UUID::toString)
                .sorted()
                .toList();
        config.set("migrated", migrated);

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
            config.save(file);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save beds.yml: " + exception.getMessage());
            return false;
        }
    }

    public enum RenameResult {
        SUCCESS,
        NOT_FOUND,
        INVALID_NAME,
        DUPLICATE
    }

    private record BedBlockKey(String worldName, int x, int y, int z) {
    }

    private record BedBreakNotice(String name, long brokenAt) {
    }

    private record BrokenBed(UUID owner, String name) {
    }

    private record PersistenceSnapshot(
            Map<UUID, Map<String, BedHome>> beds,
            Map<UUID, List<BedBreakNotice>> notices,
            Set<UUID> migratedOwners
    ) {
    }
}
