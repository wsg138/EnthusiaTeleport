package org.enthusia.teleport.log;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class ChatLogListener implements Listener {

    private final MessageLogManager logManager;

    public ChatLogListener(MessageLogManager logManager) {
        this.logManager = logManager;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (message == null || message.isEmpty()) return;
        logManager.logChat(event.getPlayer(), message);
    }
}
