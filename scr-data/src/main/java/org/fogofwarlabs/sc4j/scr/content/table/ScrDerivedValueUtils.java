package org.fogofwarlabs.sc4j.scr.content.table;

import org.fogofwarlabs.sc4j.engine.content.definition.UnitTrait;
import org.fogofwarlabs.sc4j.engine.content.definition.WeaponTarget;
import org.fogofwarlabs.sc4j.engine.content.type.ContentType;
import org.fogofwarlabs.sc4j.scr.content.ScrRaces;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitGroupFlags;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.data.type.constant.WeaponTargetFlags;
import org.jetbrains.annotations.NotNull;

/**
 * Derives the engine column values that a native field determines but does not hold as such: the race of a unit type
 * from its group flags, the race of an upgrade or tech from its race byte, the unit traits from the unit flags, the
 * weapon targets from the target flags and a boolean from a native byte. The importer derives the initial values with
 * these rules and an SC:R table writes the derived value each time it writes the native field, so the engine column
 * always matches the native value.
 */
public final class ScrDerivedValueUtils {
    /**
     * The unit flags of each {@link UnitTrait}, in trait declaration order.
     */
    private static final int @NotNull [] TRAIT_FLAGS = {UnitTypeFlags.BUILDING, UnitTypeFlags.WORKER, UnitTypeFlags.FLYER,
                                                        UnitTypeFlags.RESOURCE_DEPOT, UnitTypeFlags.RESOURCE_CONTAINER,
                                                        UnitTypeFlags.DETECTOR, UnitTypeFlags.SPELLCASTER};

    /**
     * Returns the race of a unit type: Zerg, then Terran, then Protoss by the first race group flag set.
     *
     * @param groupFlags the group flags of the unit type
     * @return the race index, or {@link ContentType#NO_INDEX} if no race flag is set
     */
    public static int getUnitRaceIndex(int groupFlags) {
        if ((groupFlags & UnitGroupFlags.ZERG) != 0) {
            return ScrRaces.ZERG.getIndex();
        }

        if ((groupFlags & UnitGroupFlags.TERRAN) != 0) {
            return ScrRaces.TERRAN.getIndex();
        }

        return (groupFlags & UnitGroupFlags.PROTOSS) != 0 ? ScrRaces.PROTOSS.getIndex() : ContentType.NO_INDEX;
    }

    /**
     * Returns the race of an upgrade or tech from its race byte, which holds a native race number.
     *
     * @param nativeRace the race byte
     * @return the race index, or {@link ContentType#NO_INDEX} if the byte names no race (for example all races)
     */
    public static int getRaceIndex(int nativeRace) {
        return nativeRace >= 0 && nativeRace < ScrRaces.COUNT ? ScrRaces.get(nativeRace).getIndex() : ContentType.NO_INDEX;
    }

    /**
     * Returns the traits of a unit type: each {@link UnitTrait} whose unit flag is set.
     *
     * @param flags the unit flags
     * @return the trait mask
     */
    public static int getUnitTraits(int flags) {
        int traits = 0;

        for (UnitTrait trait : UnitTrait.values()) {
            if ((flags & TRAIT_FLAGS[trait.ordinal()]) != 0) {
                traits |= trait.getMask();
            }
        }

        return traits;
    }

    /**
     * Returns the targets of a weapon type: ground and air by their target flags, buildings unless the weapon excludes
     * them and own units if the weapon may target them.
     *
     * @param targetFlags the target flags of the weapon
     * @return the target mask
     */
    public static int getWeaponTargets(int targetFlags) {
        int targets = 0;

        if ((targetFlags & WeaponTargetFlags.GROUND) != 0) {
            targets |= WeaponTarget.GROUND.getMask();
        }

        if ((targetFlags & WeaponTargetFlags.AIR) != 0) {
            targets |= WeaponTarget.AIR.getMask();
        }

        if ((targetFlags & WeaponTargetFlags.NON_BUILDING) == 0) {
            targets |= WeaponTarget.BUILDING.getMask();
        }

        if ((targetFlags & WeaponTargetFlags.OWN) != 0) {
            targets |= WeaponTarget.OWN_UNIT.getMask();
        }

        return targets;
    }

    /**
     * @param value a native byte that the game reads as a condition
     * @return {@code true} if the byte is not zero, {@code false} otherwise
     */
    public static boolean isSet(int value) {
        return value != 0;
    }


    private ScrDerivedValueUtils() {
    }
}
