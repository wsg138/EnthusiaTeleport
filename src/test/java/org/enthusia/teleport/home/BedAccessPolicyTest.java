package org.enthusia.teleport.home;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedAccessPolicyTest {

    @Test
    void onlyZeroBedsMayCreateANewSavedBed() {
        assertTrue(BedAccessPolicy.canCreateNew(List.of()));
        assertFalse(BedAccessPolicy.canCreateNew(List.of(bed("bed", "world", 1, 64, 1))));
    }

    @Test
    void moreThanOneSavedBedIsMigrationOverLimit() {
        assertFalse(BedAccessPolicy.isOverLimit(List.of()));
        assertFalse(BedAccessPolicy.isOverLimit(List.of(bed("bed", "world", 1, 64, 1))));
        assertTrue(BedAccessPolicy.isOverLimit(List.of(
                bed("bed", "world", 1, 64, 1),
                bed("bed2", "world", 100, 70, 100)
        )));
    }

    @Test
    void samePhysicalBedMayRefreshButDifferentBedMayNot() {
        BedHome saved = bed("bed", "world", 10, 65, -4);

        assertTrue(BedAccessPolicy.matchesOnlySavedBed(List.of(saved), "world", 10, 65, -4));
        assertFalse(BedAccessPolicy.matchesOnlySavedBed(List.of(saved), "world", 11, 65, -4));
        assertFalse(BedAccessPolicy.matchesOnlySavedBed(List.of(saved), "world_nether", 10, 65, -4));
        assertFalse(BedAccessPolicy.matchesOnlySavedBed(List.of(
                saved,
                bed("bed2", "world", 20, 65, -4)
        ), "world", 10, 65, -4));
    }

    private BedHome bed(String name, String world, int x, int y, int z) {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000001");
        return new BedHome(
                owner,
                name.toLowerCase(),
                name,
                world,
                x, y, z,
                x + 0.5D, y + 0.1D, z + 0.5D,
                0.0F, 0.0F,
                1L, 1L
        );
    }
}
