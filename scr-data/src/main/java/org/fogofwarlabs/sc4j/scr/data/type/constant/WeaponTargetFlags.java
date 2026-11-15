package org.fogofwarlabs.sc4j.scr.data.type.constant;

/**
 * Names weapons.dat flags that select permitted unit and position targets.
 */
public final class WeaponTargetFlags {
    /**
     * The weapon can target flying units.
     */
    public static final int AIR = 0x001;
    /**
     * The weapon can target ground units.
     */
    public static final int GROUND = 0x002;
    /**
     * The weapon targets only mechanical units.
     */
    public static final int MECHANICAL = 0x004;
    /**
     * The weapon targets only organic units.
     */
    public static final int ORGANIC = 0x008;
    /**
     * The weapon cannot target buildings.
     */
    public static final int NON_BUILDING = 0x010;
    /**
     * The weapon cannot target robotic units.
     */
    public static final int NON_ROBOTIC = 0x020;
    /**
     * The weapon can target a position on the map instead of a unit.
     */
    public static final int TERRAIN = 0x040;
    /**
     * The weapon targets only units that are organic or mechanical.
     */
    public static final int ORGANIC_OR_MECHANICAL = 0x080;
    /**
     * The weapon targets only units of its owner.
     */
    public static final int OWN = 0x100;

    private WeaponTargetFlags() {
    }
}
