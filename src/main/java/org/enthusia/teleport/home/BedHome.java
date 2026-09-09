package org.enthusia.teleport.home;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public final class BedHome {

    private final UUID owner;
    private final String key;
    private final String name;
    private final String worldName;
    private final int bedX;
    private final int bedY;
    private final int bedZ;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final long createdAt;
    private final long lastUsedAt;

    public BedHome(UUID owner, String key, String name, String worldName,
                   int bedX, int bedY, int bedZ,
                   double x, double y, double z,
                   float yaw, float pitch,
                   long createdAt, long lastUsedAt) {
        this.owner = owner;
        this.key = key;
        this.name = name;
        this.worldName = worldName;
        this.bedX = bedX;
        this.bedY = bedY;
        this.bedZ = bedZ;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.createdAt = createdAt;
        this.lastUsedAt = lastUsedAt;
    }

    public UUID getOwner() {
        return owner;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getBedX() {
        return bedX;
    }

    public int getBedY() {
        return bedY;
    }

    public int getBedZ() {
        return bedZ;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getLastUsedAt() {
        return lastUsedAt;
    }

    public boolean isAt(String world, int blockX, int blockY, int blockZ) {
        return worldName.equals(world) && bedX == blockX && bedY == blockY && bedZ == blockZ;
    }

    public Location toLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z, yaw, pitch);
    }
}
