package org.enthusia.teleport.combat;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.enthusia.teleport.EnthusiaTeleportPlugin;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatTagManager implements Listener {

    private final EnthusiaTeleportPlugin plugin;
    private final CombatLogXHook combatLogXHook;
    private final NPPBridge nppBridge;
    private final Map<UUID, Long> combatUntil = new ConcurrentHashMap<>();
    private final Set<EntityDamageByEntityEvent> nppCancelledDamage =
            Collections.newSetFromMap(new IdentityHashMap<>());

    // Crystal entity UUID → player UUID who last punched/owns it
    private final Map<UUID, UUID> crystalOwners = new HashMap<>();
    private boolean enabled;
    private long tagMillis;

    public CombatTagManager(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
        this.combatLogXHook = new CombatLogXHook(plugin);
        this.nppBridge = new NPPBridge();
        reload();
    }

    public void reload() {
        this.enabled = plugin.getPluginConfigManager().current().combat().enabled();
        int seconds = plugin.getPluginConfigManager().current().combat().tagSeconds();
        this.tagMillis = seconds * 1000L;
        this.combatLogXHook.tryHook();
        this.nppBridge.tryHook();
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    public boolean isInCombat(Player player) {
        if (combatLogXHook.isInCombat(player)) {
            return true;
        }
        if (!enabled) return false;
        Long until = combatUntil.get(player.getUniqueId());
        if (until == null) return false;
        return System.currentTimeMillis() < until;
    }

    private void tag(Player player) {
        if (!enabled || player == null) return;
        combatUntil.put(player.getUniqueId(), System.currentTimeMillis() + tagMillis);
    }

    // ─── Damager resolution ──────────────────────────────────────────────────

    /**
     * Resolves the "real" player responsible for damage.
     * Handles: direct melee, projectiles, TNT, and end crystals.
     */
    private Player getPlayerDamager(Entity damager) {
        // Direct melee
        if (damager instanceof Player p) {
            return p;
        }

        // Projectiles shot by a player (ignore snowballs & eggs)
        if (damager instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player shooter) {
                if (projectile instanceof Snowball || projectile instanceof Egg) {
                    return null;
                }
                return shooter;
            }
        }

        // TNT owned by a player
        if (damager instanceof TNTPrimed tnt) {
            if (tnt.getSource() instanceof Player src) {
                return src;
            }
        }

        // End crystal → look up who last punched/placed it
        if (damager instanceof EnderCrystal crystal) {
            UUID ownerUuid = crystalOwners.get(crystal.getUniqueId());
            if (ownerUuid != null) {
                return Bukkit.getPlayer(ownerUuid);
            }
        }

        return null;
    }

    // ─── Crystal ownership tracking ──────────────────────────────────────────

    /**
     * When a player punches an end crystal, they "own" it for explosion attribution.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onCrystalPunch(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) return;
        if (!(event.getDamager() instanceof Player player)) return;

        crystalOwners.put(crystal.getUniqueId(), player.getUniqueId());
    }

    /**
     * When a player places an end crystal on obsidian/bedrock.
     * The crystal entity spawns after the interact event — schedule a one-tick search.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onCrystalPlace(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        if (event.getItem() == null || event.getItem().getType() != Material.END_CRYSTAL) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        Material type = clicked.getType();
        if (type != Material.OBSIDIAN && type != Material.BEDROCK) return;

        Player player = event.getPlayer();
        Location loc = clicked.getLocation().add(0.5, 1, 0.5);

        Bukkit.getScheduler().runTask(plugin, () -> {
            World world = loc.getWorld();
            if (world == null) return;
            for (Entity entity : world.getNearbyEntities(loc, 0.5, 0.5, 0.5)) {
                if (entity instanceof EnderCrystal crystal) {
                    crystalOwners.put(crystal.getUniqueId(), player.getUniqueId());
                    break;
                }
            }
        });
    }

    /**
     * Clean up crystal ownership entry after the entity is removed (exploded).
     * Damage events fire synchronously in the same tick, so delay removal.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCrystalRemove(EntityRemoveFromWorldEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) return;
        UUID crystalId = crystal.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> crystalOwners.remove(crystalId), 20L); // 1 second
    }

    // ─── NPP protection cancellation (HIGHEST priority) ──────────────────────

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onNppCancelEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = getPlayerDamager(event.getDamager());
        if (attacker == null || event.getFinalDamage() <= 0) return;
        if (nppBridge.hasBypass(attacker)) return;

        removeProtectionForAttack(attacker, victim);
        if (nppBridge.isProtected(victim)) {
            event.setCancelled(true);
            nppCancelledDamage.add(event);
        }
    }

    private void removeProtectionForAttack(Player attacker, Player victim) {
        if (!attacker.equals(victim) && !nppBridge.hasBypass(attacker) && nppBridge.isProtected(attacker)) {
            nppBridge.removeProtection(attacker);
        }
    }

    // ─── Combat tagging (MONITOR priority, observe-only) ─────────────────────

    /**
     * Entity vs entity damage: combat tagging and NPP attacker strip.
     * Runs at MONITOR with no ignoreCancelled so it still fires after
     * the HIGHEST NPP handler cancels the event — attacker tagging must
     * happen regardless.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = getPlayerDamager(event.getDamager());
        boolean cancelledByNppBridge = nppCancelledDamage.remove(event);
        if (event.isCancelled()) {
            if (!cancelledByNppBridge) return;
            // Only bridge-cancelled damage tags the attacker.
            if (attacker != null && !attacker.equals(victim)) {
                if (!combatLogXHook.isHooked()) {
                    tag(attacker);
                }
            }
            return;
        }

        if (event.getFinalDamage() <= 0) return;
        if (attacker == null) return;

        // NPP: attacker is protected → strip their protection
        if (!combatLogXHook.isHooked()) {
            tag(victim);
            tag(attacker);
        }
    }

    // ─── Cleanup ─────────────────────────────────────────────────────────────

    @EventHandler
    public void onPearlLaunch(ProjectileLaunchEvent event) {
        if (!enabled) return;

        if (!(event.getEntity() instanceof EnderPearl pearl)) return;
        if (!(pearl.getShooter() instanceof Player player)) return;

        if (!isInCombat(player)) {
            return;
        }

        tag(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        combatUntil.remove(player.getUniqueId());

        crystalOwners.values().removeIf(uuid -> uuid.equals(player.getUniqueId()));
    }
}
