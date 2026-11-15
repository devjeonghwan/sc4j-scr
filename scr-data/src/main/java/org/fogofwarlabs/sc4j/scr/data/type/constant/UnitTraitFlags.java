package org.fogofwarlabs.sc4j.scr.data.type.constant;

/**
 * Names the bits of the unit trait column ({@link org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView#getTraits(int)}): the unit type sets the
 * SC:R rules test that no {@code units.dat} field holds. The importer fills the column; the bit values are no native layout.
 */
public final class UnitTraitFlags {
    /**
     * The Ghost types, which can lock down, cloak and nuke: Ghost, Sarah Kerrigan, Samir Duran, Alexei Stukov and Infested Duran.
     */
    public static final int GHOST = 1;
    /**
     * The types a Zerg building morphs into: Lair, Hive, Greater Spire, Spore Colony and Sunken Colony.
     */
    public static final int BUILDING_MORPH_RESULT = 1 << 1;
    /**
     * The types with a hangar: Carrier, Warbringer, Gantrithor and Reaver.
     */
    public static final int HANGAR = 1 << 2;
    /**
     * The types whose hangar holds Interceptors: Carrier and Gantrithor.
     */
    public static final int INTERCEPTOR_HANGAR = 1 << 3;
    /**
     * The types whose hangar holds Scarabs: Reaver and Warbringer.
     */
    public static final int SCARAB_HANGAR = 1 << 4;
    /**
     * The eggs whose unit morphs into the queued type: Egg, Cocoon and Lurker Egg.
     */
    public static final int EGG = 1 << 5;
    /**
     * The gas structures built on a Vespene Geyser: Refinery, Extractor and Assimilator.
     */
    public static final int GAS_STRUCTURE = 1 << 6;
    /**
     * The Larva and the eggs: Larva, Egg, Cocoon and Lurker Egg.
     */
    public static final int LARVA_OR_EGG = 1 << 7;
    /**
     * The three mineral field types.
     */
    public static final int MINERAL_FIELD = 1 << 8;
    /**
     * The buildings that produce units and keep a rally point (construction button set {@code 234}).
     */
    public static final int RALLY_PRODUCER = 1 << 9;
    /**
     * The beacon types, the flag beacons included.
     */
    public static final int BEACON = 1 << 10;
    /**
     * The Vulture types, which carry Spider Mines: Vulture and Jim Raynor's Vulture.
     */
    public static final int VULTURE = 1 << 11;
    /**
     * The map doodad units, which are shown when they are completed before placement: the traps, the Floor Hatch and the doors.
     */
    public static final int MAP_DOODAD = 1 << 12;
    /**
     * The traps, which are cloaked and undetected once completed: the floor and wall missile, gun and flame traps.
     */
    public static final int TRAP = 1 << 13;
    /**
     * The map revealers, which join the map revealer list when they are completed: Scanner Sweep and Map Revealer.
     */
    public static final int MAP_REVEALER = 1 << 14;
    /**
     * The hangar units, which stay hidden when they are completed: Interceptor and Scarab.
     */
    public static final int HANGAR_UNIT = 1 << 15;
    /**
     * The types that never collide with other units: the beacons and the Floor Hatch.
     */
    public static final int COLLISION_FREE = 1 << 16;
    /**
     * The non-building types whose sprite visibility follows the building rules (fog sprites, kept visibility): the floor and wall traps and
     * the doors, from the Floor Missile Trap to the Right Wall Flame Trap.
     */
    public static final int BUILDING_VISIBILITY = 1 << 17;
    /**
     * The types without sight of their own: the Nuclear Missile.
     */
    public static final int SIGHTLESS = 1 << 18;
    /**
     * The types a cloaking field never cloaks: the Arbiter types, which cast the field, and the Nuclear Missile.
     */
    public static final int CLOAK_FIELD_EXEMPT = 1 << 19;
    /**
     * The types whose order timer restarts at once whenever their ground weapon cooldown is over: Zergling and Devouring One.
     */
    public static final int COOLDOWN_ORDER_TIMER = 1 << 20;
    /**
     * The types that take no part in the flyer repulsion: the Interceptor.
     */
    public static final int NO_AIR_REPULSION = 1 << 21;
    /**
     * The types that never block a local path: the four doors from the Upper Level Door.
     */
    public static final int PATH_TRANSPARENT = 1 << 22;

    private UnitTraitFlags() {
    }
}
