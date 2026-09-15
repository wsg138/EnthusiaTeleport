package org.enthusia.teleport.combat;

import org.enthusia.teleport.request.TeleportRequestType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatTeleportPolicyTest {

    @Test
    void blocksOnlyWhenInCombatWithoutBypass() {
        assertFalse(CombatTeleportPolicy.shouldBlock(false, false));
        assertFalse(CombatTeleportPolicy.shouldBlock(false, true));
        assertTrue(CombatTeleportPolicy.shouldBlock(true, false));
        assertFalse(CombatTeleportPolicy.shouldBlock(true, true));
    }

    @Test
    void combatEntryReactionRespectsBypass() {
        assertTrue(CombatTeleportPolicy.shouldReactToCombatEntry(false));
        assertFalse(CombatTeleportPolicy.shouldReactToCombatEntry(true));
    }

    @Test
    void acceptedNormalTpaGrandfathersAnchorCombatButTpahereDoesNot() {
        assertFalse(CombatTeleportPolicy.cancelOnAnchorCombat(TeleportRequestType.TPA));
        assertTrue(CombatTeleportPolicy.cancelOnAnchorCombat(TeleportRequestType.TPA_HERE));
    }
}
