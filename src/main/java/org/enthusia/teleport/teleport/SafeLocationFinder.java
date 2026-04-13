package org.enthusia.teleport.teleport;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public class SafeLocationFinder {

    private final int radius;

    public SafeLocationFinder(int radius) {
        this.radius = radius;
    }

    /**
     * Used for live teleports (tpa, spawn, rtp, etc.).
     * Water is allowed, lava is not, need solid block under feet.
     */
    public Location findSafeTeleportLocation(Location target) {
        if (target == null || target.getWorld() == null) return null;
        World world = target.getWorld();

        int baseX = target.getBlockX();
        int baseY = target.getBlockY();
        int baseZ = target.getBlockZ();

        Location best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    int x = baseX + dx;
                    int y = baseY + dy;
                    int z = baseZ + dz;

                    Block feet = world.getBlockAt(x, y, z);
                    Block head = feet.getRelative(BlockFace.UP);
                    Block below = feet.getRelative(BlockFace.DOWN);

                    if (!isPassableForTeleport(feet.getType())) continue;
                    if (!isPassableForTeleport(head.getType())) continue;
                    if (!isSolidGroundForTeleport(below.getType())) continue;

                    Location candidate = new Location(
                            world,
                            x + 0.5,
                            y,
                            z + 0.5,
                            target.getYaw(),
                            target.getPitch()
                    );

                    double distSq = candidate.distanceSquared(target);
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        best = candidate;
                    }
                }
            }
        }

        return best;
    }

    private boolean isPassableForTeleport(Material mat) {
        if (mat.isAir()) return true;

        // allow water-y stuff for teleports
        if (mat == Material.WATER
                || mat == Material.KELP
                || mat == Material.KELP_PLANT
                || mat == Material.SEAGRASS
                || mat == Material.TALL_SEAGRASS) {
            return true;
        }

        return !mat.isSolid();
    }

    private boolean isSolidGroundForTeleport(Material mat) {
        if (mat == Material.LAVA || mat == Material.WATER) return false;
        return mat.isSolid();
    }

    /**
     * Stricter rules for homes:
     * - Feet + head passable and NOT liquid
     * - Block under is solid and NOT liquid
     * - No standing in water or lava
     */
    public boolean isSafeHomeLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        World world = loc.getWorld();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        Block feet = world.getBlockAt(x, y, z);
        Block head = feet.getRelative(BlockFace.UP);
        Block below = feet.getRelative(BlockFace.DOWN);

        if (!isPassableNonLiquid(feet.getType())) return false;
        if (!isPassableNonLiquid(head.getType())) return false;
        if (!isSolidNonLiquid(below.getType())) return false;

        return true;
    }

    private boolean isPassableNonLiquid(Material mat) {
        if (mat == Material.WATER || mat == Material.LAVA) return false;
        if (mat.isAir()) return true;
        return !mat.isSolid();
    }

    private boolean isSolidNonLiquid(Material mat) {
        if (mat == Material.WATER || mat == Material.LAVA) return false;
        return mat.isSolid();
    }
}
