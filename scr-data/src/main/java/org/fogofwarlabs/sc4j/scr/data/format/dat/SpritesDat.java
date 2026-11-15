package org.fogofwarlabs.sc4j.scr.data.format.dat;

import org.fogofwarlabs.sc4j.scr.data.type.DatColumn;
import org.fogofwarlabs.sc4j.scr.data.type.ScrSpriteTypeView;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Provides the column schema of {@code arr/sprites.dat} (517 entries, 3,229 bytes).
 * <p>
 * The health bar, selection circle, and Y position columns hold 387 entries.
 */
public final class SpritesDat {
    /**
     * The number of sprites.dat entries.
     */
    public static final int SPRITE_COUNT = 517;
    /**
     * The number of sprites.dat entries with a health bar and selection circle.
     */
    public static final int BAR_SPRITE_COUNT = 387;
    /**
     * The {@code Image} column name.
     */
    public static final @NotNull String IMAGE = "Image";
    /**
     * The {@code Health Bar} column name.
     */
    public static final @NotNull String HEALTH_BAR_WIDTH = "Health Bar";
    /**
     * The {@code Unused} column name.
     */
    public static final @NotNull String UNUSED = "Unused";
    /**
     * The {@code Is Visible} column name.
     */
    public static final @NotNull String VISIBLE = "Is Visible";
    /**
     * The {@code Selection Circle} column name.
     */
    public static final @NotNull String SELECTION_CIRCLE = "Selection Circle";
    /**
     * The {@code Y Position} column name.
     */
    public static final @NotNull String SELECTION_CIRCLE_OFFSET = "Y Position";
    /**
     * The alignment of the sprites.dat column arrays in the game's memory, where they follow each other in file order.
     */
    private static final int NATIVE_BLOCK_ALIGNMENT = 16;

    /**
     * Creates a {@link DatSchema} instance for {@code arr/sprites.dat}.
     *
     * @return the created {@link DatSchema} instance
     */
    public static @NotNull DatSchema createSchema() {
        return new DatSchema()
                .alignNativeBlocks(NATIVE_BLOCK_ALIGNMENT)
                .fieldU16(IMAGE, SPRITE_COUNT)
                .fieldU8(HEALTH_BAR_WIDTH, BAR_SPRITE_COUNT)
                .fieldU8(UNUSED, SPRITE_COUNT)
                .fieldU8(VISIBLE, SPRITE_COUNT)
                .fieldU8(SELECTION_CIRCLE, BAR_SPRITE_COUNT)
                .fieldS8(SELECTION_CIRCLE_OFFSET, BAR_SPRITE_COUNT);
    }

    /**
     * Creates a {@link ScrSpriteTypeView} instance over the columns of a parsed table; later writes to the table (EUD) are visible through it. An id
     * outside a column reads the game's memory around the column ({@link DatTable#readNativeElement(String, int)}). A column the table lacks (a
     * partial schema) reads as an empty column, whose accessors throw {@link IllegalArgumentException} for every id.
     *
     * @param sprites the parsed {@code sprites.dat} table
     * @return the created {@link ScrSpriteTypeView} instance
     */
    public static @NotNull ScrSpriteTypeView createTypeTable(@NotNull DatTable sprites) {
        return new ScrSpriteTypeView(column -> sprites.getColumnOrEmpty(getColumnName(column)),
                                     (column, index) -> sprites.readNativeElement(getColumnName(column), index));
    }

    /**
     * Creates a {@link ScrSpriteTypeView} instance over the columns of a game's table and the added columns of its added entries: an id past a native
     * column whose element lies in the added columns reads the added column, every other id reads as {@link #createTypeTable(DatTable)} reads it.
     * Without added entries the view is the native one.
     *
     * @param sprites      the game's {@code sprites.dat} table
     * @param addedColumns the added column of each {@code sprites.dat} field, by field name, one element per added entry
     * @return the created {@link ScrSpriteTypeView} instance
     */
    public static @NotNull ScrSpriteTypeView createTypeTable(@NotNull DatTable sprites, @NotNull Function<String, @NotNull DatColumn> addedColumns) {
        Map<ScrSpriteTypeView.Column, DatColumn> added = new EnumMap<>(ScrSpriteTypeView.Column.class);

        for (ScrSpriteTypeView.Column column : ScrSpriteTypeView.Column.values()) {
            added.put(column, addedColumns.apply(getColumnName(column)));
        }

        return new ScrSpriteTypeView(column -> sprites.getColumnOrEmpty(getColumnName(column)), (column, element) -> {
            DatColumn values = added.get(column);
            int addedElement = element - sprites.getColumnOrEmpty(getColumnName(column)).getLength();

            return addedElement >= 0 && addedElement < values.getLength()
                   ? values.get(addedElement)
                   : sprites.readNativeElement(getColumnName(column), element);
        });
    }

    /**
     * @param column a column of the sprite type table
     * @return the sprites.dat field name of the column
     */
    private static @NotNull String getColumnName(ScrSpriteTypeView.@NotNull Column column) {
        return switch (column) {
            case IMAGE -> IMAGE;
            case HEALTH_BAR_WIDTH -> HEALTH_BAR_WIDTH;
            case VISIBLE -> VISIBLE;
            case SELECTION_CIRCLE -> SELECTION_CIRCLE;
            case SELECTION_CIRCLE_OFFSET -> SELECTION_CIRCLE_OFFSET;
        };
    }


    private SpritesDat() {
    }
}
