package org.fogofwarlabs.sc4j.scr.game.unit;

import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.jetbrains.annotations.NotNull;

/**
 * Defines the shared unit type bounds and the spellcaster test. The unit type sets are trait columns of the unit type view
 * ({@link ScrUnitTypeView#getTraits(int)}).
 */
public final class UnitTypeTraitsUtils {
    /**
     * The last unit type before the buildings (Spell Disruption Web): the types up to it are the units a build queue
     * trains and the units whose {@code units.dat} subunit is created with them.
     */
    public static final int LAST_NON_BUILDING_TYPE = 0x69;

    /**
     * Tests a unit type a build queue trains: a type up to {@link #LAST_NON_BUILDING_TYPE} or an added unit type.
     *
     * @param units the unit type table
     * @param type  the unit type
     * @return {@code true} if the type is up to {@link #LAST_NON_BUILDING_TYPE} or an added type, {@code false} otherwise
     */
    public static boolean isQueueUnitType(@NotNull ScrUnitTypeView units, int type) {
        return type <= LAST_NON_BUILDING_TYPE || units.isAddedType(type);
    }

    /**
     * Tests a spellcaster type that is not a hallucination.
     *
     * @param units the unit type table
     * @param unit  the unit
     * @return {@code true} if the unit is a spellcaster and is not a hallucination, {@code false} otherwise
     */
    public static boolean isSpellcaster(@NotNull ScrUnitTypeView units, @NotNull Unit unit) {
        return (units.getFlags(unit.getUnitId()) & UnitTypeFlags.SPELLCASTER) != 0 && (unit.getStatus() & UnitStatusFlags.HALLUCINATION) == 0;
    }


    private UnitTypeTraitsUtils() {
    }
}
