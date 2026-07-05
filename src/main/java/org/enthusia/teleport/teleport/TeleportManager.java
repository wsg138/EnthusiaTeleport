package org.enthusia.teleport.teleport;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.api.TeleportApi;
import org.enthusia.teleport.back.BackManager;
import org.enthusia.teleport.combat.CombatTagManager;
import org.enthusia.teleport.config.PluginConfig;
import org.enthusia.teleport.util.Messages;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class TeleportManager implements TeleportApi, Listener {

    private static final String BYPASS_COMBAT_PERMISSION = "enthusia.teleport.bypass-combat";
    private static final String BYPASS_TELEPORT_PERMISSION = "enthusia.teleport.bypass-teleport";
    private static final String BYPASS_WORLD_BLOCK_PERMISSION = "enthusia.teleport.bypass-world-block";
    private static final double INSTANT_WARMUP_THRESHOLD_SECONDS = 0.05D;
    private static final double TICKS_PER_SECOND = 20.0D;
    private static final long MILLIS_PER_SECOND = 1000L;

    public enum CancelReason {
        MOVE,
        DAMAGE,
        DISCONNECT,
        RELOAD,
        DISABLE
    }

    public static class TeleportFlags {
        final boolean bypassCooldown;
        final boolean bypassWarmup;
        final boolean recordBack;

        private TeleportFlags(boolean bypassCooldown, boolean bypassWarmup, boolean recordBack) {
            this.bypassCooldown = bypassCooldown;
            this.bypassWarmup = bypassWarmup;
            this.recordBack = recordBack;
        }

        public static TeleportFlags standard() {
            return new TeleportFlags(false, false, true);
        }

        public static TeleportFlags instant() {
            return new TeleportFlags(true, true, true);
        }

        public static TeleportFlags back() {
            return new TeleportFlags(true, true, false);
        }
    }

    private static class ActiveTeleport {
        private final UUID playerId;
        private final Location origin;
        private final BukkitTask task;
        private final Player anchor;
        private final Runnable onSuccess;

        private ActiveTeleport(UUID playerId, Location origin, BukkitTask task, Player anchor, Runnable onSuccess) {
            this.playerId = playerId;
            this.origin = origin;
            this.task = task;
            this.anchor = anchor;
            this.onSuccess = onSuccess;
        }
    }

    private final EnthusiaTeleportPlugin plugin;
    private final Messages messages;
    private final BackManager backManager;
    private final Map<UUID, Double> warmupModifiers = new ConcurrentHashMap<>();
    private final Map<UUID, Double> cooldownModifiers = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldownUntil = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveTeleport> activeTeleports = new ConcurrentHashMap<>();

    private SafeLocationFinder safeFinder;
    private double baseWarmupSeconds;
    private double movementCancelDistanceSquared;
    private int baseCooldownSeconds;
    private Set<String> blockedTargetWorlds;

    public TeleportManager(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
        this.messages = plugin.getMessages();
        this.backManager = plugin.getBackManager();
        reloadSettings();
    }

    public void reloadSettings() {
        PluginConfig.TeleportSettings settings = plugin.getPluginConfigManager().current().teleport();
        this.baseWarmupSeconds = settings.warmupSeconds();
        this.movementCancelDistanceSquared = settings.movementCancelDistance() * settings.movementCancelDistance();
        this.baseCooldownSeconds = settings.cooldownSeconds();
        this.safeFinder = new SafeLocationFinder(settings.safeSearchRadius());
        this.blockedTargetWorlds = settings.blockedTargetWorlds();
        resetCooldowns();
    }

    public void shutdown() {
        cancelAll(CancelReason.DISABLE);
        warmupModifiers.clear();
        cooldownModifiers.clear();
        cooldownUntil.clear();
    }

    public void cancelAll(CancelReason reason) {
        List<UUID> players = new ArrayList<>(activeTeleports.keySet());
        for (UUID playerId : players) {
            cancelTeleport(playerId, reason);
        }
    }

    public void resetCooldowns() {
        cooldownUntil.clear();
    }

    @Override
    public double getBaseWarmupSeconds() {
        return baseWarmupSeconds;
    }

    @Override
    public int getBaseCooldownSeconds() {
        return baseCooldownSeconds;
    }

    @Override
    public void setWarmupModifier(UUID playerId, double modifier) {
        warmupModifiers.put(playerId, modifier <= 0.0D ? 0.01D : modifier);
    }

    @Override
    public void setCooldownModifier(UUID playerId, double modifier) {
        cooldownModifiers.put(playerId, modifier <= 0.0D ? 0.01D : modifier);
    }

    @Override
    public double getEffectiveWarmupSeconds(UUID playerId) {
        return baseWarmupSeconds * warmupModifiers.getOrDefault(playerId, 1.0D);
    }

    @Override
    public int getEffectiveCooldownSeconds(UUID playerId) {
        return (int) Math.round(baseCooldownSeconds * cooldownModifiers.getOrDefault(playerId, 1.0D));
    }

    public SafeLocationFinder getSafeFinder() {
        return safeFinder;
    }

    public boolean isOnCooldown(Player player) {
        Long until = cooldownUntil.get(player.getUniqueId());
        return until != null && System.currentTimeMillis() < until;
    }

    public long getCooldownRemaining(Player player) {
        Long until = cooldownUntil.get(player.getUniqueId());
        if (until == null) {
            return 0L;
        }
        return Math.max(0L, (until - System.currentTimeMillis()) / 1000L);
    }

    public boolean checkAndNotifyCooldown(Player player) {
        if (hasBypassTeleport(player) || !isOnCooldown(player)) {
            return false;
        }
        messages.send(player, "teleport.cooldown-active", Map.of("seconds", Long.toString(getCooldownRemaining(player))));
        return true;
    }

    public void startTeleport(Player player, Location target, boolean useSafeSearch, Player anchor, String warmupKey) {
        startTeleport(player, target, useSafeSearch, anchor, warmupKey, null, TeleportFlags.standard());
    }

    public void startTeleport(Player player, Location target, boolean useSafeSearch, Player anchor, String warmupKey, Runnable onSuccess) {
        startTeleport(player, target, useSafeSearch, anchor, warmupKey, onSuccess, TeleportFlags.standard());
    }

    public void startTeleport(Player player,
                              Location target,
                              boolean useSafeSearch,
                              Player anchor,
                              String warmupKey,
                              Runnable onSuccess,
                              TeleportFlags flags) {
        startTeleport(player, () -> target, useSafeSearch, anchor, warmupKey, onSuccess, flags);
    }

    public void startTeleportToLivePlayer(Player player,
                                          Player anchor,
                                          boolean useSafeSearch,
                                          String warmupKey,
                                          Runnable onSuccess,
                                          TeleportFlags flags) {
        startTeleport(
                player,
                () -> anchor != null && anchor.isOnline() ? anchor.getLocation() : null,
                useSafeSearch,
                anchor,
                warmupKey,
                onSuccess,
                flags
        );
    }

    private void startTeleport(Player player,
                               Supplier<Location> targetSupplier,
                               boolean useSafeSearch,
                               Player anchor,
                               String warmupKey,
                               Runnable onSuccess,
                               TeleportFlags flags) {
        if (rejectTeleportStart(player, flags)) {
            return;
        }

        boolean bypassAll = hasBypassTeleport(player);
        boolean bypassCooldown = bypassAll || flags.bypassCooldown;
        boolean bypassWarmup = bypassAll || flags.bypassWarmup;
        double warmup = bypassWarmup ? 0.0D : getEffectiveWarmupSeconds(player.getUniqueId());
        if (warmup <= INSTANT_WARMUP_THRESHOLD_SECONDS) {
            completeTeleport(player, targetSupplier.get(), useSafeSearch, anchor, !bypassCooldown, flags.recordBack, onSuccess);
            return;
        }

        scheduleWarmup(player, targetSupplier, useSafeSearch, anchor, warmupKey, onSuccess, flags, bypassCooldown, warmup);
    }

    private boolean rejectTeleportStart(Player player, TeleportFlags flags) {
        if (player == null) {
            return true;
        }
        CombatTagManager combat = plugin.getCombatManager();
        if (combat != null && combat.isInCombat(player) && !player.hasPermission(BYPASS_COMBAT_PERMISSION)) {
            messages.send(player, "teleport.combat-blocked");
            return true;
        }
        boolean bypassAll = hasBypassTeleport(player);
        boolean bypassCooldown = bypassAll || flags.bypassCooldown;
        return !bypassCooldown && checkAndNotifyCooldown(player);
    }

    private void scheduleWarmup(Player player,
                                Supplier<Location> targetSupplier,
                                boolean useSafeSearch,
                                Player anchor,
                                String warmupKey,
                                Runnable onSuccess,
                                TeleportFlags flags,
                                boolean bypassCooldown,
                                double warmup) {
        cancelTeleport(player.getUniqueId(), null);
        messages.send(player, warmupKey, Map.of("seconds", String.format(java.util.Locale.US, "%.1f", warmup)));

        Location origin = player.getLocation().clone();
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                ActiveTeleport active = activeTeleports.remove(player.getUniqueId());
                Location liveTarget = targetSupplier.get();
                Runnable callback = active == null ? null : active.onSuccess;
                completeTeleport(player, liveTarget, useSafeSearch, anchor, !bypassCooldown, flags.recordBack, callback);
            }
        }.runTaskLater(plugin, (long) Math.ceil(warmup * TICKS_PER_SECOND));

        activeTeleports.put(player.getUniqueId(), new ActiveTeleport(player.getUniqueId(), origin, task, anchor, onSuccess));
    }

    private void completeTeleport(Player player,
                                  Location target,
                                  boolean useSafeSearch,
                                  Player anchor,
                                  boolean applyCooldown,
                                  boolean recordBack,
                                  Runnable onSuccess) {
        if (doTeleport(player, target, useSafeSearch, anchor, applyCooldown, recordBack) && onSuccess != null) {
            onSuccess.run();
        }
    }

    public void cancelTeleport(UUID playerId, CancelReason reason) {
        ActiveTeleport active = activeTeleports.remove(playerId);
        if (active == null) {
            return;
        }

        active.task.cancel();
        if (reason != null) {
            plugin.getPerformanceMonitor().increment("teleport.warmup_cancelled." + reason.name().toLowerCase(java.util.Locale.ROOT));
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || reason == null) {
            return;
        }

        switch (reason) {
            case MOVE -> {
                messages.send(player, "teleport.warmup-cancelled-move");
                notifyAnchor(active, "teleport.warmup-cancelled-move-other", player);
            }
            case DAMAGE -> {
                messages.send(player, "teleport.warmup-cancelled-damage");
                notifyAnchor(active, "teleport.warmup-cancelled-damage-other", player);
            }
            case DISCONNECT -> {
                messages.send(player, "teleport.warmup-cancelled-disconnect");
                notifyAnchor(active, "teleport.warmup-cancelled-disconnect-other", player);
            }
            case RELOAD, DISABLE -> {
                messages.send(player, "teleport.cancelled-reload");
                notifyAnchor(active, "teleport.cancelled-reload-other", player);
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        ActiveTeleport active = activeTeleports.get(event.getPlayer().getUniqueId());
        if (active == null || event.getTo() == null) {
            return;
        }

        if (movedTooFar(active.origin, event.getTo())) {
            cancelTeleport(event.getPlayer().getUniqueId(), CancelReason.MOVE);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getFinalDamage() <= 0.0D) {
            return;
        }
        if (activeTeleports.containsKey(player.getUniqueId())) {
            cancelTeleport(player.getUniqueId(), CancelReason.DAMAGE);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID quittingId = event.getPlayer().getUniqueId();
        cancelTeleport(quittingId, null);
        cooldownUntil.remove(quittingId);

        List<UUID> anchoredTeleports = new ArrayList<>();
        for (Map.Entry<UUID, ActiveTeleport> entry : activeTeleports.entrySet()) {
            ActiveTeleport active = entry.getValue();
            if (active.anchor != null && Objects.equals(active.anchor.getUniqueId(), quittingId)) {
                anchoredTeleports.add(entry.getKey());
            }
        }
        for (UUID teleporterId : anchoredTeleports) {
            cancelTeleport(teleporterId, CancelReason.DISCONNECT);
        }
    }

    private boolean doTeleport(Player player, Location target, boolean useSafeSearch, Player anchor, boolean applyCooldown, boolean recordBack) {
        if (!player.isOnline()) {
            return false;
        }

        Location from = player.getLocation().clone();
        Location destination = resolveDestination(player, target, useSafeSearch, anchor);
        if (destination == null || rejectBlockedWorld(player, destination)) {
            return false;
        }

        List<Entity> passengers = new ArrayList<>(player.getPassengers());
        for (Entity passenger : passengers) {
            player.removePassenger(passenger);
        }

        boolean success = player.teleport(destination);
        if (!success) {
            messages.send(player, "teleport.failed");
            return false;
        }

        restorePassengers(player, passengers);

        messages.send(player, "teleport.teleported");
        notifyTeleportComplete(player, anchor);
        recordBackIfNeeded(player, from, destination, recordBack);
        applyCooldownIfNeeded(player, applyCooldown);
        return true;
    }

    private Location resolveDestination(Player player, Location target, boolean useSafeSearch, Player anchor) {
        if (target == null || target.getWorld() == null) {
            messages.send(player, "teleport.safe-fallback-failed");
            return null;
        }
        if (!useSafeSearch) {
            return target.clone();
        }
        Location safe = safeFinder.findSafeTeleportLocation(target);
        if (safe == null) {
            messages.send(player, "teleport.safe-fallback-failed");
            return null;
        }
        if (anchor != null && anchor.isOnline() && !sameBlock(anchor.getLocation(), target)) {
            messages.send(anchor, "teleport.anchor-unsafe", Map.of("teleporter", player.getName()));
        }
        return safe;
    }

    private boolean rejectBlockedWorld(Player player, Location destination) {
        World world = destination.getWorld();
        if (world == null || !blockedTargetWorlds.contains(world.getName().toLowerCase(java.util.Locale.ROOT))) {
            return false;
        }
        if (player.hasPermission(BYPASS_WORLD_BLOCK_PERMISSION)) {
            return false;
        }
        messages.send(player, "teleport.world-blocked", Map.of("world", world.getName()));
        return true;
    }

    private void restorePassengers(Player player, List<Entity> passengers) {
        if (passengers.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Entity passenger : passengers) {
                if (passenger != null && !passenger.isDead()) {
                    player.addPassenger(passenger);
                }
            }
        });
    }

    private void notifyTeleportComplete(Player player, Player anchor) {
        if (anchor == null || !anchor.isOnline()) {
            return;
        }
        messages.send(player, "teleport.teleported-to-player", Map.of("target", anchor.getName()));
        boolean hidden = isHidden(player);
        if (!hidden) {
            messages.send(anchor, "teleport.teleported-from-player", Map.of("teleporter", player.getName()));
        }
        playTeleportSound(player);
        if (!hidden) {
            playTeleportSound(anchor);
        }
    }

    private void recordBackIfNeeded(Player player, Location from, Location destination, boolean recordBack) {
        if (recordBack && backManager != null && !sameBlock(from, destination)) {
            backManager.record(player, from);
        }
    }

    private void applyCooldownIfNeeded(Player player, boolean applyCooldown) {
        if (applyCooldown) {
            applyCooldown(player);
        }
    }

    private void applyCooldown(Player player) {
        int seconds = getEffectiveCooldownSeconds(player.getUniqueId());
        if (seconds > 0) {
            cooldownUntil.put(player.getUniqueId(), System.currentTimeMillis() + seconds * MILLIS_PER_SECOND);
        }
    }

    private void notifyAnchor(ActiveTeleport active, String key, Player player) {
        if (active.anchor == null || !active.anchor.isOnline() || Objects.equals(active.anchor.getUniqueId(), active.playerId)) {
            return;
        }
        messages.send(active.anchor, key, Map.of("player", player.getName()));
    }

    private boolean movedTooFar(Location origin, Location to) {
        if (origin == null || to == null || !Objects.equals(origin.getWorld(), to.getWorld())) {
            return true;
        }
        return origin.distanceSquared(to) > movementCancelDistanceSquared;
    }

    private boolean sameBlock(Location first, Location second) {
        if (first == null || second == null || !Objects.equals(first.getWorld(), second.getWorld())) {
            return false;
        }
        return first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }

    private boolean hasBypassTeleport(Player player) {
        return player.hasPermission(BYPASS_TELEPORT_PERMISSION);
    }

    private boolean isHidden(Player player) {
        return player.getGameMode() == GameMode.SPECTATOR || hasTrueMetadata(player, "vanish") || hasTrueMetadata(player, "vanished");
    }

    private boolean hasTrueMetadata(Player player, String key) {
        if (!player.hasMetadata(key)) {
            return false;
        }
        for (MetadataValue value : player.getMetadata(key)) {
            if (value != null && (value.asBoolean() || "true".equalsIgnoreCase(value.asString()))) {
                return true;
            }
        }
        return false;
    }

    private void playTeleportSound(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 1.0F);
    }
}
