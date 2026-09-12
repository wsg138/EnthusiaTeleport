package org.enthusia.teleport.home;

import java.util.Collection;

/**
 * Centralizes the invariant for persistent bed homes: a player may keep at most one.
 */
public final class BedAccessPolicy {

    public static final int MAX_SAVED_BEDS = 1;

    private BedAccessPolicy() {
    }

    public static boolean isOverLimit(Collection<BedHome> beds) {
        return beds != null && beds.size() > MAX_SAVED_BEDS;
    }

    public static boolean canCreateNew(Collection<BedHome> beds) {
        return beds == null || beds.isEmpty();
    }

    public static boolean matchesOnlySavedBed(
            Collection<BedHome> beds,
            String worldName,
            int bedX,
            int bedY,
            int bedZ
    ) {
        if (beds == null || beds.size() != MAX_SAVED_BEDS) {
            return false;
        }
        BedHome home = beds.iterator().next();
        return home.isAt(worldName, bedX, bedY, bedZ);
    }
}
