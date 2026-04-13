package org.enthusia.teleport.api;

import java.util.UUID;

public interface TeleportApi {

    /**
     * Base warmup in seconds from config, for informational use.
     */
    double getBaseWarmupSeconds();

    /**
     * Base cooldown in seconds from config, for informational use.
     */
    int getBaseCooldownSeconds();

    /**
     * Set a per-player warmup modifier.
     * 1.0 = normal, 0.5 = half as long, 2.0 = double.
     */
    void setWarmupModifier(UUID playerId, double modifier);

    /**
     * Set a per-player cooldown modifier.
     * 1.0 = normal, 0.5 = half, 2.0 = double.
     */
    void setCooldownModifier(UUID playerId, double modifier);

    /**
     * Get the effective warmup seconds that will currently be used for this player.
     */
    double getEffectiveWarmupSeconds(UUID playerId);

    /**
     * Get the effective cooldown seconds that will currently be used for this player.
     */
    int getEffectiveCooldownSeconds(UUID playerId);
}
