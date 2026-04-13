package org.enthusia.teleport.home;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class HomeLimitGuiHolder implements InventoryHolder {

    private final UUID owner;
    private final int limit;
    private final Set<String> selected = new HashSet<>();

    public HomeLimitGuiHolder(UUID owner, int limit) {
        this.owner = owner;
        this.limit = limit;
    }

    public UUID getOwner() {
        return owner;
    }

    public int getLimit() {
        return limit;
    }

    public Set<String> getSelected() {
        return selected;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
