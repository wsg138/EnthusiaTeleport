package org.enthusia.teleport.request;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.enthusia.teleport.EnthusiaTeleportPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TeleportRequestManager implements Listener {

    private final EnthusiaTeleportPlugin plugin;
    private final Map<UUID, Map<UUID, TeleportRequest>> outgoing = new HashMap<>();
    private final Map<UUID, Map<UUID, TeleportRequest>> incoming = new HashMap<>();

    public TeleportRequestManager(EnthusiaTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    public void shutdown() {
        cancelAllForReload();
    }

    public void cancelAllForReload() {
        List<TeleportRequest> requests = new ArrayList<>();
        for (Map<UUID, TeleportRequest> map : outgoing.values()) {
            requests.addAll(map.values());
        }

        outgoing.clear();
        incoming.clear();

        List<UUID> notified = new ArrayList<>();
        for (TeleportRequest request : requests) {
            request.getExpiryTask().cancel();

            Player sender = Bukkit.getPlayer(request.getSender());
            Player target = Bukkit.getPlayer(request.getTarget());
            if (sender != null && sender.isOnline() && !notified.contains(sender.getUniqueId())) {
                plugin.getMessages().send(sender, "teleport.request.cancelled-reload");
                notified.add(sender.getUniqueId());
            }
            if (target != null && target.isOnline() && !notified.contains(target.getUniqueId())) {
                plugin.getMessages().send(target, "teleport.request.cancelled-reload");
                notified.add(target.getUniqueId());
            }
        }
    }

    public boolean hasOutgoingTo(Player sender, Player target) {
        return getOutgoingMap(sender.getUniqueId()).containsKey(target.getUniqueId());
    }

    public void createRequest(Player sender, Player target, TeleportRequestType type) {
        int expirySeconds = plugin.getPluginConfigManager().current().teleport().requestExpirySeconds();
        long expiryAt = System.currentTimeMillis() + expirySeconds * 1000L;

        UUID senderId = sender.getUniqueId();
        UUID targetId = target.getUniqueId();
        BukkitRunnable expiryTask = new BukkitRunnable() {
            @Override
            public void run() {
                TeleportRequest request = removeRequest(senderId, targetId, false);
                if (request == null) {
                    return;
                }
                if (sender.isOnline()) {
                    plugin.getMessages().send(sender, "teleport.expired-request-sender", Map.of("target", target.getName()));
                }
                if (target.isOnline()) {
                    plugin.getMessages().send(target, "teleport.expired-request-receiver", Map.of("sender", sender.getName()));
                }
            }
        };

        TeleportRequest request = new TeleportRequest(
                senderId,
                targetId,
                type,
                expiryAt,
                expiryTask.runTaskLater(plugin, expirySeconds * 20L)
        );

        getOutgoingMap(senderId).put(targetId, request);
        getIncomingMap(targetId).put(senderId, request);
    }

    public TeleportRequest getIncoming(Player target, Player sender) {
        return getIncomingMap(target.getUniqueId()).get(sender.getUniqueId());
    }

    public TeleportRequest getMostRecentIncoming(Player target) {
        TeleportRequest newest = null;
        for (TeleportRequest request : getIncomingMap(target.getUniqueId()).values()) {
            if (newest == null || request.getExpiryAt() > newest.getExpiryAt()) {
                newest = request;
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

    public void removeRequest(TeleportRequest request) {
        if (request == null) {
            return;
        }
        removeRequest(request.getSender(), request.getTarget(), true);
    }

    public void removeByPlayer(UUID playerId) {
        removeByPlayer(playerId, false);
    }

    public void removeByPlayer(UUID playerId, boolean notifyCounterpart) {
        Map<UUID, TeleportRequest> outgoingRequests = outgoing.remove(playerId);
        if (outgoingRequests != null) {
            for (TeleportRequest request : outgoingRequests.values()) {
                request.getExpiryTask().cancel();
                Map<UUID, TeleportRequest> targetIncoming = incoming.get(request.getTarget());
                if (targetIncoming != null) {
                    targetIncoming.remove(playerId);
                    if (targetIncoming.isEmpty()) {
                        incoming.remove(request.getTarget());
                    }
                }

                if (notifyCounterpart) {
                    Player target = Bukkit.getPlayer(request.getTarget());
                    if (target != null && target.isOnline()) {
                        plugin.getMessages().send(target, "teleport.request.cancelled-disconnect", Map.of("player", resolveName(playerId)));
                    }
                }
            }
        }

        Map<UUID, TeleportRequest> incomingRequests = incoming.remove(playerId);
        if (incomingRequests != null) {
            for (TeleportRequest request : incomingRequests.values()) {
                request.getExpiryTask().cancel();
                Map<UUID, TeleportRequest> senderOutgoing = outgoing.get(request.getSender());
                if (senderOutgoing != null) {
                    senderOutgoing.remove(playerId);
                    if (senderOutgoing.isEmpty()) {
                        outgoing.remove(request.getSender());
                    }
                }

                if (notifyCounterpart) {
                    Player sender = Bukkit.getPlayer(request.getSender());
                    if (sender != null && sender.isOnline()) {
                        plugin.getMessages().send(sender, "teleport.request.cancelled-disconnect", Map.of("player", resolveName(playerId)));
                    }
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeByPlayer(event.getPlayer().getUniqueId(), true);
    }

    private String resolveName(UUID playerId) {
        Player online = Bukkit.getPlayer(playerId);
        return online != null ? online.getName() : "That player";
    }

    private TeleportRequest removeRequest(UUID senderId, UUID targetId, boolean cancelTask) {
        Map<UUID, TeleportRequest> senderRequests = outgoing.get(senderId);
        TeleportRequest request = senderRequests != null ? senderRequests.remove(targetId) : null;
        if (senderRequests != null && senderRequests.isEmpty()) {
            outgoing.remove(senderId);
        }

        Map<UUID, TeleportRequest> targetRequests = incoming.get(targetId);
        if (targetRequests != null) {
            targetRequests.remove(senderId);
            if (targetRequests.isEmpty()) {
                incoming.remove(targetId);
            }
        }

        if (request != null && cancelTask) {
            request.getExpiryTask().cancel();
        }
        return request;
    }

    private Map<UUID, TeleportRequest> getOutgoingMap(UUID senderId) {
        return outgoing.computeIfAbsent(senderId, unused -> new HashMap<>());
    }

    private Map<UUID, TeleportRequest> getIncomingMap(UUID targetId) {
        return incoming.computeIfAbsent(targetId, unused -> new HashMap<>());
    }
}
