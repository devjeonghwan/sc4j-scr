package org.fogofwarlabs.sc4j.scr.data.format.dat;

import org.fogofwarlabs.sc4j.scr.data.type.ScrUpgradeTypeView;
import org.jetbrains.annotations.NotNull;

/**
 * Provides the column schema of {@code arr/upgrades.dat} (61 entries, 1,281 bytes).
 */
public final class UpgradesDat {
    /**
     * The number of upgrades.dat entries.
     */
    public static final int UPGRADE_COUNT = 61;
    /**
     * The {@code Ore Cost} column name.
     */
    public static final @NotNull String MINERAL_COST = "Ore Cost";
    /**
     * The {@code Ore Cost Factor} column name.
     */
    public static final @NotNull String MINERAL_COST_FACTOR = "Ore Cost Factor";
    /**
     * The {@code Gas Cost} column name.
     */
    public static final @NotNull String GAS_COST = "Gas Cost";
    /**
     * The {@code Gas Cost Factor} column name.
     */
    public static final @NotNull String GAS_COST_FACTOR = "Gas Cost Factor";
    /**
     * The {@code Time Cost} column name.
     */
    public static final @NotNull String RESEARCH_TIME = "Time Cost";
    /**
     * The {@code Time Cost Factor} column name.
     */
    public static final @NotNull String RESEARCH_TIME_FACTOR = "Time Cost Factor";
    /**
     * The {@code DatReqs Index} column name.
     */
    public static final @NotNull String REQUIREMENT_INDEX = "DatReqs Index";
    /**
     * The {@code Icon} column name.
     */
    public static final @NotNull String ICON = "Icon";
    /**
     * The {@code Label} column name.
     */
    public static final @NotNull String LABEL = "Label";
    /**
     * The {@code Race} column name.
     */
    public static final @NotNull String RACE = "Race";
    /**
     * The {@code Max Repeats} column name.
     */
    public static final @NotNull String MAXIMUM_LEVEL = "Max Repeats";
    /**
     * The {@code BroodWar} column name.
     */
    public static final @NotNull String BROOD_WAR_FLAG = "BroodWar";
    /**
     * The alignment of the upgrades.dat column arrays in the game's memory, where they follow each other in file order.
     */
    private static final int NATIVE_BLOCK_ALIGNMENT = 16;

    /**
     * Creates a {@link DatSchema} instance for {@code arr/upgrades.dat}.
     *
     * @return the created {@link DatSchema} instance
     */
    public static @NotNull DatSchema createSchema() {
        return new DatSchema()
                .alignNativeBlocks(NATIVE_BLOCK_ALIGNMENT)
                .fieldU16(MINERAL_COST, UPGRADE_COUNT)
                .fieldU16(MINERAL_COST_FACTOR, UPGRADE_COUNT)
                .fieldU16(GAS_COST, UPGRADE_COUNT)
                .fieldU16(GAS_COST_FACTOR, UPGRADE_COUNT)
                .fieldU16(RESEARCH_TIME, UPGRADE_COUNT)
                .fieldU16(RESEARCH_TIME_FACTOR, UPGRADE_COUNT)
                .fieldU16(REQUIREMENT_INDEX, UPGRADE_COUNT)
                .fieldU16(ICON, UPGRADE_COUNT)
                .fieldU16(LABEL, UPGRADE_COUNT)
                .fieldU8(RACE, UPGRADE_COUNT)
                .fieldU8(MAXIMUM_LEVEL, UPGRADE_COUNT)
                .fieldU8(BROOD_WAR_FLAG, UPGRADE_COUNT);
    }

    /**
     * Creates a {@link ScrUpgradeTypeView} instance over the columns of a parsed table; later writes to the table (EUD, map settings, requirement
     * indices) are visible through it. An id outside a column reads the game's memory around the column
     * ({@link DatTable#readNativeElement(String, int)}). A column the table lacks (a partial schema) reads as an empty column, whose accessors throw
     * {@link IllegalArgumentException} for every id.
     *
     * @param upgrades the parsed {@code upgrades.dat} table
     * @return the created {@link ScrUpgradeTypeView} instance
     */
    public static @NotNull ScrUpgradeTypeView createTypeTable(@NotNull DatTable upgrades) {
        return new ScrUpgradeTypeView(column -> upgrades.getColumnOrEmpty(getColumnName(column)),
                                      REQUIREMENT_INDEX,
                                      (column, index) -> upgrades.readNativeElement(getColumnName(column), index));
    }

    /**
     * @param column a column of the upgrade type table
     * @return the upgrades.dat field name of the column
     */
    private static @NotNull String getColumnName(ScrUpgradeTypeView.@NotNull Column column) {
        return switch (column) {
            case MINERAL_COST -> MINERAL_COST;
            case MINERAL_COST_FACTOR -> MINERAL_COST_FACTOR;
            case GAS_COST -> GAS_COST;
            case GAS_COST_FACTOR -> GAS_COST_FACTOR;
            case RESEARCH_TIME -> RESEARCH_TIME;
            case RESEARCH_TIME_FACTOR -> RESEARCH_TIME_FACTOR;
            case REQUIREMENT_INDEX -> REQUIREMENT_INDEX;
            case ICON -> ICON;
            case LABEL -> LABEL;
            case RACE -> RACE;
            case MAXIMUM_LEVEL -> MAXIMUM_LEVEL;
            case BROOD_WAR_FLAG -> BROOD_WAR_FLAG;
        };
    }


    private UpgradesDat() {
    }
}
