package org.enthusia.teleport.message;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.enthusia.teleport.EnthusiaTeleportPlugin;
import org.enthusia.teleport.util.Messages;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MessageManager {

    private final Messages messages;
    private final org.enthusia.teleport.log.MessageLogManager logManager;
    private final Map<UUID, List<UUID>> lastPartnersByPlayer = new HashMap<>();
    private final Map<UUID, List<String>> lastPartnerNamesByPlayer = new HashMap<>();

    public MessageManager(EnthusiaTeleportPlugin plugin) {
        this.messages = plugin.getMessages();
        this.logManager = plugin.getMessageLogManager();
    }

    public void recordIncoming(Player recipient, Player sender) {
        setLastPartners(recipient, java.util.List.of(sender));
    }

    public void setLastPartners(Player owner, List<Player> partners) {
        List<UUID> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Player partner : partners) {
            if (partner == null) continue;
            ids.add(partner.getUniqueId());
            names.add(partner.getName());
        }
        lastPartnersByPlayer.put(owner.getUniqueId(), ids);
        lastPartnerNamesByPlayer.put(owner.getUniqueId(), names);
    }

    public List<UUID> getLastPartnerIds(UUID playerId) {
        return lastPartnersByPlayer.getOrDefault(playerId, java.util.List.of());
    }

    public List<String> getLastPartnerNames(UUID playerId) {
        return lastPartnerNamesByPlayer.getOrDefault(playerId, java.util.List.of());
    }

    public void sendDirectMessage(Player sender, Player recipient, String message) {
        messages.send(sender, "message.sent",
                Map.of("target", recipient.getName(), "message", message));
        messages.send(recipient, "message.received",
                Map.of("sender", sender.getName(), "message", message));
        recipient.playSound(recipient.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.1f);
        recordIncoming(recipient, sender);
        logManager.logMsg(sender, java.util.List.of(recipient), message);
    }
}
