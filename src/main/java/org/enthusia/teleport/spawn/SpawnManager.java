package org.enthusia.teleport.spawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.enthusia.teleport.EnthusiaTeleportPlugin;

import java.util.Arrays;

/**
 * Handles spawn location lookups and first-time join perks.
 */
public class SpawnManager implements Listener {

    private final EnthusiaTeleportPlugin plugin;

    public SpawnManager(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    public Location getSpawnLocation() {
        String worldName = plugin.getConfig().getString("spawn.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            if (Bukkit.getWorlds().isEmpty()) {
                return null;
            }
            world = Bukkit.getWorlds().get(0);
        }

        boolean centerOnBlock = plugin.getConfig().getBoolean("spawn.center-on-block", true);

        double x = center(plugin.getConfig().getDouble("spawn.x", 0.5), centerOnBlock);
        double y = plugin.getConfig().getDouble("spawn.y", 64.0);
        double z = center(plugin.getConfig().getDouble("spawn.z", 0.5), centerOnBlock);
        float yaw = (float) plugin.getConfig().getDouble("spawn.yaw", 0.0);
        float pitch = (float) plugin.getConfig().getDouble("spawn.pitch", 0.0);

        return new Location(world, x, y, z, yaw, pitch);
    }

    private double center(double coord, boolean centerOnBlock) {
        if (!centerOnBlock) return coord;
        if (Math.abs(coord - Math.round(coord)) > 1e-9) return coord;
        return Math.round(coord) + 0.5;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Location spawn = getSpawnLocation();
        if (spawn == null) return;
        event.setRespawnLocation(spawn);
    }

    @EventHandler
    public void onFirstJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPlayedBefore()) return;

        Location spawn = getSpawnLocation();
        if (spawn != null) {
            player.teleport(spawn);
            player.setBedSpawnLocation(spawn, true);
        }

        ItemStack steak = new ItemStack(Material.COOKED_BEEF, 8);
        ItemStack sword = new ItemStack(Material.STONE_SWORD);
        ItemStack axe = new ItemStack(Material.STONE_AXE);
        ItemStack shovel = new ItemStack(Material.STONE_SHOVEL);
        ItemStack pickaxe = new ItemStack(Material.STONE_PICKAXE);

        Arrays.asList(steak, sword, axe, shovel, pickaxe)
                .forEach(item -> player.getInventory().addItem(item));
    }
}
