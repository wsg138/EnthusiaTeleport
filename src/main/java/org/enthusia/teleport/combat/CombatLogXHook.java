package org.enthusia.teleport.combat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.enthusia.teleport.EnthusiaTeleportPlugin;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Lightweight, reflection-based hook into CombatLogX.
 * Lets us query the plugin for "is player in combat?" without a compile-time dependency.
 */
public class CombatLogXHook {

    private final EnthusiaTeleportPlugin plugin;

    private Object combatManager;
    private Method isInCombatMethod;
    private Class<?> parameterType;

    public CombatLogXHook(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
        tryHook();
    }

    public void tryHook() {
        this.combatManager = null;
        this.isInCombatMethod = null;
        this.parameterType = null;

        Plugin combatLogX = Bukkit.getPluginManager().getPlugin("CombatLogX");
        if (combatLogX == null || !combatLogX.isEnabled()) {
            return;
        }

        try {
            Method getCombatManager = combatLogX.getClass().getMethod("getCombatManager");
            Object manager = getCombatManager.invoke(combatLogX);
            if (manager == null) return;

            Method isInCombat = findIsInCombatMethod(manager.getClass());
            if (isInCombat == null) {
                plugin.getLogger().warning("[EnthusiaTeleport] Found CombatLogX but could not find an isInCombat method.");
                return;
            }

            this.combatManager = manager;
            this.isInCombatMethod = isInCombat;
            this.parameterType = isInCombat.getParameterTypes()[0];

            plugin.getLogger().info("[EnthusiaTeleport] Hooked into CombatLogX for combat checks.");
        } catch (Exception ex) {
            plugin.getLogger().warning("[EnthusiaTeleport] Failed to hook into CombatLogX: " + ex.getMessage());
        }
    }

    public boolean isHooked() {
        return combatManager != null && isInCombatMethod != null;
    }

    public boolean isInCombat(Player player) {
        if (!isHooked() || player == null) return false;
        try {
            Object arg = buildArgument(player);
            Object result = isInCombatMethod.invoke(combatManager, arg);
            if (result instanceof Boolean bool) {
                return bool;
            }
        } catch (Exception ignored) {
            // fall through to false
        }
        return false;
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
