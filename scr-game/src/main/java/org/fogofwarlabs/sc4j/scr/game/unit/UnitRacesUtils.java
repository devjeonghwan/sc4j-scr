package org.fogofwarlabs.sc4j.scr.game.unit;

import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitGroupFlags;
import org.jetbrains.annotations.NotNull;

/**
 * Reads the two race interpretations of a unit type from its StarEdit group flags. They differ in the order the flags are
 * tested, and both answer {@link #NEUTRAL} for a type without a playable race.
 */
public final class UnitRacesUtils {
    /**
     * The race of a type without the Zerg, Terran or Protoss group flag.
     */
    public static final int NEUTRAL = ScrRaceIds.NEUTRAL;

    /**
     * Returns the race of supply, costs and their error texts: Zerg, then Terran, then Protoss.
     *
     * @param units the unit type table
     * @param type  the units.dat type
     * @return {@code 0} Zerg, {@code 1} Terran, {@code 2} Protoss or {@link #NEUTRAL}
     */
    public static int getSupplyRace(@NotNull ScrUnitTypeView units, int type) {
        int flags = units.getGroupFlags(type);

        if ((flags & UnitGroupFlags.ZERG) != 0) {
            return ScrRaceIds.ZERG;
        }

        if ((flags & UnitGroupFlags.TERRAN) != 0) {
            return ScrRaceIds.TERRAN;
        }

        return (flags & UnitGroupFlags.PROTOSS) != 0 ? ScrRaceIds.PROTOSS : NEUTRAL;
    }

    /**
     * Returns the race of unit sounds and messages: Zerg, then Protoss, then Terran.
     *
     * @param units the unit type table
     * @param type  the units.dat type
     * @return {@code 0} Zerg, {@code 1} Terran, {@code 2} Protoss or {@link #NEUTRAL}
     */
    public static int getDisplayRace(@NotNull ScrUnitTypeView units, int type) {
        int flags = units.getGroupFlags(type);

        if ((flags & UnitGroupFlags.ZERG) != 0) {
            return ScrRaceIds.ZERG;
        }

        if ((flags & UnitGroupFlags.PROTOSS) != 0) {
            return ScrRaceIds.PROTOSS;
        }

        return (flags & UnitGroupFlags.TERRAN) != 0 ? ScrRaceIds.TERRAN : NEUTRAL;
    }


    private UnitRacesUtils() {
    }
}
