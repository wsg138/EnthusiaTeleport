package org.enthusia.teleport.home;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public class AdminHomeGuiHolder implements InventoryHolder {

    private final UUID viewer;
    private final UUID target;
    private final String targetName;

    public AdminHomeGuiHolder(UUID viewer, UUID target, String targetName) {
        this.viewer = viewer;
        this.target = target;
        this.targetName = targetName;
    }

    public UUID getViewer() {
        return viewer;
    }

    public UUID getTarget() {
        return target;
    }

    public String getTargetName() {
        return targetName;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
