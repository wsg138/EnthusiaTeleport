package org.enthusia.teleport.invsee;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public class InvseeHolder implements InventoryHolder {

    public enum ViewType {
        INVENTORY,
        ENDER_CHEST
    }

    private final UUID viewerId;
    private final UUID targetId;
    private final ViewType viewType;
    private final boolean editable;

    public InvseeHolder(UUID viewerId, UUID targetId, ViewType viewType, boolean editable) {
        this.viewerId = viewerId;
        this.targetId = targetId;
        this.viewType = viewType;
        this.editable = editable;
    }

    public UUID getViewerId() {
        return viewerId;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public ViewType getViewType() {
        return viewType;
    }

    public boolean isEditable() {
        return editable;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
