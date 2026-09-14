package org.enthusia.teleport.combat;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.api.CancelReason;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Combat integration for EnthusiaTeleport.
 *
 * CombatLogX is the sole authority for combat state. This class intentionally does
 * not keep its own timer or extend combat for pearls; CombatLogX owns those rules.
 * The listener code that remains here is NewPlayerProtection compatibility plus
 * the request/warmup policy reaction to CombatLogX's own PlayerTagEvent.
 */
public class CombatTagManager implements Listener {

    private static final String BYPASS_COMBAT_PERMISSION = "enthusia.teleport.bypass-combat";
    private static final String COMBATLOGX_TAG_EVENT =
            "com.github.sirblobman.combatlogx.api.event.PlayerTagEvent";

    private final EnthusiaTeleportPlugin plugin;
    private final CombatLogXHook combatLogXHook;
    private final NPPBridge nppBridge;
    private boolean combatTagListenerRegistered;

    // Crystal entity UUID -> player UUID who last punched/owns it. Used only so
    // NewPlayerProtection can attribute crystal damage to the attacking player.
    private final Map<UUID, UUID> crystalOwners = new HashMap<>();

    public CombatTagManager(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
        this.combatLogXHook = new CombatLogXHook(plugin);
        this.nppBridge = new NPPBridge();
        registerCombatLogXTagListener();
    }

    public void reload() {
        this.combatLogXHook.tryHook();
        this.nppBridge.tryHook();
        registerCombatLogXTagListener();
    }

    /**
     * Returns CombatLogX's authoritative combat state.
     */
    public boolean isInCombat(Player player) {
        return combatLogXHook.isInCombat(player);
    }

    /**
     * Listen directly to CombatLogX's PlayerTagEvent without maintaining our own
     * combat timer. Reflection keeps the integration version-tolerant while the
     * hard plugin dependency guarantees CombatLogX loads first.
     */
    private void registerCombatLogXTagListener() {
        if (combatTagListenerRegistered) {
            return;
        }

        Plugin combatLogX = Bukkit.getPluginManager().getPlugin("CombatLogX");
        if (combatLogX == null || !combatLogX.isEnabled()) {
            plugin.getLogger().severe("[EnthusiaTeleport] Cannot register CombatLogX PlayerTagEvent listener because CombatLogX is not enabled.");
            return;
        }

        try {
            Class<?> rawEventClass = Class.forName(
                    COMBATLOGX_TAG_EVENT,
                    false,
                    combatLogX.getClass().getClassLoader()
            );
            if (!Event.class.isAssignableFrom(rawEventClass)) {
                plugin.getLogger().severe("[EnthusiaTeleport] CombatLogX PlayerTagEvent is not a Bukkit event.");
                return;
            }

            Class<? extends Event> eventClass = rawEventClass.asSubclass(Event.class);
            Method getPlayerMethod = rawEventClass.getMethod("getPlayer");

            Bukkit.getPluginManager().registerEvent(
                    eventClass,
                    this,
                    EventPriority.MONITOR,
                    (listener, event) -> handleCombatLogXTag(event, getPlayerMethod),
                    plugin,
                    true
            );
            combatTagListenerRegistered = true;
            plugin.getLogger().info("[EnthusiaTeleport] Listening for CombatLogX combat-entry events.");
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().severe("[EnthusiaTeleport] Failed to register CombatLogX PlayerTagEvent listener: " + ex.getMessage());
        }
    }

    private void handleCombatLogXTag(Event event, Method getPlayerMethod) {
        try {
            Object taggedPlayer = getPlayerMethod.invoke(event);
            if (!(taggedPlayer instanceof Player player)) {
                return;
            }
            if (player.hasPermission(BYPASS_COMBAT_PERMISSION)) {
                return;
            }

            // Pending /tpahere invitations from this player are no longer safe.
            plugin.getRequestManager().cancelOutgoingTpahereForCombat(player);

            // If the tagged player is themselves in a teleport warmup, cancel that
            // warmup so dealing damage cannot be used to escape combat. We do not
            // cancel teleports merely anchored to this player; this preserves the
            // requested behavior where an already-accepted /tpa may still arrive
            // after the accepting anchor enters combat.
            plugin.getTeleportManager().cancelTeleport(player.getUniqueId(), CancelReason.COMBAT);
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().severe("[EnthusiaTeleport] Failed to process CombatLogX PlayerTagEvent: " + ex.getMessage());
        }
    }

    // ─── Damager resolution for NewPlayerProtection ─────────────────────────

    /**
     * Resolves the player responsible for damage so NewPlayerProtection can
     * remove attacker protection and block protected victims consistently.
     */
    private Player getPlayerDamager(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }

        if (damager instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player shooter) {
                if (projectile instanceof Snowball || projectile instanceof Egg) {
                    return null;
                }
                return shooter;
            }
        }

        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player source) {
            return source;
        }

        if (damager instanceof EnderCrystal crystal) {
            UUID ownerUuid = crystalOwners.get(crystal.getUniqueId());
            if (ownerUuid != null) {
                return Bukkit.getPlayer(ownerUuid);
            }
        }

        return null;
    }

    // ─── Crystal ownership tracking for NewPlayerProtection ─────────────────

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onCrystalPunch(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) return;
        if (!(event.getDamager() instanceof Player player)) return;

        crystalOwners.put(crystal.getUniqueId(), player.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onCrystalPlace(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        if (event.getItem() == null || event.getItem().getType() != Material.END_CRYSTAL) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        Material type = clicked.getType();
        if (type != Material.OBSIDIAN && type != Material.BEDROCK) return;

        Player player = event.getPlayer();
        Location location = clicked.getLocation().add(0.5, 1, 0.5);

        Bukkit.getScheduler().runTask(plugin, () -> {
            World world = location.getWorld();
            if (world == null) return;
            for (Entity entity : world.getNearbyEntities(location, 0.5, 0.5, 0.5)) {
                if (entity instanceof EnderCrystal crystal) {
                    crystalOwners.put(crystal.getUniqueId(), player.getUniqueId());
                    break;
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCrystalRemove(EntityRemoveFromWorldEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) return;
        UUID crystalId = crystal.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> crystalOwners.remove(crystalId), 20L);
    }

    // ─── NewPlayerProtection compatibility ──────────────────────────────────

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onNppCancelEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = getPlayerDamager(event.getDamager());
        if (attacker == null || event.getFinalDamage() <= 0) return;
        if (nppBridge.hasBypass(attacker)) return;

        removeProtectionForAttack(attacker, victim);
        if (nppBridge.isProtected(victim)) {
            event.setCancelled(true);
        }
    }

    private void removeProtectionForAttack(Player attacker, Player victim) {
        if (!attacker.equals(victim)
                && !nppBridge.hasBypass(attacker)
                && nppBridge.isProtected(attacker)) {
            nppBridge.removeProtection(attacker);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        crystalOwners.values().removeIf(playerId::equals);
    }
}
