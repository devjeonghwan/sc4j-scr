package org.fogofwarlabs.sc4j.scr.data.type.constant;

/**
 * Names units.dat group flags; the three race bits form the race field.
 */
public final class UnitGroupFlags {
    /**
     * The Zerg race bit.
     */
    public static final int ZERG = 1;
    /**
     * The Terran race bit.
     */
    public static final int TERRAN = 2;
    /**
     * The Protoss race bit.
     */
    public static final int PROTOSS = 4;
    /**
     * The men group bit.
     */
    public static final int MEN = 8;
    /**
     * The building group bit.
     */
    public static final int BUILDING = 16;
    /**
     * The factory group bit.
     */
    public static final int FACTORY = 32;
    /**
     * The independent group bit.
     */
    public static final int INDEPENDENT = 64;
    /**
     * The neutral group bit.
     */
    public static final int NEUTRAL = 128;

    private UnitGroupFlags() {
    }
}
