package org.fogofwarlabs.sc4j.scr.content;

import org.fogofwarlabs.sc4j.engine.entity.column.ColumnKey;
import org.fogofwarlabs.sc4j.engine.entity.column.ColumnReset;
import org.fogofwarlabs.sc4j.engine.entity.column.IntColumn;
import org.fogofwarlabs.sc4j.scr.data.type.ArrayDatColumn;
import org.fogofwarlabs.sc4j.scr.data.type.DatColumn;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTraitFlags;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * Derives the unit trait columns of the SC:R content ({@link ScrUnitTypeView.TraitColumn}): the per-type values of the SC:R rules that no
 * {@code units.dat} field holds. The content importer registers them as extension columns of the unit type table, so every game reads its own
 * columns through its {@link ScrUnitTypeView}; a unit type table parsed outside a game reads the same values from {@link #createColumns()}.
 * The simulation reads the traits only through a type view.
 */
public final class ScrUnitTraits {
    /**
     * The number of unit types of the trait columns, the units.dat entries.
     */
    public static final int TYPE_COUNT = ScrUnitIds.NONE;

    /**
     * The upgraded top speed of a Scout, Mojo or Artanis ({@link ScrUnitTypeView.TraitColumn#UPGRADED_TOP_SPEED}).
     */
    private static final int UPGRADED_SCOUT_SPEED = 0x6AB;
    /**
     * The initial energy of a Shield Battery ({@link ScrUnitTypeView.TraitColumn#INITIAL_ENERGY}), fixed-point with eight fraction bits.
     */
    private static final int SHIELD_BATTERY_INITIAL_ENERGY = 0x6400;
    /**
     * The Spider Mines a Vulture type starts with ({@link ScrUnitTypeView.TraitColumn#INITIAL_AMMUNITION}).
     */
    private static final int VULTURE_SPIDER_MINES = 3;
    /**
     * The base hangar capacity of a Carrier type ({@link ScrUnitTypeView.TraitColumn#HANGAR_CAPACITY}).
     */
    private static final int CARRIER_CAPACITY = 4;
    /**
     * The base hangar capacity of a Reaver type.
     */
    private static final int REAVER_CAPACITY = 5;
    /**
     * The unit types {@code ZERG_LAIR + bit} that a Zerg building morphs into: Lair, Hive, Greater Spire, Spore Colony and Sunken Colony.
     */
    private static final int BUILDING_MORPH_TYPES = 0x5023;
    /**
     * The highest bit of {@link #BUILDING_MORPH_TYPES}, the offset of the Sunken Colony from the Lair.
     */
    private static final int BUILDING_MORPH_LAST_OFFSET = 14;
    /**
     * The first unit type of the rally producer bit mask.
     */
    private static final int RALLY_PRODUCER_BASE = 0x6A;
    /**
     * The last bit index of that mask.
     */
    private static final int RALLY_PRODUCER_LAST_BIT = 0x3D;
    /**
     * The rally producers from {@link #RALLY_PRODUCER_BASE}: Command Center, Barracks, Factory, Starport, Infested
     * Command Center, Hatchery, Lair, Hive, Nexus, Robotics Facility, Gateway and Stargate.
     */
    private static final long RALLY_PRODUCER_MASK = 0x204300000F0001A1L;
    /**
     * The unit types {@code ZERG_LARVA + bit} of the Larva and the eggs: Larva, Egg, Cocoon and Lurker Egg.
     */
    private static final long LARVA_AND_EGG_TYPES = 0x4000000001000003L;
    /**
     * The highest bit of {@link #LARVA_AND_EGG_TYPES}.
     */
    private static final int LARVA_AND_EGG_LAST_OFFSET = 0x3E;

    /**
     * The extension column key of {@link ScrUnitTypeView.TraitColumn#TRAITS}.
     */
    private static final @NotNull ColumnKey<IntColumn> TRAITS_KEY = ColumnKey.ofInt("scr.units.traits", 0, ColumnReset.KEEP);
    /**
     * The extension column key of {@link ScrUnitTypeView.TraitColumn#UPGRADED_TOP_SPEED}.
     */
    private static final @NotNull ColumnKey<IntColumn> UPGRADED_TOP_SPEED_KEY = ColumnKey.ofInt("scr.units.upgradedTopSpeed", 0, ColumnReset.KEEP);
    /**
     * The extension column key of {@link ScrUnitTypeView.TraitColumn#HANGAR_CAPACITY}.
     */
    private static final @NotNull ColumnKey<IntColumn> HANGAR_CAPACITY_KEY = ColumnKey.ofInt("scr.units.hangarCapacity", 0, ColumnReset.KEEP);
    /**
     * The extension column key of {@link ScrUnitTypeView.TraitColumn#TRANSPORT_UPGRADE}.
     */
    private static final @NotNull ColumnKey<IntColumn> TRANSPORT_UPGRADE_KEY = ColumnKey.ofInt("scr.units.transportUpgrade", ScrUpgradeIds.NONE,
                                                                                               ColumnReset.KEEP);
    /**
     * The extension column key of {@link ScrUnitTypeView.TraitColumn#INITIAL_ENERGY}.
     */
    private static final @NotNull ColumnKey<IntColumn> INITIAL_ENERGY_KEY = ColumnKey.ofInt("scr.units.initialEnergy",
                                                                                            ScrUnitTypeView.DEFAULT_INITIAL_ENERGY,
                                                                                            ColumnReset.KEEP);
    /**
     * The extension column key of {@link ScrUnitTypeView.TraitColumn#INITIAL_AMMUNITION}.
     */
    private static final @NotNull ColumnKey<IntColumn> INITIAL_AMMUNITION_KEY = ColumnKey.ofInt("scr.units.initialAmmunition", 0, ColumnReset.KEEP);

    /**
     * Returns the extension column key of a trait column.
     *
     * @param column the trait column
     * @return the key the content importer registers the column under
     */
    public static @NotNull ColumnKey<IntColumn> getKey(ScrUnitTypeView.@NotNull TraitColumn column) {
        return switch (column) {
            case TRAITS -> TRAITS_KEY;
            case UPGRADED_TOP_SPEED -> UPGRADED_TOP_SPEED_KEY;
            case HANGAR_CAPACITY -> HANGAR_CAPACITY_KEY;
            case TRANSPORT_UPGRADE -> TRANSPORT_UPGRADE_KEY;
            case INITIAL_ENERGY -> INITIAL_ENERGY_KEY;
            case INITIAL_AMMUNITION -> INITIAL_AMMUNITION_KEY;
        };
    }

    /**
     * Creates the values of a trait column, indexed by the units.dat entry.
     *
     * @param column the trait column
     * @return the created values, {@link #TYPE_COUNT} entries
     */
    public static int @NotNull [] createValues(ScrUnitTypeView.@NotNull TraitColumn column) {
        int[] values = new int[TYPE_COUNT];

        for (int type = 0; type < TYPE_COUNT; type++) {
            values[type] = switch (column) {
                case TRAITS -> getTraits(type);
                case UPGRADED_TOP_SPEED -> getUpgradedTopSpeed(type);
                case HANGAR_CAPACITY -> getHangarCapacity(type);
                case TRANSPORT_UPGRADE -> type == ScrUnitIds.ZERG_OVERLORD ? ScrUpgradeIds.VENTRAL_SACS : ScrUpgradeIds.NONE;
                case INITIAL_ENERGY -> type == ScrUnitIds.PROTOSS_SHIELD_BATTERY ? SHIELD_BATTERY_INITIAL_ENERGY
                                                                                 : ScrUnitTypeView.DEFAULT_INITIAL_ENERGY;
                case INITIAL_AMMUNITION -> (getTraits(type) & UnitTraitFlags.VULTURE) != 0 ? VULTURE_SPIDER_MINES : 0;
            };
        }

        return values;
    }

    /**
     * Creates the trait columns of a unit type table parsed outside a game: one new array column per trait column, with the values of
     * {@link #createValues}.
     *
     * @return the created column source for {@link ScrUnitTypeView}
     */
    public static @NotNull Function<ScrUnitTypeView.TraitColumn, @NotNull DatColumn> createColumns() {
        return column -> new ArrayDatColumn(createValues(column));
    }

    private static int getTraits(int type) {
        int traits = 0;

        if (type == ScrUnitIds.TERRAN_GHOST || type == ScrUnitIds.HERO_SARAH_KERRIGAN || type == ScrUnitIds.HERO_ALEXEI_STUKOV ||
            type == ScrUnitIds.HERO_SAMIR_DURAN || type == ScrUnitIds.HERO_INFESTED_DURAN) {
            traits |= UnitTraitFlags.GHOST;
        }

        int morphOffset = type - ScrUnitIds.ZERG_LAIR;

        if (morphOffset >= 0 && morphOffset <= BUILDING_MORPH_LAST_OFFSET && (BUILDING_MORPH_TYPES >>> morphOffset & 1) != 0) {
            traits |= UnitTraitFlags.BUILDING_MORPH_RESULT;
        }

        if (type == ScrUnitIds.PROTOSS_CARRIER || type >= ScrUnitIds.HERO_WARBRINGER && type <= ScrUnitIds.PROTOSS_REAVER) {
            traits |= UnitTraitFlags.HANGAR;
        }

        if (type == ScrUnitIds.PROTOSS_CARRIER || type == ScrUnitIds.HERO_GANTRITHOR) {
            traits |= UnitTraitFlags.INTERCEPTOR_HANGAR;
        }

        if (type == ScrUnitIds.PROTOSS_REAVER || type == ScrUnitIds.HERO_WARBRINGER) {
            traits |= UnitTraitFlags.SCARAB_HANGAR;
        }

        if (type == ScrUnitIds.ZERG_EGG || type == ScrUnitIds.ZERG_COCOON || type == ScrUnitIds.ZERG_LURKER_EGG) {
            traits |= UnitTraitFlags.EGG;
        }

        if (type == ScrUnitIds.TERRAN_REFINERY || type == ScrUnitIds.ZERG_EXTRACTOR || type == ScrUnitIds.PROTOSS_ASSIMILATOR) {
            traits |= UnitTraitFlags.GAS_STRUCTURE;
        }

        int larvaOffset = type - ScrUnitIds.ZERG_LARVA;

        if (larvaOffset >= 0 && larvaOffset <= LARVA_AND_EGG_LAST_OFFSET && (LARVA_AND_EGG_TYPES >> larvaOffset & 1) != 0) {
            traits |= UnitTraitFlags.LARVA_OR_EGG;
        }

        if (type >= ScrUnitIds.RESOURCE_MINERAL_FIELD && type <= ScrUnitIds.RESOURCE_MINERAL_FIELD_TYPE_3) {
            traits |= UnitTraitFlags.MINERAL_FIELD;
        }

        int rallyIndex = type - RALLY_PRODUCER_BASE;

        if (rallyIndex >= 0 && rallyIndex <= RALLY_PRODUCER_LAST_BIT && (RALLY_PRODUCER_MASK >>> rallyIndex & 1) != 0) {
            traits |= UnitTraitFlags.RALLY_PRODUCER;
        }

        if (type >= ScrUnitIds.SPECIAL_ZERG_BEACON && type <= ScrUnitIds.SPECIAL_PROTOSS_FLAG_BEACON) {
            traits |= UnitTraitFlags.BEACON;
        }

        if (type == ScrUnitIds.TERRAN_VULTURE || type == ScrUnitIds.HERO_JIM_RAYNOR_VULTURE) {
            traits |= UnitTraitFlags.VULTURE;
        }

        if (type >= ScrUnitIds.SPECIAL_FLOOR_MISSILE_TRAP && type <= ScrUnitIds.SPECIAL_RIGHT_WALL_FLAME_TRAP) {
            traits |= UnitTraitFlags.MAP_DOODAD;
        }

        if (type == ScrUnitIds.SPECIAL_FLOOR_MISSILE_TRAP ||
            type >= ScrUnitIds.SPECIAL_FLOOR_GUN_TRAP && type <= ScrUnitIds.SPECIAL_RIGHT_WALL_FLAME_TRAP) {
            traits |= UnitTraitFlags.TRAP;
        }

        if (type == ScrUnitIds.SPELL_SCANNER_SWEEP || type == ScrUnitIds.SPECIAL_MAP_REVEALER) {
            traits |= UnitTraitFlags.MAP_REVEALER;
        }

        if (type == ScrUnitIds.PROTOSS_INTERCEPTOR || type == ScrUnitIds.PROTOSS_SCARAB) {
            traits |= UnitTraitFlags.HANGAR_UNIT;
        }

        if (type >= ScrUnitIds.SPECIAL_ZERG_BEACON && type <= ScrUnitIds.SPECIAL_PROTOSS_FLAG_BEACON || type == ScrUnitIds.SPECIAL_FLOOR_HATCH) {
            traits |= UnitTraitFlags.COLLISION_FREE;
        }

        if (type >= ScrUnitIds.SPECIAL_FLOOR_MISSILE_TRAP && type <= ScrUnitIds.SPECIAL_RIGHT_WALL_FLAME_TRAP) {
            traits |= UnitTraitFlags.BUILDING_VISIBILITY;
        }

        if (type == ScrUnitIds.TERRAN_NUCLEAR_MISSILE) {
            traits |= UnitTraitFlags.SIGHTLESS;
        }

        if (type == ScrUnitIds.PROTOSS_ARBITER || type == ScrUnitIds.HERO_DANIMOTH || type == ScrUnitIds.TERRAN_NUCLEAR_MISSILE) {
            traits |= UnitTraitFlags.CLOAK_FIELD_EXEMPT;
        }

        if (type == ScrUnitIds.ZERG_ZERGLING || type == ScrUnitIds.HERO_DEVOURING_ONE) {
            traits |= UnitTraitFlags.COOLDOWN_ORDER_TIMER;
        }

        if (type == ScrUnitIds.PROTOSS_INTERCEPTOR) {
            traits |= UnitTraitFlags.NO_AIR_REPULSION;
        }

        if (type >= ScrUnitIds.SPECIAL_UPPER_LEVEL_DOOR && type <= ScrUnitIds.SPECIAL_RIGHT_PIT_DOOR) {
            traits |= UnitTraitFlags.PATH_TRANSPARENT;
        }

        return traits;
    }

    private static int getUpgradedTopSpeed(int type) {
        return type == ScrUnitIds.PROTOSS_SCOUT || type == ScrUnitIds.HERO_MOJO || type == ScrUnitIds.HERO_ARTANIS ? UPGRADED_SCOUT_SPEED : 0;
    }

    private static int getHangarCapacity(int type) {
        int traits = getTraits(type);

        if ((traits & UnitTraitFlags.INTERCEPTOR_HANGAR) != 0) {
            return CARRIER_CAPACITY;
        }

        return (traits & UnitTraitFlags.SCARAB_HANGAR) != 0 ? REAVER_CAPACITY : 0;
    }


    private ScrUnitTraits() {
    }
}
