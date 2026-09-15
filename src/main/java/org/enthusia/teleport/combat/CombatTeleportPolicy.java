package org.enthusia.teleport.combat;

import org.enthusia.teleport.request.TeleportRequestType;

/**
 * Pure combat policy shared by commands and CombatLogX event handling.
 * Keeping the policy dependency-free makes the intended request semantics
 * directly regression-testable without booting a Paper server.
 */
public final class CombatTeleportPolicy {

    private CombatTeleportPolicy() {
    }

    /**
     * Whether normal teleport behavior should be blocked for the player now.
     */
    public static boolean shouldBlock(boolean inCombat, boolean hasCombatBypass) {
        return inCombat && !hasCombatBypass;
    }

    /**
     * Whether a CombatLogX combat-entry event should affect this player's requests/warmups.
     */
    public static boolean shouldReactToCombatEntry(boolean hasCombatBypass) {
        return !hasCombatBypass;
    }

    /**
     * Accepted /tpahere warmups remain sensitive to the sender/anchor entering combat.
     * Accepted normal /tpa warmups intentionally do not, preserving the requested
     * grandfather behavior for the accepting anchor.
     */
    public static boolean cancelOnAnchorCombat(TeleportRequestType requestType) {
        return requestType == TeleportRequestType.TPA_HERE;
    }
}
