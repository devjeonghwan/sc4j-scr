package org.fogofwarlabs.sc4j.scr.data.format.dat;

import org.fogofwarlabs.sc4j.scr.data.type.PortraitTypeTable;
import org.jetbrains.annotations.NotNull;

/**
 * Provides the column schema of {@code arr/portdata.dat} (110 entries, 1,320 bytes).
 */
public final class PortdataDat {
    /**
     * The number of portdata.dat entries.
     */
    public static final int PORTRAIT_COUNT = 110;
    /**
     * The {@code Idle Dir} column name.
     */
    public static final @NotNull String IDLE_DIRECTORY = "Idle Dir";
    /**
     * The {@code Talking Dir} column name.
     */
    public static final @NotNull String TALKING_DIRECTORY = "Talking Dir";
    /**
     * The {@code Idle Change 0} column name.
     */
    public static final @NotNull String FIRST_IDLE_CHANGE = "Idle Change 0";
    /**
     * The {@code Idle Change 1} column name.
     */
    public static final @NotNull String SECOND_IDLE_CHANGE = "Idle Change 1";
    /**
     * The {@code Idle Change 2} column name.
     */
    public static final @NotNull String THIRD_IDLE_CHANGE = "Idle Change 2";
    /**
     * The {@code Idle Change 3} column name.
     */
    public static final @NotNull String FOURTH_IDLE_CHANGE = "Idle Change 3";
    /**
     * The alignment of the portdata.dat column arrays in the game's memory, where they follow each other in file order.
     */
    private static final int NATIVE_BLOCK_ALIGNMENT = 16;

    /**
     * Creates a {@link DatSchema} instance for {@code arr/portdata.dat}.
     *
     * @return the created {@link DatSchema} instance
     */
    public static @NotNull DatSchema createSchema() {
        return new DatSchema()
                .alignNativeBlocks(NATIVE_BLOCK_ALIGNMENT)
                .fieldU32(IDLE_DIRECTORY, PORTRAIT_COUNT)
                .fieldU32(TALKING_DIRECTORY, PORTRAIT_COUNT)
                .fieldU8(FIRST_IDLE_CHANGE, PORTRAIT_COUNT)
                .fieldU8(SECOND_IDLE_CHANGE, PORTRAIT_COUNT)
                .fieldU8(THIRD_IDLE_CHANGE, PORTRAIT_COUNT)
                .fieldU8(FOURTH_IDLE_CHANGE, PORTRAIT_COUNT)
                .holdNativeReferences(IDLE_DIRECTORY)
                .holdNativeReferences(TALKING_DIRECTORY);
    }

    /**
     * Creates a {@link PortraitTypeTable} instance over the columns of a parsed table; later writes to the table (EUD) are visible through it. An id
     * outside a column reads the game's memory around the column ({@link DatTable#readNativeElement(String, int)}). A column the table lacks (a
     * partial schema) reads as an empty column, whose accessors throw {@link IllegalArgumentException} for every id.
     *
     * @param portdata the parsed {@code portdata.dat} table
     * @return the created {@link PortraitTypeTable} instance
     */
    public static @NotNull PortraitTypeTable createTypeTable(@NotNull DatTable portdata) {
        return new PortraitTypeTable(column -> portdata.getColumnOrEmpty(getColumnName(column)),
                                     (column, index) -> portdata.readNativeElement(getColumnName(column), index));
    }

    /**
     * @param column a column of the portrait type table
     * @return the portdata.dat field name of the column
     */
    private static @NotNull String getColumnName(PortraitTypeTable.@NotNull Column column) {
        return switch (column) {
            case IDLE_DIRECTORY -> IDLE_DIRECTORY;
            case TALKING_DIRECTORY -> TALKING_DIRECTORY;
            case FIRST_IDLE_CHANGE -> FIRST_IDLE_CHANGE;
            case SECOND_IDLE_CHANGE -> SECOND_IDLE_CHANGE;
            case THIRD_IDLE_CHANGE -> THIRD_IDLE_CHANGE;
            case FOURTH_IDLE_CHANGE -> FOURTH_IDLE_CHANGE;
        };
    }


    private PortdataDat() {
    }
}
