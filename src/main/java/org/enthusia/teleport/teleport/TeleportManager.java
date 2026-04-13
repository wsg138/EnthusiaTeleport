package org.enthusia.teleport.teleport;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.Sound;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.api.TeleportApi;
import org.enthusia.teleport.back.BackManager;
import org.enthusia.teleport.combat.CombatTagManager;
import org.enthusia.teleport.util.Messages;

import java.util.*;

public class TeleportManager implements TeleportApi, Listener {
    private static final String BYPASS_WORLD_BLOCK_PERMISSION = "enthusia.teleport.bypass-world-block";

    public enum CancelReason {
        MOVE,
        DAMAGE
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

        public static TeleportFlags noCooldown() {
            return new TeleportFlags(true, false, true);
        }

        public static TeleportFlags instant() {
            return new TeleportFlags(true, true, true);
        }

        public static TeleportFlags back() {
            return new TeleportFlags(true, true, false);
        }
    }

    private static class ActiveTeleport {
        final UUID playerId;
        final Location fromBlock;
        final BukkitTask task;
        final Player anchor;       // player being teleported to (can be null)
        final Runnable onSuccess;  // called after a successful teleport

        ActiveTeleport(UUID playerId, Location fromBlock, BukkitTask task,
                       Player anchor, Runnable onSuccess) {
            this.playerId = playerId;
            this.fromBlock = fromBlock;
            this.task = task;
            this.anchor = anchor;
            this.onSuccess = onSuccess;
        }
    }

    private final EnthusiaTeleportPlugin plugin;
    private final Messages messages;
    private final SafeLocationFinder safeFinder;
    private final BackManager backManager;

    private double baseWarmupSeconds;
    private int baseCooldownSeconds;

    private final Map<UUID, Double> warmupModifiers = new HashMap<>();
    private final Map<UUID, Double> cooldownModifiers = new HashMap<>();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();
    private final Map<UUID, ActiveTeleport> activeTeleports = new HashMap<>();

    public TeleportManager(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
        this.messages = plugin.getMessages();
        reloadSettings();
        this.safeFinder = new SafeLocationFinder(
                plugin.getConfig().getInt("teleport.safe-search-radius", 4)
        );
        this.backManager = plugin.getBackManager();
    }

    public void reloadSettings() {
        this.baseWarmupSeconds = plugin.getConfig().getDouble("teleport.warmup-seconds", 5.0);
        this.baseCooldownSeconds = plugin.getConfig().getInt("teleport.cooldown-seconds", 60);
    }

    public void shutdown() {
        for (ActiveTeleport at : activeTeleports.values()) {
            at.task.cancel();
        }
        activeTeleports.clear();
    }

    // -------- TeleportApi impl ---------

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
        if (modifier <= 0) modifier = 0.01;
        warmupModifiers.put(playerId, modifier);
    }

    @Override
    public void setCooldownModifier(UUID playerId, double modifier) {
        if (modifier <= 0) modifier = 0.01;
        cooldownModifiers.put(playerId, modifier);
    }

    @Override
    public double getEffectiveWarmupSeconds(UUID playerId) {
        double mod = warmupModifiers.getOrDefault(playerId, 1.0);
        return baseWarmupSeconds * mod;
    }

    @Override
    public int getEffectiveCooldownSeconds(UUID playerId) {
        double mod = cooldownModifiers.getOrDefault(playerId, 1.0);
        return (int) Math.round(baseCooldownSeconds * mod);
    }

    // ------- Cooldown ----------

    public boolean isOnCooldown(Player player) {
        Long until = cooldownUntil.get(player.getUniqueId());
        if (until == null) return false;
        return System.currentTimeMillis() < until;
    }

    public long getCooldownRemaining(Player player) {
        Long until = cooldownUntil.get(player.getUniqueId());
        if (until == null) return 0;
        long diff = until - System.currentTimeMillis();
        return Math.max(0, diff / 1000L);
    }

    public boolean checkAndNotifyCooldown(Player player) {
        if (hasBypassTeleport(player)) return false;
        if (!isOnCooldown(player)) return false;
        long remaining = getCooldownRemaining(player);
        messages.send(player, "teleport.cooldown-active",
                Map.of("seconds", Long.toString(remaining)));
        return true;
    }

    private void applyCooldown(Player player) {
        int seconds = getEffectiveCooldownSeconds(player.getUniqueId());
        if (seconds <= 0) return;
        long until = System.currentTimeMillis() + seconds * 1000L;
        cooldownUntil.put(player.getUniqueId(), until);
    }

    // ------- Teleport entrypoints ---------

    // Old signature – used by most commands
    public void startTeleport(Player player,
                              Location target,
                              boolean useSafeSearch,
                              Player anchor,
                              String warmupKey) {
        startTeleport(player, target, useSafeSearch, anchor, warmupKey, null, TeleportFlags.standard());
    }

    // New signature – allows a callback (used by /rtp)
    public void startTeleport(Player player,
                              Location target,
                              boolean useSafeSearch,
                              Player anchor,
                              String warmupKey,
                              Runnable onSuccess) {
        startTeleport(player, target, useSafeSearch, anchor, warmupKey, onSuccess, TeleportFlags.standard());
    }

    // New signature – allows flags to bypass warmup/cooldown and/or disable /back history.
    public void startTeleport(Player player,
                              Location target,
                              boolean useSafeSearch,
                              Player anchor,
                              String warmupKey,
                              Runnable onSuccess,
                              TeleportFlags flags) {

        UUID id = player.getUniqueId();

        // Combat check (blocks all teleports while in combat, unless bypass)
        CombatTagManager combat = plugin.getCombatManager();
        if (combat != null
                && combat.isInCombat(player)
                && !player.hasPermission("enthusia.teleport.bypass-combat")) {
            messages.send(player, "teleport.combat-blocked");
            return;
        }

        boolean bypassAll = hasBypassTeleport(player);
        boolean bypassCooldown = bypassAll || flags.bypassCooldown;
        boolean bypassWarmup = bypassAll || flags.bypassWarmup;
        boolean recordBack = flags.recordBack;

        if (!bypassCooldown && checkAndNotifyCooldown(player)) {
            return;
        }

        double warmup = bypassWarmup ? 0.0 : getEffectiveWarmupSeconds(id);
        boolean applyCooldown = !bypassCooldown;
        if (warmup <= 0.05) {
            boolean success = doTeleport(player, target, useSafeSearch, anchor, applyCooldown, recordBack);
            if (success && onSuccess != null) {
                onSuccess.run();
            }
            return;
        }

        // Cancel any existing teleport
        cancelTeleport(id, null);

        messages.send(player, warmupKey,
                Map.of("seconds", String.format("%.1f", warmup)));

        Location fromBlock = player.getLocation().getBlock().getLocation();

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                ActiveTeleport at = activeTeleports.remove(id);
                boolean success = doTeleport(player, target, useSafeSearch, anchor, applyCooldown, recordBack);
                if (success && at != null && at.onSuccess != null) {
                    at.onSuccess.run();
                }
            }
        }.runTaskLater(plugin, (long) Math.ceil(20 * warmup));

        activeTeleports.put(id, new ActiveTeleport(id, fromBlock, task, anchor, onSuccess));
    }

    /**
     * Perform the actual teleport. Returns true if teleport succeeded.
     * Also enforces "no teleport into blocked worlds".
     */
    private boolean doTeleport(Player player,
                               Location target,
                               boolean useSafeSearch,
                               Player anchor,
                               boolean applyCooldown,
                               boolean recordBack) {

        if (!player.isOnline()) return false;
        if (target == null || target.getWorld() == null) {
            messages.send(player, "teleport.safe-fallback-failed");
            return false;
        }

        Location from = player.getLocation().clone();
        Location dest = target.clone();

        if (useSafeSearch) {
            Location safe = safeFinder.findSafeTeleportLocation(target);
            if (safe == null) {
                messages.send(player, "teleport.safe-fallback-failed");
                return false;
            }
            dest = safe;
            if (anchor != null && anchor.isOnline() && !sameBlock(anchor.getLocation(), target)) {
                plugin.getMessages().send(anchor, "teleport.anchor-unsafe",
                        Map.of("teleporter", player.getName()));
            }
        }

        // Block teleports into certain worlds (e.g. "surfevents")
        World destWorld = dest.getWorld();
        List<String> blocked = plugin.getConfig().getStringList("teleport.blocked-target-worlds");
        if (destWorld != null && blocked != null && !player.hasPermission(BYPASS_WORLD_BLOCK_PERMISSION)) {
            String name = destWorld.getName();
            for (String w : blocked) {
                if (name.equalsIgnoreCase(w)) {
                    messages.send(player, "teleport.world-blocked",
                            Map.of("world", name));
                    return false;
                }
            }
        }

        List<Entity> passengers = new ArrayList<>(player.getPassengers());
        for (Entity passenger : passengers) {
            player.removePassenger(passenger);
        }

        boolean success = player.teleport(dest);
        if (!success) {
            messages.send(player, "teleport.failed");
            return false;
        }

        if (!passengers.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Entity passenger : passengers) {
                    if (passenger == null || passenger.isDead()) continue;
                    player.addPassenger(passenger);
                }
            });
        }

        messages.send(player, "teleport.teleported");
        if (anchor != null && anchor.isOnline()) {
            messages.send(player, "teleport.teleported-to-player",
                    Map.of("target", anchor.getName()));
            if (!isHidden(player)) {
                messages.send(anchor, "teleport.teleported-from-player",
                        Map.of("teleporter", player.getName()));
            }
            Sound sound = Sound.BLOCK_NOTE_BLOCK_PLING;
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            if (!isHidden(player)) {
                anchor.playSound(anchor.getLocation(), sound, 1.0f, 1.0f);
            }
            plugin.getLogger().info(player.getName() + " teleported to " + anchor.getName() + ".");
        }
        if (recordBack && backManager != null && !sameBlock(from, dest)) {
            backManager.record(player, from);
        }
        if (applyCooldown) {
            applyCooldown(player);
        }
        return true;
    }

    private boolean sameBlock(Location a, Location b) {
        if (a == null || b == null) return false;
        if (!Objects.equals(a.getWorld(), b.getWorld())) return false;
        return a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    public void cancelTeleport(UUID playerId, CancelReason reason) {
        ActiveTeleport at = activeTeleports.remove(playerId);
        if (at == null) return;
        at.task.cancel();
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return;

        if (reason == null) return;

        switch (reason) {
            case MOVE -> {
                messages.send(player, "teleport.warmup-cancelled-move");
                if (at.anchor != null && !at.anchor.getUniqueId().equals(playerId) && at.anchor.isOnline()) {
                    messages.send(at.anchor, "teleport.warmup-cancelled-move-other",
                            Map.of("player", player.getName()));
                }
            }
            case DAMAGE -> {
                messages.send(player, "teleport.warmup-cancelled-damage");
                if (at.anchor != null && !at.anchor.getUniqueId().equals(playerId) && at.anchor.isOnline()) {
                    messages.send(at.anchor, "teleport.warmup-cancelled-damage-other",
                            Map.of("player", player.getName()));
                }
            }
        }
    }

    // -------- listeners --------

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        ActiveTeleport at = activeTeleports.get(event.getPlayer().getUniqueId());
        if (at == null) return;

        if (movedBlock(event.getFrom(), event.getTo())) {
            cancelTeleport(event.getPlayer().getUniqueId(), CancelReason.MOVE);
        }
    }

    private boolean movedBlock(Location from, Location to) {
        if (to == null) return false;
        return from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getFinalDamage() <= 0) return;
        if (!activeTeleports.containsKey(player.getUniqueId())) return;
        cancelTeleport(player.getUniqueId(), CancelReason.DAMAGE);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        ActiveTeleport at = activeTeleports.remove(id);
        if (at != null) {
            at.task.cancel();
        }
    }

    public SafeLocationFinder getSafeFinder() {
        return safeFinder;
    }

    private boolean hasBypassTeleport(Player player) {
        return player != null && player.hasPermission("enthusia.teleport.bypass-teleport");
    }

    private boolean isHidden(Player player) {
        if (player == null) return false;
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return true;
        if (hasTrueMetadata(player, "vanished")) return true;
        if (hasTrueMetadata(player, "vanish")) return true;
        return false;
    }

    private boolean hasTrueMetadata(Player player, String key) {
        if (!player.hasMetadata(key)) return false;
        for (MetadataValue value : player.getMetadata(key)) {
            if (value == null) continue;
            if (value.asBoolean()) return true;
            String raw = value.asString();
            if (raw != null && raw.equalsIgnoreCase("true")) return true;
        }
        return false;
    }
}
