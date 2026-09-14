package org.enthusia.teleport.combat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.enthusia.teleport.EnthusiaTeleportPlugin;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Reflection-based hook into CombatLogX.
 * CombatLogX is the sole authority for whether a player is combat tagged.
 */
public class CombatLogXHook {

    private final EnthusiaTeleportPlugin plugin;

    private Object combatManager;
    private Method isInCombatMethod;
    private Class<?> parameterType;
    private boolean queryFailureWarned;

    public CombatLogXHook(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
        tryHook();
    }

    public void tryHook() {
        this.combatManager = null;
        this.isInCombatMethod = null;
        this.parameterType = null;
        this.queryFailureWarned = false;

        Plugin combatLogX = Bukkit.getPluginManager().getPlugin("CombatLogX");
        if (combatLogX == null || !combatLogX.isEnabled()) {
            plugin.getLogger().severe("[EnthusiaTeleport] CombatLogX is required but is not enabled.");
            return;
        }

        try {
            Method getCombatManager = combatLogX.getClass().getMethod("getCombatManager");
            Object manager = getCombatManager.invoke(combatLogX);
            if (manager == null) {
                plugin.getLogger().severe("[EnthusiaTeleport] CombatLogX returned no combat manager.");
                return;
            }

            Method isInCombat = findIsInCombatMethod(manager.getClass());
            if (isInCombat == null) {
                plugin.getLogger().severe("[EnthusiaTeleport] Found CombatLogX but could not find an isInCombat method.");
                return;
            }

            this.combatManager = manager;
            this.isInCombatMethod = isInCombat;
            this.parameterType = isInCombat.getParameterTypes()[0];

            plugin.getLogger().info("[EnthusiaTeleport] Hooked into CombatLogX as the authoritative combat source.");
        } catch (Exception ex) {
            plugin.getLogger().severe("[EnthusiaTeleport] Failed to hook into CombatLogX: " + ex.getMessage());
        }
    }

    public boolean isHooked() {
        return combatManager != null && isInCombatMethod != null;
    }

    public boolean isInCombat(Player player) {
        if (player == null) return false;

        // Fail closed if the required combat integration is unavailable. Silently
        // allowing teleports here would reintroduce combat-escape behavior.
        if (!isHooked()) {
            warnQueryFailure("CombatLogX hook is unavailable");
            return true;
        }

        try {
            Object arg = buildArgument(player);
            Object result = isInCombatMethod.invoke(combatManager, arg);
            if (result instanceof Boolean bool) {
                return bool;
            }
            warnQueryFailure("CombatLogX returned a non-boolean combat result");
        } catch (Exception ex) {
            warnQueryFailure("CombatLogX combat query failed: " + ex.getMessage());
        }

        return true;
    }

    private void warnQueryFailure(String message) {
        if (queryFailureWarned) return;
        queryFailureWarned = true;
        plugin.getLogger().severe("[EnthusiaTeleport] " + message + "; teleport combat checks will fail closed.");
    }

    private Method findIsInCombatMethod(Class<?> managerClass) {
        for (String name : new String[]{"isInCombat", "isTagged"}) {
            for (Class<?> param : new Class<?>[]{Player.class, Entity.class, UUID.class}) {
                try {
                    return managerClass.getMethod(name, param);
                } catch (NoSuchMethodException ignored) {
                    // keep searching
                }
            }
        }
        return null;
    }

    private Object buildArgument(Player player) {
        if (parameterType == null) return player;

        if (parameterType.equals(UUID.class)) {
            return player.getUniqueId();
        }
        if (parameterType.isAssignableFrom(Player.class) || parameterType.isAssignableFrom(Entity.class)) {
            return player;
        }
        return player;
    }
}
