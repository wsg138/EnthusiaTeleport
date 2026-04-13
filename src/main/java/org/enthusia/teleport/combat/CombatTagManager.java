package org.enthusia.teleport.combat;

import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.enthusia.teleport.EnthusiaTeleportPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CombatTagManager implements Listener {

    private final EnthusiaTeleportPlugin plugin;
    private final CombatLogXHook combatLogXHook;
    private final Map<UUID, Long> combatUntil = new HashMap<>();

    private boolean enabled;
    private long tagMillis;

    public CombatTagManager(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
        this.combatLogXHook = new CombatLogXHook(plugin);
        reload();
    }

    public void reload() {
        this.enabled = plugin.getConfig().getBoolean("combat.enabled", true);
        int seconds = plugin.getConfig().getInt("combat.tag-seconds", 30);
        this.tagMillis = seconds * 1000L;
        // Re-hook in case CombatLogX was added/removed or reloaded.
        this.combatLogXHook.tryHook();
    }

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

        return null;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getFinalDamage() <= 0) return;
        if (!enabled || combatLogXHook.isHooked()) {
            // External combat plugins (CombatLogX) handle tagging when hooked.
            return;
        }

        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = getPlayerDamager(event.getDamager());
        if (attacker == null) {
            // Environmental damage or non-player sources → no combat tag
            return;
        }

        // Tag both attacker and victim
        tag(victim);
        tag(attacker);
    }

    /**
     * When a player throws an ender pearl WHILE already in combat,
     * refresh their combat timer back to full.
     */
    @EventHandler
    public void onPearlLaunch(ProjectileLaunchEvent event) {
        if (!enabled) return;

        if (!(event.getEntity() instanceof EnderPearl pearl)) return;
        if (!(pearl.getShooter() instanceof Player player)) return;

        if (!isInCombat(player)) {
            // Throwing pearls out of combat does NOT start combat
            return;
        }

        // Refresh combat timer
        tag(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        combatUntil.remove(event.getPlayer().getUniqueId());
    }
}
