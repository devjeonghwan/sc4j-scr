package org.fogofwarlabs.sc4j.scr.data.type.constant;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;

/**
 * Names the unit-type operands of trigger conditions and actions that select a group instead of a type.
 * <p>
 * Values below {@code 228} are {@code units.dat} types ({@link ScrUnitIds}); {@code 228} is the unit type none.
 * Names follow the StarEdit labels.
 */
public final class TriggerUnitGroups {
    /**
     * The unit type none ({@link ScrUnitIds#NONE}).
     */
    public static final int NONE = ScrUnitIds.NONE;
    /**
     * Any unit: men and buildings.
     */
    public static final int ANY_UNIT = 229;
    /**
     * Men: units with the {@link UnitGroupFlags#MEN} group flag.
     */
    public static final int MEN = 230;
    /**
     * Buildings: units with the {@link UnitGroupFlags#BUILDING} group flag.
     */
    public static final int BUILDINGS = 231;
    /**
     * Factories: units with the {@link UnitGroupFlags#FACTORY} group flag.
     */
    public static final int FACTORIES = 232;

    private TriggerUnitGroups() {
    }
}
