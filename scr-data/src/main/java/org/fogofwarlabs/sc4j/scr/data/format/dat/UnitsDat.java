package org.fogofwarlabs.sc4j.scr.data.format.dat;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitTraits;
import org.fogofwarlabs.sc4j.scr.data.type.DatColumn;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;
import java.util.function.IntUnaryOperator;

/**
 * Provides the column schema of {@code arr/units.dat}.
 * <p>
 * The schema has {@value #UNIT_COUNT} unit entries in classic field order. The infestation and addon columns hold {@value #INFESTATION_UNIT_COUNT}
 * entries for the unit ids {@code 106} to {@code 201}, and the sound columns hold {@value #SOUND_UNIT_COUNT} entries for the unit ids {@code 0} to
 * {@code 105}.
 */
public final class UnitsDat {
    /**
     * The number of units.dat entries.
     */
    public static final int UNIT_COUNT = 228;
    /**
     * The number of units.dat entries with ready and annoyed sounds.
     */
    public static final int SOUND_UNIT_COUNT = 106;
    /**
     * The number of units.dat entries with an infestation type, starting at unit {@code 106}.
     */
    public static final int INFESTATION_UNIT_COUNT = 96;
    /**
     * The {@code Graphics} column name.
     */
    public static final @NotNull String FLINGY_TYPE = "Graphics";
    /**
     * The {@code Subunit1} column name.
     */
    public static final @NotNull String FIRST_SUBUNIT = "Subunit1";
    /**
     * The {@code Subunit2} column name.
     */
    public static final @NotNull String SECOND_SUBUNIT = "Subunit2";
    /**
     * The {@code Infestation} column name.
     */
    public static final @NotNull String INFESTED_TYPE = "Infestation";
    /**
     * The {@code Construction Animation} column name.
     */
    public static final @NotNull String CONSTRUCTION_IMAGE = "Construction Animation";
    /**
     * The {@code Unit Direction} column name.
     */
    public static final @NotNull String INITIAL_DIRECTION = "Unit Direction";
    /**
     * The {@code Shield Enable} column name.
     */
    public static final @NotNull String SHIELD_ENABLED = "Shield Enable";
    /**
     * The {@code Shield Amount} column name.
     */
    public static final @NotNull String SHIELD_AMOUNT = "Shield Amount";
    /**
     * The {@code Hit Points} column name.
     */
    public static final @NotNull String HIT_POINTS = "Hit Points";
    /**
     * The {@code Elevation Level} column name.
     */
    public static final @NotNull String ELEVATION_LEVEL = "Elevation Level";
    /**
     * The {@code Unknown} column name.
     */
    public static final @NotNull String UNKNOWN_BYTE = "Unknown";
    /**
     * The {@code Sublabel} column name.
     */
    public static final @NotNull String SUBLABEL = "Sublabel";
    /**
     * The {@code Comp AI Idle} column name.
     */
    public static final @NotNull String COMPUTER_IDLE_ORDER = "Comp AI Idle";
    /**
     * The {@code Human AI Idle} column name.
     */
    public static final @NotNull String HUMAN_IDLE_ORDER = "Human AI Idle";
    /**
     * The {@code Return to Idle} column name.
     */
    public static final @NotNull String RETURN_TO_IDLE_ORDER = "Return to Idle";
    /**
     * The {@code Attack Unit} column name.
     */
    public static final @NotNull String ATTACK_UNIT_ORDER = "Attack Unit";
    /**
     * The {@code Attack Move} column name.
     */
    public static final @NotNull String ATTACK_MOVE_ORDER = "Attack Move";
    /**
     * The {@code Ground Weapon} column name.
     */
    public static final @NotNull String GROUND_WEAPON = "Ground Weapon";
    /**
     * The {@code Max Ground Hits} column name.
     */
    public static final @NotNull String MAXIMUM_GROUND_HITS = "Max Ground Hits";
    /**
     * The {@code Air Weapon} column name.
     */
    public static final @NotNull String AIR_WEAPON = "Air Weapon";
    /**
     * The {@code Max Air Hits} column name.
     */
    public static final @NotNull String MAXIMUM_AIR_HITS = "Max Air Hits";
    /**
     * The {@code AI Internal} column name.
     */
    public static final @NotNull String COMPUTER_INTERNAL_FLAGS = "AI Internal";
    /**
     * The {@code Special Ability Flags} column name.
     */
    public static final @NotNull String FLAGS = "Special Ability Flags";
    /**
     * The {@code Target Acquisition Range} column name.
     */
    public static final @NotNull String TARGET_ACQUISITION_RANGE = "Target Acquisition Range";
    /**
     * The {@code Sight Range} column name.
     */
    public static final @NotNull String SIGHT_RANGE = "Sight Range";
    /**
     * The {@code Armor Upgrade} column name.
     */
    public static final @NotNull String ARMOR_UPGRADE = "Armor Upgrade";
    /**
     * The {@code Unit Size} column name.
     */
    public static final @NotNull String SIZE_CLASS = "Unit Size";
    /**
     * The {@code Armor} column name.
     */
    public static final @NotNull String ARMOR = "Armor";
    /**
     * The {@code Right-click Action} column name.
     */
    public static final @NotNull String RIGHT_CLICK_ACTION = "Right-click Action";
    /**
     * The {@code Ready Sound} column name.
     */
    public static final @NotNull String READY_SOUND = "Ready Sound";
    /**
     * The {@code What Sound Start} column name.
     */
    public static final @NotNull String WHAT_SOUND_START = "What Sound Start";
    /**
     * The {@code What Sound End} column name.
     */
    public static final @NotNull String WHAT_SOUND_END = "What Sound End";
    /**
     * The {@code Piss Sound Start} column name.
     */
    public static final @NotNull String ANNOYED_SOUND_START = "Piss Sound Start";
    /**
     * The {@code Piss Sound End} column name.
     */
    public static final @NotNull String ANNOYED_SOUND_END = "Piss Sound End";
    /**
     * The {@code Yes Sound Start} column name.
     */
    public static final @NotNull String YES_SOUND_START = "Yes Sound Start";
    /**
     * The {@code Yes Sound End} column name.
     */
    public static final @NotNull String YES_SOUND_END = "Yes Sound End";
    /**
     * The {@code Placement Box Width} column name.
     */
    public static final @NotNull String PLACEMENT_WIDTH = "Placement Box Width";
    /**
     * The {@code Placement Box Height} column name.
     */
    public static final @NotNull String PLACEMENT_HEIGHT = "Placement Box Height";
    /**
     * The {@code Addon Horizontal} column name.
     */
    public static final @NotNull String ADDON_HORIZONTAL_OFFSET = "Addon Horizontal";
    /**
     * The {@code Addon Vertical} column name.
     */
    public static final @NotNull String ADDON_VERTICAL_OFFSET = "Addon Vertical";
    /**
     * The {@code Unit Size Left} column name.
     */
    public static final @NotNull String DIMENSION_LEFT = "Unit Size Left";
    /**
     * The {@code Unit Size Up} column name.
     */
    public static final @NotNull String DIMENSION_UP = "Unit Size Up";
    /**
     * The {@code Unit Size Right} column name.
     */
    public static final @NotNull String DIMENSION_RIGHT = "Unit Size Right";
    /**
     * The {@code Unit Size Down} column name.
     */
    public static final @NotNull String DIMENSION_DOWN = "Unit Size Down";
    /**
     * The {@code Portrait} column name.
     */
    public static final @NotNull String PORTRAIT = "Portrait";
    /**
     * The {@code Mineral Cost} column name.
     */
    public static final @NotNull String MINERAL_COST = "Mineral Cost";
    /**
     * The {@code Vespene Cost} column name.
     */
    public static final @NotNull String GAS_COST = "Vespene Cost";
    /**
     * The {@code Build Time} column name.
     */
    public static final @NotNull String BUILD_TIME = "Build Time";
    /**
     * The {@code Requirement Index} column name.
     */
    public static final @NotNull String REQUIREMENT_INDEX = "Requirement Index";
    /**
     * The {@code StarEdit Group Flags} column name.
     */
    public static final @NotNull String GROUP_FLAGS = "StarEdit Group Flags";
    /**
     * The {@code Supply Provided} column name.
     */
    public static final @NotNull String SUPPLY_PROVIDED = "Supply Provided";
    /**
     * The {@code Supply Required} column name.
     */
    public static final @NotNull String SUPPLY_REQUIRED = "Supply Required";
    /**
     * The {@code Space Required} column name.
     */
    public static final @NotNull String SPACE_REQUIRED = "Space Required";
    /**
     * The {@code Space Provided} column name.
     */
    public static final @NotNull String SPACE_PROVIDED = "Space Provided";
    /**
     * The {@code Build Score} column name.
     */
    public static final @NotNull String BUILD_SCORE = "Build Score";
    /**
     * The {@code Destroy Score} column name.
     */
    public static final @NotNull String DESTROY_SCORE = "Destroy Score";
    /**
     * The {@code Unit Map String} column name.
     */
    public static final @NotNull String MAP_STRING_INDEX = "Unit Map String";
    /**
     * The {@code Broodwar Unit Flag} column name.
     */
    public static final @NotNull String BROOD_WAR_FLAG = "Broodwar Unit Flag";
    /**
     * The {@code StarEdit Availability Flags} column name.
     */
    public static final @NotNull String AVAILABILITY_FLAGS = "StarEdit Availability Flags";
    /**
     * The subunit entry of a unit type without a subunit: the entry count, the id of no unit type.
     */
    private static final int NO_SUBUNIT = UNIT_COUNT;
    /**
     * The shift from a weapon range in pixels to the target acquisition range in tiles of 32 pixels.
     */
    private static final int RANGE_TO_TILES_SHIFT = 5;
    /**
     * The mask that keeps the unsigned 8-bit width of a stored target acquisition range.
     */
    private static final int BYTE_MASK = 0xFF;
    /**
     * The alignment of the units.dat column arrays in the game's memory, where they follow each other in file order.
     */
    private static final int NATIVE_BLOCK_ALIGNMENT = 16;

    /**
     * Creates a {@link DatSchema} instance for {@code arr/units.dat}.
     *
     * @return the created {@link DatSchema} instance
     */
    public static @NotNull DatSchema createSchema() {
        return new DatSchema()
                .alignNativeBlocks(NATIVE_BLOCK_ALIGNMENT)
                .fieldU8(FLINGY_TYPE, UNIT_COUNT)
                .fieldU16(FIRST_SUBUNIT, UNIT_COUNT)
                .fieldU16(SECOND_SUBUNIT, UNIT_COUNT)
                .fieldU16(INFESTED_TYPE, INFESTATION_UNIT_COUNT)
                .fieldU32(CONSTRUCTION_IMAGE, UNIT_COUNT)
                .fieldU8(INITIAL_DIRECTION, UNIT_COUNT)
                .fieldU8(SHIELD_ENABLED, UNIT_COUNT)
                .fieldU16(SHIELD_AMOUNT, UNIT_COUNT)
                .fieldU32(HIT_POINTS, UNIT_COUNT)
                .fieldU8(ELEVATION_LEVEL, UNIT_COUNT)
                .fieldU8(UNKNOWN_BYTE, UNIT_COUNT)
                .fieldU8(SUBLABEL, UNIT_COUNT)
                .fieldU8(COMPUTER_IDLE_ORDER, UNIT_COUNT)
                .fieldU8(HUMAN_IDLE_ORDER, UNIT_COUNT)
                .fieldU8(RETURN_TO_IDLE_ORDER, UNIT_COUNT)
                .fieldU8(ATTACK_UNIT_ORDER, UNIT_COUNT)
                .fieldU8(ATTACK_MOVE_ORDER, UNIT_COUNT)
                .fieldU8(GROUND_WEAPON, UNIT_COUNT)
                .fieldU8(MAXIMUM_GROUND_HITS, UNIT_COUNT)
                .fieldU8(AIR_WEAPON, UNIT_COUNT)
                .fieldU8(MAXIMUM_AIR_HITS, UNIT_COUNT)
                .fieldU8(COMPUTER_INTERNAL_FLAGS, UNIT_COUNT)
                .fieldU32(FLAGS, UNIT_COUNT)
                .fieldU8(TARGET_ACQUISITION_RANGE, UNIT_COUNT)
                .fieldU8(SIGHT_RANGE, UNIT_COUNT)
                .fieldU8(ARMOR_UPGRADE, UNIT_COUNT)
                .fieldU8(SIZE_CLASS, UNIT_COUNT)
                .fieldU8(ARMOR, UNIT_COUNT)
                .fieldU8(RIGHT_CLICK_ACTION, UNIT_COUNT)
                .fieldU16(READY_SOUND, SOUND_UNIT_COUNT)
                .fieldU16(WHAT_SOUND_START, UNIT_COUNT)
                .fieldU16(WHAT_SOUND_END, UNIT_COUNT)
                .fieldU16(ANNOYED_SOUND_START, SOUND_UNIT_COUNT)
                .fieldU16(ANNOYED_SOUND_END, SOUND_UNIT_COUNT)
                .fieldU16(YES_SOUND_START, SOUND_UNIT_COUNT)
                .fieldU16(YES_SOUND_END, SOUND_UNIT_COUNT)
                .fieldU16x2(PLACEMENT_WIDTH, PLACEMENT_HEIGHT, UNIT_COUNT)
                .fieldU16x2(ADDON_HORIZONTAL_OFFSET, ADDON_VERTICAL_OFFSET, INFESTATION_UNIT_COUNT)
                .fieldU16x4(DIMENSION_LEFT, DIMENSION_UP, DIMENSION_RIGHT, DIMENSION_DOWN, UNIT_COUNT)
                .fieldU16(PORTRAIT, UNIT_COUNT)
                .fieldU16(MINERAL_COST, UNIT_COUNT)
                .fieldU16(GAS_COST, UNIT_COUNT)
                .fieldU16(BUILD_TIME, UNIT_COUNT)
                .fieldU16(REQUIREMENT_INDEX, UNIT_COUNT)
                .fieldU8(GROUP_FLAGS, UNIT_COUNT)
                .fieldU8(SUPPLY_PROVIDED, UNIT_COUNT)
                .fieldU8(SUPPLY_REQUIRED, UNIT_COUNT)
                .fieldU8(SPACE_REQUIRED, UNIT_COUNT)
                .fieldU8(SPACE_PROVIDED, UNIT_COUNT)
                .fieldU16(BUILD_SCORE, UNIT_COUNT)
                .fieldU16(DESTROY_SCORE, UNIT_COUNT)
                .fieldU16(MAP_STRING_INDEX, UNIT_COUNT)
                .fieldU8(BROOD_WAR_FLAG, UNIT_COUNT)
                .fieldU16(AVAILABILITY_FLAGS, UNIT_COUNT);
    }

    /**
     * Initializes target acquisition ranges from subunit and weapon ranges.
     * <p>
     * Each entry reads the current subunit range before its ascending, in-place update. Weapon ranges are unsigned 32-bit values; the result is
     * stored as an unsigned 8-bit value.
     *
     * @param units   the parsed units.dat table to initialize
     * @param weapons the parsed weapons.dat table
     * @throws UnsupportedOperationException if a subunit index is outside units.dat
     */
    public static void initializeTargetAcquisitionRanges(@NotNull DatTable units, @NotNull DatTable weapons) {
        DatColumn subunits = units.getColumn(FIRST_SUBUNIT);
        DatColumn groundWeapons = units.getColumn(GROUND_WEAPON);
        DatColumn airWeapons = units.getColumn(AIR_WEAPON);
        DatColumn acquisitionRanges = units.getColumn(TARGET_ACQUISITION_RANGE);
        DatColumn maximumRanges = weapons.getColumn(WeaponsDat.MAXIMUM_RANGE);

        for (int unitIndex = 0; unitIndex < UNIT_COUNT; unitIndex++) {
            int sourceIndex = subunits.get(unitIndex) == NO_SUBUNIT ? unitIndex : subunits.get(unitIndex);

            if (sourceIndex < 0 || sourceIndex >= UNIT_COUNT) {
                throw Exceptions.newUnsupportedOperationException("Subunit {0} lies outside the units.dat types.", sourceIndex);
            }

            int acquisitionRange = acquisitionRanges.get(sourceIndex);
            int groundWeapon = groundWeapons.get(sourceIndex);
            int airWeapon = airWeapons.get(sourceIndex);

            if (groundWeapon < WeaponsDat.WEAPON_COUNT) {
                acquisitionRange = Math.max(acquisitionRange, maximumRanges.get(groundWeapon) >>> RANGE_TO_TILES_SHIFT);
            }

            if (airWeapon < WeaponsDat.WEAPON_COUNT) {
                acquisitionRange = Math.max(acquisitionRange, maximumRanges.get(airWeapon) >>> RANGE_TO_TILES_SHIFT);
            }

            acquisitionRanges.set(unitIndex, acquisitionRange & BYTE_MASK);
        }
    }

    /**
     * Creates a {@link ScrUnitTypeView} instance over the columns of a parsed table with the trait columns of the installed content
     * ({@link ScrUnitTraits#createColumns()}).
     *
     * @param units the parsed {@code units.dat} table
     * @return the created {@link ScrUnitTypeView} instance
     */
    public static @NotNull ScrUnitTypeView createTypeTable(@NotNull DatTable units) {
        return createTypeTable(units, ScrUnitTraits.createColumns());
    }

    /**
     * Creates a {@link ScrUnitTypeView} instance over the columns of a parsed table; later writes to the table (EUD, map settings, requirement
     * indices) are visible through it. An id outside a column reads the game's memory around the column
     * ({@link DatTable#readNativeElement(String, int)}). A column the table lacks (a partial schema) reads as an empty column, whose accessors throw
     * {@link IllegalArgumentException} for every id.
     *
     * @param units        the parsed {@code units.dat} table
     * @param traitColumns the trait columns of the view
     * @return the created {@link ScrUnitTypeView} instance
     */
    public static @NotNull ScrUnitTypeView createTypeTable(@NotNull DatTable units,
                                                           @NotNull Function<ScrUnitTypeView.TraitColumn, @NotNull DatColumn> traitColumns) {
        return new ScrUnitTypeView(column -> units.getColumnOrEmpty(getColumnName(column)),
                                   traitColumns,
                                   REQUIREMENT_INDEX,
                                   (column, index) -> units.readNativeElement(getColumnName(column), index));
    }

    /**
     * Creates a {@link ScrUnitTypeView} instance over the columns of a game's table and the columns of its added unit types: the ids from
     * {@code 233} on that name an added type read the added columns, every other id reads as {@link #createTypeTable(DatTable, Function)} reads it.
     *
     * @param units             the game's {@code units.dat} table
     * @param traitColumns      the trait columns of the view
     * @param addedColumns      the added column of each {@code units.dat} field, by field name
     * @param addedTraitColumns the added trait columns of the view
     * @param addedBaseTypes    the native unit type id of the base type of each added type id
     * @return the created {@link ScrUnitTypeView} instance
     */
    public static @NotNull ScrUnitTypeView createTypeTable(@NotNull DatTable units,
                                                           @NotNull Function<ScrUnitTypeView.TraitColumn, @NotNull DatColumn> traitColumns,
                                                           @NotNull Function<String, @NotNull DatColumn> addedColumns,
                                                           @NotNull Function<ScrUnitTypeView.TraitColumn, @NotNull DatColumn> addedTraitColumns,
                                                           @NotNull IntUnaryOperator addedBaseTypes) {
        return new ScrUnitTypeView(column -> units.getColumnOrEmpty(getColumnName(column)),
                                   traitColumns,
                                   column -> addedColumns.apply(getColumnName(column)),
                                   addedTraitColumns,
                                   addedBaseTypes,
                                   REQUIREMENT_INDEX,
                                   (column, index) -> units.readNativeElement(getColumnName(column), index));
    }

    /**
     * @param column a column of the unit type table
     * @return the units.dat field name of the column
     */
    private static @NotNull String getColumnName(ScrUnitTypeView.@NotNull Column column) {
        return switch (column) {
            case FLINGY_TYPE -> FLINGY_TYPE;
            case FIRST_SUBUNIT -> FIRST_SUBUNIT;
            case SECOND_SUBUNIT -> SECOND_SUBUNIT;
            case INFESTED_TYPE -> INFESTED_TYPE;
            case CONSTRUCTION_IMAGE -> CONSTRUCTION_IMAGE;
            case INITIAL_DIRECTION -> INITIAL_DIRECTION;
            case SHIELD_ENABLED -> SHIELD_ENABLED;
            case SHIELD_AMOUNT -> SHIELD_AMOUNT;
            case HIT_POINTS -> HIT_POINTS;
            case ELEVATION_LEVEL -> ELEVATION_LEVEL;
            case UNKNOWN_BYTE -> UNKNOWN_BYTE;
            case SUBLABEL -> SUBLABEL;
            case COMPUTER_IDLE_ORDER -> COMPUTER_IDLE_ORDER;
            case HUMAN_IDLE_ORDER -> HUMAN_IDLE_ORDER;
            case RETURN_TO_IDLE_ORDER -> RETURN_TO_IDLE_ORDER;
            case ATTACK_UNIT_ORDER -> ATTACK_UNIT_ORDER;
            case ATTACK_MOVE_ORDER -> ATTACK_MOVE_ORDER;
            case GROUND_WEAPON -> GROUND_WEAPON;
            case MAXIMUM_GROUND_HITS -> MAXIMUM_GROUND_HITS;
            case AIR_WEAPON -> AIR_WEAPON;
            case MAXIMUM_AIR_HITS -> MAXIMUM_AIR_HITS;
            case COMPUTER_INTERNAL_FLAGS -> COMPUTER_INTERNAL_FLAGS;
            case FLAGS -> FLAGS;
            case TARGET_ACQUISITION_RANGE -> TARGET_ACQUISITION_RANGE;
            case SIGHT_RANGE -> SIGHT_RANGE;
            case ARMOR_UPGRADE -> ARMOR_UPGRADE;
            case SIZE_CLASS -> SIZE_CLASS;
            case ARMOR -> ARMOR;
            case RIGHT_CLICK_ACTION -> RIGHT_CLICK_ACTION;
            case READY_SOUND -> READY_SOUND;
            case WHAT_SOUND_START -> WHAT_SOUND_START;
            case WHAT_SOUND_END -> WHAT_SOUND_END;
            case ANNOYED_SOUND_START -> ANNOYED_SOUND_START;
            case ANNOYED_SOUND_END -> ANNOYED_SOUND_END;
            case YES_SOUND_START -> YES_SOUND_START;
            case YES_SOUND_END -> YES_SOUND_END;
            case PLACEMENT_WIDTH -> PLACEMENT_WIDTH;
            case PLACEMENT_HEIGHT -> PLACEMENT_HEIGHT;
            case ADDON_HORIZONTAL_OFFSET -> ADDON_HORIZONTAL_OFFSET;
            case ADDON_VERTICAL_OFFSET -> ADDON_VERTICAL_OFFSET;
            case DIMENSION_LEFT -> DIMENSION_LEFT;
            case DIMENSION_UP -> DIMENSION_UP;
            case DIMENSION_RIGHT -> DIMENSION_RIGHT;
            case DIMENSION_DOWN -> DIMENSION_DOWN;
            case PORTRAIT -> PORTRAIT;
            case MINERAL_COST -> MINERAL_COST;
            case GAS_COST -> GAS_COST;
            case BUILD_TIME -> BUILD_TIME;
            case REQUIREMENT_INDEX -> REQUIREMENT_INDEX;
            case GROUP_FLAGS -> GROUP_FLAGS;
            case SUPPLY_PROVIDED -> SUPPLY_PROVIDED;
            case SUPPLY_REQUIRED -> SUPPLY_REQUIRED;
            case SPACE_REQUIRED -> SPACE_REQUIRED;
            case SPACE_PROVIDED -> SPACE_PROVIDED;
            case BUILD_SCORE -> BUILD_SCORE;
            case DESTROY_SCORE -> DESTROY_SCORE;
            case MAP_STRING_INDEX -> MAP_STRING_INDEX;
            case BROOD_WAR_FLAG -> BROOD_WAR_FLAG;
            case AVAILABILITY_FLAGS -> AVAILABILITY_FLAGS;
        };
    }


    private UnitsDat() {
    }
}
