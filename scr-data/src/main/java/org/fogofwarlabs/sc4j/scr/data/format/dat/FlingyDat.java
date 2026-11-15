package org.fogofwarlabs.sc4j.scr.data.format.dat;

import org.fogofwarlabs.sc4j.scr.data.type.DatColumn;
import org.fogofwarlabs.sc4j.scr.data.type.ScrFlingyTypeView;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Provides the column schema of {@code arr/flingy.dat} (209 entries, 3,135 bytes).
 */
public final class FlingyDat {
    /**
     * The number of flingy.dat entries.
     */
    public static final int FLINGY_COUNT = 209;
    /**
     * The {@code Sprite} column name.
     */
    public static final @NotNull String SPRITE = "Sprite";
    /**
     * The {@code Top Speed} column name.
     */
    public static final @NotNull String TOP_SPEED = "Top Speed";
    /**
     * The {@code Acceleration} column name.
     */
    public static final @NotNull String ACCELERATION = "Acceleration";
    /**
     * The {@code Halt Distance} column name.
     */
    public static final @NotNull String HALT_DISTANCE = "Halt Distance";
    /**
     * The {@code Turn Rate} column name.
     */
    public static final @NotNull String TURN_RATE = "Turn Rate";
    /**
     * The {@code Unused} column name.
     */
    public static final @NotNull String UNUSED = "Unused";
    /**
     * The {@code Movement Control} column name.
     */
    public static final @NotNull String MOVEMENT_CONTROL = "Movement Control";
    /**
     * The alignment of the flingy.dat column arrays in the game's memory, where they follow each other in file order.
     */
    private static final int NATIVE_BLOCK_ALIGNMENT = 16;

    /**
     * Creates a {@link DatSchema} instance for {@code arr/flingy.dat}.
     *
     * @return the created {@link DatSchema} instance
     */
    public static @NotNull DatSchema createSchema() {
        return new DatSchema()
                .alignNativeBlocks(NATIVE_BLOCK_ALIGNMENT)
                .fieldU16(SPRITE, FLINGY_COUNT)
                .fieldU32(TOP_SPEED, FLINGY_COUNT)
                .fieldU16(ACCELERATION, FLINGY_COUNT)
                .fieldU32(HALT_DISTANCE, FLINGY_COUNT)
                .fieldU8(TURN_RATE, FLINGY_COUNT)
                .fieldU8(UNUSED, FLINGY_COUNT)
                .fieldU8(MOVEMENT_CONTROL, FLINGY_COUNT);
    }

    /**
     * Creates a {@link ScrFlingyTypeView} instance over the columns of a parsed table; later writes to the table (EUD) are visible through it. An id
     * outside a column reads the game's memory around the column ({@link DatTable#readNativeElement(String, int)}). A column the table lacks (a
     * partial schema) reads as an empty column, whose accessors throw {@link IllegalArgumentException} for every id.
     *
     * @param flingy the parsed {@code flingy.dat} table
     * @return the created {@link ScrFlingyTypeView} instance
     */
    public static @NotNull ScrFlingyTypeView createTypeTable(@NotNull DatTable flingy) {
        return new ScrFlingyTypeView(column -> flingy.getColumnOrEmpty(getColumnName(column)),
                                     (column, index) -> flingy.readNativeElement(getColumnName(column), index));
    }

    /**
     * Creates a {@link ScrFlingyTypeView} instance over the columns of a game's table and the added columns of its added entries: an id past a native
     * column whose element lies in the added columns reads the added column, every other id reads as {@link #createTypeTable(DatTable)} reads it.
     * Without added entries the view is the native one.
     *
     * @param flingy       the game's {@code flingy.dat} table
     * @param addedColumns the added column of each {@code flingy.dat} field, by field name, one element per added entry
     * @return the created {@link ScrFlingyTypeView} instance
     */
    public static @NotNull ScrFlingyTypeView createTypeTable(@NotNull DatTable flingy, @NotNull Function<String, @NotNull DatColumn> addedColumns) {
        Map<ScrFlingyTypeView.Column, DatColumn> added = new EnumMap<>(ScrFlingyTypeView.Column.class);

        for (ScrFlingyTypeView.Column column : ScrFlingyTypeView.Column.values()) {
            added.put(column, addedColumns.apply(getColumnName(column)));
        }

        return new ScrFlingyTypeView(column -> flingy.getColumnOrEmpty(getColumnName(column)), (column, element) -> {
            DatColumn values = added.get(column);
            int addedElement = element - flingy.getColumnOrEmpty(getColumnName(column)).getLength();

            return addedElement >= 0 && addedElement < values.getLength()
                   ? values.get(addedElement)
                   : flingy.readNativeElement(getColumnName(column), element);
        });
    }

    /**
     * @param column a column of the flingy type table
     * @return the flingy.dat field name of the column
     */
    private static @NotNull String getColumnName(ScrFlingyTypeView.@NotNull Column column) {
        return switch (column) {
            case SPRITE -> SPRITE;
            case TOP_SPEED -> TOP_SPEED;
            case ACCELERATION -> ACCELERATION;
            case HALT_DISTANCE -> HALT_DISTANCE;
            case TURN_RATE -> TURN_RATE;
            case MOVEMENT_CONTROL -> MOVEMENT_CONTROL;
        };
    }


    private FlingyDat() {
    }
}
