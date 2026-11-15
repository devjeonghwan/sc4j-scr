package org.fogofwarlabs.sc4j.scr.data.format.dat;

import org.fogofwarlabs.sc4j.scr.data.type.ScrTechTypeView;
import org.jetbrains.annotations.NotNull;

/**
 * Provides the column schema of {@code arr/techdata.dat} (44 entries, 836 bytes).
 */
public final class TechdataDat {
    /**
     * The number of techdata.dat entries.
     */
    public static final int TECH_COUNT = 44;
    /**
     * The {@code Ore Cost} column name.
     */
    public static final @NotNull String MINERAL_COST = "Ore Cost";
    /**
     * The {@code Gas Cost} column name.
     */
    public static final @NotNull String GAS_COST = "Gas Cost";
    /**
     * The {@code Time Cost} column name.
     */
    public static final @NotNull String RESEARCH_TIME = "Time Cost";
    /**
     * The {@code Energy} column name.
     */
    public static final @NotNull String ENERGY_COST = "Energy";
    /**
     * The {@code DatReqs Index} column name.
     */
    public static final @NotNull String RESEARCH_REQUIREMENT_INDEX = "DatReqs Index";
    /**
     * The {@code UseReqs Index} column name.
     */
    public static final @NotNull String USE_REQUIREMENT_INDEX = "UseReqs Index";
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
     * The {@code Researched} column name.
     */
    public static final @NotNull String RESEARCHED_FLAG = "Researched";
    /**
     * The {@code BroodWar} column name.
     */
    public static final @NotNull String BROOD_WAR_FLAG = "BroodWar";
    /**
     * The alignment of the techdata.dat column arrays in the game's memory, where they follow each other in file order.
     */
    private static final int NATIVE_BLOCK_ALIGNMENT = 16;
    /**
     * The alignment of the 8-bit column arrays of techdata.dat in the game's memory.
     */
    private static final int NATIVE_BYTE_BLOCK_ALIGNMENT = 8;

    /**
     * Creates a {@link DatSchema} instance for {@code arr/techdata.dat}.
     *
     * @return the created {@link DatSchema} instance
     */
    public static @NotNull DatSchema createSchema() {
        return new DatSchema()
                .alignNativeBlocks(NATIVE_BLOCK_ALIGNMENT)
                .fieldU16(MINERAL_COST, TECH_COUNT)
                .fieldU16(GAS_COST, TECH_COUNT)
                .fieldU16(RESEARCH_TIME, TECH_COUNT)
                .fieldU16(ENERGY_COST, TECH_COUNT)
                .fieldU16(RESEARCH_REQUIREMENT_INDEX, TECH_COUNT)
                .fieldU16(USE_REQUIREMENT_INDEX, TECH_COUNT)
                .fieldU16(ICON, TECH_COUNT)
                .fieldU16(LABEL, TECH_COUNT)
                .fieldU8(RACE, TECH_COUNT)
                .fieldU8(RESEARCHED_FLAG, TECH_COUNT)
                .fieldU8(BROOD_WAR_FLAG, TECH_COUNT)
                .alignNativeBlock(RACE, NATIVE_BYTE_BLOCK_ALIGNMENT)
                .alignNativeBlock(RESEARCHED_FLAG, NATIVE_BYTE_BLOCK_ALIGNMENT)
                .alignNativeBlock(BROOD_WAR_FLAG, NATIVE_BYTE_BLOCK_ALIGNMENT);
    }

    /**
     * Creates a {@link ScrTechTypeView} instance over the columns of a parsed table; later writes to the table (EUD, map settings, requirement
     * indices) are visible through it. An id outside a column reads the game's memory around the column
     * ({@link DatTable#readNativeElement(String, int)}). A column the table lacks (a partial schema) reads as an empty column, whose accessors throw
     * {@link IllegalArgumentException} for every id.
     *
     * @param techdata the parsed {@code techdata.dat} table
     * @return the created {@link ScrTechTypeView} instance
     */
    public static @NotNull ScrTechTypeView createTypeTable(@NotNull DatTable techdata) {
        return new ScrTechTypeView(column -> techdata.getColumnOrEmpty(getColumnName(column)),
                                   RESEARCH_REQUIREMENT_INDEX, USE_REQUIREMENT_INDEX,
                                   (column, index) -> techdata.readNativeElement(getColumnName(column), index));
    }

    /**
     * @param column a column of the tech type table
     * @return the techdata.dat field name of the column
     */
    private static @NotNull String getColumnName(ScrTechTypeView.@NotNull Column column) {
        return switch (column) {
            case MINERAL_COST -> MINERAL_COST;
            case GAS_COST -> GAS_COST;
            case RESEARCH_TIME -> RESEARCH_TIME;
            case ENERGY_COST -> ENERGY_COST;
            case RESEARCH_REQUIREMENT_INDEX -> RESEARCH_REQUIREMENT_INDEX;
            case USE_REQUIREMENT_INDEX -> USE_REQUIREMENT_INDEX;
            case ICON -> ICON;
            case LABEL -> LABEL;
            case RACE -> RACE;
            case RESEARCHED_FLAG -> RESEARCHED_FLAG;
            case BROOD_WAR_FLAG -> BROOD_WAR_FLAG;
        };
    }


    private TechdataDat() {
    }
}
