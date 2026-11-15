package org.fogofwarlabs.sc4j.scr.data.type.constant;

/**
 * Names the bits of the {@code units.dat} {@code StarEdit Availability Flags} column (the unit type table's availability
 * flags).
 * <p>
 * The names follow the StarEdit labels of the column.
 */
public final class UnitAvailabilityFlags {
    /**
     * Availability flag {@code 0x1} (StarEdit's "Non-Neutral"): the Sync checks the visibility of the sprites of such
     * non-building types.
     */
    public static final int NON_NEUTRAL = 0x1;
    /**
     * Availability flag {@code 0x2}: the type is listed in the editor's unit palette; triggers create only such types.
     */
    public static final int UNIT_LISTING = 0x2;
    /**
     * Availability flag {@code 0x200}: the type exists only with the expansion.
     */
    public static final int EXPANSION_ONLY = 0x200;

    private UnitAvailabilityFlags() {
    }
}
