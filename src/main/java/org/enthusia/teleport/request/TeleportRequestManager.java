package org.enthusia.teleport.request;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.enthusia.teleport.EnthusiaTeleportPlugin;

import java.util.*;

public class TeleportRequestManager {

    private final EnthusiaTeleportPlugin plugin;

    // sender -> (target -> request)
    private final Map<UUID, Map<UUID, TeleportRequest>> outgoing = new HashMap<>();
    // target -> (sender -> request)
    private final Map<UUID, Map<UUID, TeleportRequest>> incoming = new HashMap<>();

    public TeleportRequestManager(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    public void shutdown() {
        for (Map<UUID, TeleportRequest> map : outgoing.values()) {
            for (TeleportRequest r : map.values()) {
                r.getExpiryTask().cancel();
            }
        }
        outgoing.clear();
        incoming.clear();
    }

    private Map<UUID, TeleportRequest> getOutgoingMap(UUID sender) {
        return outgoing.computeIfAbsent(sender, k -> new HashMap<>());
    }

    private Map<UUID, TeleportRequest> getIncomingMap(UUID target) {
        return incoming.computeIfAbsent(target, k -> new HashMap<>());
    }

    public boolean hasOutgoingTo(Player sender, Player target) {
        return getOutgoingMap(sender.getUniqueId()).containsKey(target.getUniqueId());
    }

    public void createRequest(Player sender, Player target, TeleportRequestType type) {
        UUID s = sender.getUniqueId();
        UUID t = target.getUniqueId();

        int expirySeconds = plugin.getConfig().getInt("teleport.request-expiry-seconds", 60);
        long expiryAt = System.currentTimeMillis() + expirySeconds * 1000L;

        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                TeleportRequest req = getOutgoingMap(s).remove(t);
                if (req == null) return;
                getIncomingMap(t).remove(s);

                Player sPlayer = sender;
                Player tPlayer = target;

                if (sPlayer != null && sPlayer.isOnline()) {
                    plugin.getMessages().send(sPlayer, "teleport.expired-request-sender",
                            Map.of("target", tPlayer != null ? tPlayer.getName() : "Unknown"));
                }
                if (tPlayer != null && tPlayer.isOnline()) {
                    plugin.getMessages().send(tPlayer, "teleport.expired-request-receiver",
                            Map.of("sender", sPlayer != null ? sPlayer.getName() : "Unknown"));
                }
            }
        };

        TeleportRequest req = new TeleportRequest(
                s,
                t,
                type,
                expiryAt,
                runnable.runTaskLater(plugin, expirySeconds * 20L)
        );

        getOutgoingMap(s).put(t, req);
        getIncomingMap(t).put(s, req);
    }

    public TeleportRequest getIncoming(Player target, Player sender) {
        return getIncomingMap(target.getUniqueId()).get(sender.getUniqueId());
    }

    public TeleportRequest getMostRecentIncoming(Player target) {
        Map<UUID, TeleportRequest> map = getIncomingMap(target.getUniqueId());
        if (map.isEmpty()) return null;

        TeleportRequest newest = null;
        for (TeleportRequest r : map.values()) {
            if (newest == null || r.getExpiryAt() > newest.getExpiryAt()) {
                newest = r;
            }
        }
        return newest;
    }

    public Collection<TeleportRequest> getOutgoingRequests(Player sender) {
        return new ArrayList<>(getOutgoingMap(sender.getUniqueId()).values());
    }

    public Collection<TeleportRequest> getIncomingRequests(Player target) {
        return new ArrayList<>(getIncomingMap(target.getUniqueId()).values());
    }

    public void removeRequest(TeleportRequest req) {
        if (req == null) return;
        UUID s = req.getSender();
        UUID t = req.getTarget();

        Map<UUID, TeleportRequest> outMap = getOutgoingMap(s);
        Map<UUID, TeleportRequest> inMap = getIncomingMap(t);

        TeleportRequest existing = outMap.remove(t);
        inMap.remove(s);
        if (existing != null) {
            existing.getExpiryTask().cancel();
        }
    }

    public void removeByPlayer(UUID playerId) {
        // Remove all outgoing from this player
        Map<UUID, TeleportRequest> out = outgoing.remove(playerId);
        if (out != null) {
            for (TeleportRequest req : out.values()) {
                req.getExpiryTask().cancel();
                getIncomingMap(req.getTarget()).remove(playerId);
            }
        }

        // Remove all incoming to this player
        Map<UUID, TeleportRequest> in = incoming.remove(playerId);
        if (in != null) {
            for (TeleportRequest req : in.values()) {
                req.getExpiryTask().cancel();
                getOutgoingMap(req.getSender()).remove(playerId);
            }
        }
    }
}
