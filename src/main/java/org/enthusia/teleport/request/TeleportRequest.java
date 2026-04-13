package org.enthusia.teleport.request;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public class TeleportRequest {

    private final UUID sender;
    private final UUID target;
    private final TeleportRequestType type;
    private final long expiryAt;
    private final BukkitTask expiryTask;

    public TeleportRequest(UUID sender,
                           UUID target,
                           TeleportRequestType type,
                           long expiryAt,
                           BukkitTask expiryTask) {
        this.sender = sender;
        this.target = target;
        this.type = type;
        this.expiryAt = expiryAt;
        this.expiryTask = expiryTask;
    }

    public UUID getSender() {
        return sender;
    }

    public UUID getTarget() {
        return target;
    }

    public TeleportRequestType getType() {
        return type;
    }

    public long getExpiryAt() {
        return expiryAt;
    }

    public BukkitTask getExpiryTask() {
        return expiryTask;
    }

    public Player getSenderPlayer() {
        return Bukkit.getPlayer(sender);
    }

    public Player getTargetPlayer() {
        return Bukkit.getPlayer(target);
    }
}
