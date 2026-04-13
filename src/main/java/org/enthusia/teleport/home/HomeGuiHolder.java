package org.enthusia.teleport.home;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public class HomeGuiHolder implements InventoryHolder {

    private final UUID owner;

    public HomeGuiHolder(UUID owner) {
        this.owner = owner;
    }

    public UUID getOwner() {
        return owner;
    }

    @Override
    public Inventory getInventory() {
        return null; // will be set by Bukkit when inventory is created
    }
}
