package org.enthusia.teleport.api;

import java.util.UUID;

/**
 * Public /tpignore contract for other plugins, registered with Bukkit's ServicesManager.
 *
 * Calls must be made on the server main thread. Callers pass identities they have already authenticated;
 * this API performs no permission checks and sends no player messages. Changes are saved the same way as
 * /tpignore and appear in /tpignore list.
 */
public interface TeleportIgnoreApi {

    /** Whether {@code receiver} ignores teleport requests from {@code sender}. */
    boolean isIgnoring(UUID receiver, UUID sender);

    /**
     * Ignore or stop ignoring teleport requests from {@code sender}. Setting the current state again changes nothing.
     *
     * @throws IllegalArgumentException if receiver and sender are the same player
     * @throws IllegalStateException if called off the main thread
     */
    void setIgnoring(UUID receiver, UUID sender, boolean ignore);
}
