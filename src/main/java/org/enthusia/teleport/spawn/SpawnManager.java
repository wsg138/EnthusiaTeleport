package org.enthusia.teleport.spawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.config.PluginConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SpawnManager implements Listener {

    private static final NamespacedKey SPAWN_PREFERENCE =
            new NamespacedKey("enthusiateleport", "respawn_at_spawn");
    private final EnthusiaTeleportPlugin plugin;

    public boolean prefersServerSpawn(Player player) {
        return player.getPersistentDataContainer().has(SPAWN_PREFERENCE, PersistentDataType.BYTE);
    }

    public void setServerSpawnPreference(Player player, boolean useSpawn) {
        if (useSpawn) {
            player.getPersistentDataContainer().set(SPAWN_PREFERENCE, PersistentDataType.BYTE, (byte) 1);
        } else {
            player.getPersistentDataContainer().remove(SPAWN_PREFERENCE);
        }
    }

    public SpawnManager(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    public Location getSpawnLocation() {
        PluginConfig.SpawnSettings settings = plugin.getPluginConfigManager().current().spawn();
        World world = Bukkit.getWorld(settings.world());
        if (world == null) {
            if (Bukkit.getWorlds().isEmpty()) {
                return null;
            }
            world = Bukkit.getWorlds().get(0);
        }

        if (!settings.useConfiguredSpawn()) {
            return world.getSpawnLocation();
        }

        double x = center(settings.x(), settings.centerOnBlock());
        double z = center(settings.z(), settings.centerOnBlock());
        return new Location(world, x, settings.y(), z, settings.yaw(), settings.pitch());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!plugin.getPluginConfigManager().current().spawn().respawnOverrideEnabled()) {
            return;
        }

        if (event.getRespawnReason() == PlayerRespawnEvent.RespawnReason.DEATH
                && (event.isBedSpawn() || event.isAnchorSpawn()) && !event.isMissingRespawnBlock()
                && !prefersServerSpawn(event.getPlayer())) {
            // Paper has already found a safe place beside the player's active respawn block.
            return;
        }

        Location spawn = getSpawnLocation();
        if (spawn != null) {
            event.setRespawnLocation(spawn);
        }
    }

    @EventHandler
    public void onFirstJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPlayedBefore()) {
            return;
        }

        PluginConfig.SpawnSettings settings = plugin.getPluginConfigManager().current().spawn();
        Location spawn = getSpawnLocation();

        if (settings.firstJoinTeleportEnabled() && spawn != null) {
            player.teleport(spawn);
        }
        if (settings.firstJoinSetBedSpawn() && spawn != null) {
            player.setBedSpawnLocation(spawn, true);
        }
        if (settings.firstJoinKitEnabled()) {
            giveStarterKit(player, settings);
        }
        new org.enthusia.teleport.rtp.SurvivalOnboarding(plugin).firstJoin(player);
    }

    private void giveStarterKit(Player player, PluginConfig.SpawnSettings settings) {
        if (settings.firstJoinKitClearInventory()) {
            player.getInventory().clear();
        }
        for (ItemStack item : resolveStarterKit(settings)) {
            player.getInventory().addItem(item);
        }
    }

    private List<ItemStack> resolveStarterKit(PluginConfig.SpawnSettings settings) {
        List<ItemStack> items = new ArrayList<>();
        for (PluginConfig.KitItem kitItem : settings.starterKitItems()) {
            Material material = Material.matchMaterial(kitItem.material().toUpperCase(Locale.ROOT));
            if (material == null || material.isAir()) {
                plugin.getLogger().warning("Ignoring invalid starter kit material: " + kitItem.material());
                continue;
            }
            items.add(new ItemStack(material, kitItem.amount()));
        }
        return items;
    }

    private double center(double value, boolean centerOnBlock) {
        if (!centerOnBlock) {
            return value;
        }
        if (Math.abs(value - Math.round(value)) > 1.0E-9D) {
            return value;
        }
        return Math.round(value) + 0.5D;
    }
}
