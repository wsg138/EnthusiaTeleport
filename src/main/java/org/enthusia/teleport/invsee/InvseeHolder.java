package org.enthusia.teleport.invsee;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public class InvseeHolder implements InventoryHolder {

    private final UUID targetId;

    public InvseeHolder(UUID targetId) {
        this.targetId = targetId;
    }

    public UUID getTargetId() {
        return targetId;
    }

    @Override
    public Inventory getInventory() {
        // Bukkit sets this internally; we don't use it directly.
        return null;
    }
}
