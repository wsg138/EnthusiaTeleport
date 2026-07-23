package org.enthusia.teleport.combat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reflection-based bridge to NewPlayerProtection plugin.
 * Queries protection state and removes protection via NPP's internal ProtectionManager.
 * No compile-time dependency — safe when NPP is absent.
 */
public class NPPBridge {

    private Object protectionManager;
    private Method isProtectedMethod;
    private Method removeProtectionMethod;
    private boolean available;

    public NPPBridge() {
        tryHook();
    }

    public void tryHook() {
        this.available = false;
        this.protectionManager = null;
        this.isProtectedMethod = null;
        this.removeProtectionMethod = null;

        Plugin npp = Bukkit.getPluginManager().getPlugin("NewPlayerProtection");
        if (npp == null || !npp.isEnabled()) {
            return;
        }

        try {
            Field pmField = npp.getClass().getDeclaredField("protectionManager");
            pmField.setAccessible(true);
            this.protectionManager = pmField.get(npp);

            if (this.protectionManager == null) return;

            this.isProtectedMethod = this.protectionManager.getClass()
                    .getMethod("isProtected", Player.class);
            this.removeProtectionMethod = this.protectionManager.getClass()
                    .getMethod("removeProtection", Player.class);

            this.available = true;
            npp.getLogger().info("[NPPBridge] Hooked into NewPlayerProtection successfully.");
        } catch (Exception ex) {
            // NPP not present, wrong version, or internal API changed
            npp.getLogger().warning(
                    "[NPPBridge] Failed to hook NPP: " + ex.getMessage());
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isProtected(Player player) {
        if (!available || player == null) return false;
        try {
            Object result = isProtectedMethod.invoke(protectionManager, player);
            return result instanceof Boolean bool && bool;
        } catch (Exception ignored) {
            return false;
        }
    }

    public void removeProtection(Player player) {
        if (!available || player == null) return;
        try {
            removeProtectionMethod.invoke(protectionManager, player);
        } catch (Exception ignored) {
            // best-effort
        }
    }
}
