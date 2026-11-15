package org.fogofwarlabs.sc4j.scr.data.type.constant;

/**
 * Names the bits of the {@code units.dat} {@code AI Internal} column (the unit type table's computer internal flags).
 * <p>
 * The names describe how the computer players use each bit.
 */
public final class UnitComputerFlags {
    /**
     * AI Internal flag {@code 1}: the type never counts for a computer attack group.
     */
    public static final int ATTACK_GROUP_IGNORED = 0x01;
    /**
     * AI Internal flag {@code 2}: an adopted unit of the type joins its owner's first town instead of becoming a guard.
     */
    public static final int TOWN_UNIT = 0x02;

    private UnitComputerFlags() {
    }
}
