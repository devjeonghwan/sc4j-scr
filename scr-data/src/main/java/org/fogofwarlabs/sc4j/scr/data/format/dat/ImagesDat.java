package org.fogofwarlabs.sc4j.scr.data.format.dat;

import org.fogofwarlabs.sc4j.scr.data.type.DatColumn;
import org.fogofwarlabs.sc4j.scr.data.type.ScrImageTypeView;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Provides the column schema of {@code arr/images.dat} (999 entries, 37,962 bytes).
 */
public final class ImagesDat {
    /**
     * The number of images.dat entries.
     */
    public static final int IMAGE_COUNT = 999;
    /**
     * The {@code GRP File} column name.
     */
    public static final @NotNull String GRAPHICS_FILE = "GRP File";
    /**
     * The {@code Graphic Turns} column name.
     */
    public static final @NotNull String GRAPHIC_TURNS = "Graphic Turns";
    /**
     * The {@code Clickable} column name.
     */
    public static final @NotNull String CLICKABLE = "Clickable";
    /**
     * The {@code Use Full Iscript} column name.
     */
    public static final @NotNull String FULL_ISCRIPT = "Use Full Iscript";
    /**
     * The {@code Draw If Cloaked} column name.
     */
    public static final @NotNull String DRAW_IF_CLOAKED = "Draw If Cloaked";
    /**
     * The {@code Draw Function} column name.
     */
    public static final @NotNull String DRAW_FUNCTION = "Draw Function";
    /**
     * The {@code Color Shift} column name.
     */
    public static final @NotNull String COLOR_SHIFT = "Color Shift";
    /**
     * The {@code Iscript} column name.
     */
    public static final @NotNull String ISCRIPT = "Iscript";
    /**
     * The {@code Shield Overlay} column name.
     */
    public static final @NotNull String SHIELD_OVERLAY = "Shield Overlay";
    /**
     * The {@code Attack Overlay} column name.
     */
    public static final @NotNull String ATTACK_OVERLAY = "Attack Overlay";
    /**
     * The {@code Damage Overlay} column name.
     */
    public static final @NotNull String DAMAGE_OVERLAY = "Damage Overlay";
    /**
     * The {@code Special Overlay} column name.
     */
    public static final @NotNull String SPECIAL_OVERLAY = "Special Overlay";
    /**
     * The {@code Landing Dust Overlay} column name.
     */
    public static final @NotNull String LANDING_DUST_OVERLAY = "Landing Dust Overlay";
    /**
     * The {@code Liftoff Dust Overlay} column name.
     */
    public static final @NotNull String LIFTOFF_DUST_OVERLAY = "Liftoff Dust Overlay";
    /**
     * The alignment of the images.dat column arrays in the game's memory, where they follow each other in file order.
     */
    private static final int NATIVE_BLOCK_ALIGNMENT = 16;

    /**
     * Creates a {@link DatSchema} instance for {@code arr/images.dat}.
     *
     * @return the created {@link DatSchema} instance
     */
    public static @NotNull DatSchema createSchema() {
        return new DatSchema()
                .alignNativeBlocks(NATIVE_BLOCK_ALIGNMENT)
                .fieldU32(GRAPHICS_FILE, IMAGE_COUNT)
                .fieldU8(GRAPHIC_TURNS, IMAGE_COUNT)
                .fieldU8(CLICKABLE, IMAGE_COUNT)
                .fieldU8(FULL_ISCRIPT, IMAGE_COUNT)
                .fieldU8(DRAW_IF_CLOAKED, IMAGE_COUNT)
                .fieldU8(DRAW_FUNCTION, IMAGE_COUNT)
                .fieldU8(COLOR_SHIFT, IMAGE_COUNT)
                .fieldU32(ISCRIPT, IMAGE_COUNT)
                .fieldU32(SHIELD_OVERLAY, IMAGE_COUNT)
                .fieldU32(ATTACK_OVERLAY, IMAGE_COUNT)
                .fieldU32(DAMAGE_OVERLAY, IMAGE_COUNT)
                .fieldU32(SPECIAL_OVERLAY, IMAGE_COUNT)
                .fieldU32(LANDING_DUST_OVERLAY, IMAGE_COUNT)
                .fieldU32(LIFTOFF_DUST_OVERLAY, IMAGE_COUNT)
                .holdNativeReferences(GRAPHICS_FILE)
                .holdNativeReferences(SHIELD_OVERLAY)
                .holdNativeReferences(ATTACK_OVERLAY)
                .holdNativeReferences(DAMAGE_OVERLAY)
                .holdNativeReferences(SPECIAL_OVERLAY)
                .holdNativeReferences(LANDING_DUST_OVERLAY)
                .holdNativeReferences(LIFTOFF_DUST_OVERLAY);
    }

    /**
     * Creates a {@link ScrImageTypeView} instance over the columns of a parsed table; later writes to the table (EUD) are visible through it. An id
     * outside a column reads the game's memory around the column ({@link DatTable#readNativeElement(String, int)}). A column the table lacks (a
     * partial schema) reads as an empty column, whose accessors throw {@link IllegalArgumentException} for every id.
     *
     * @param images the parsed {@code images.dat} table
     * @return the created {@link ScrImageTypeView} instance
     */
    public static @NotNull ScrImageTypeView createTypeTable(@NotNull DatTable images) {
        return new ScrImageTypeView(column -> images.getColumnOrEmpty(getColumnName(column)),
                                    (column, index) -> images.readNativeElement(getColumnName(column), index));
    }

    /**
     * Creates a {@link ScrImageTypeView} instance over the columns of a game's table and the added columns of its added entries: an id past a native
     * column whose element lies in the added columns reads the added column, every other id reads as {@link #createTypeTable(DatTable)} reads it.
     * Without added entries the view is the native one.
     *
     * @param images       the game's {@code images.dat} table
     * @param addedColumns the added column of each {@code images.dat} field, by field name, one element per added entry
     * @return the created {@link ScrImageTypeView} instance
     */
    public static @NotNull ScrImageTypeView createTypeTable(@NotNull DatTable images, @NotNull Function<String, @NotNull DatColumn> addedColumns) {
        Map<ScrImageTypeView.Column, DatColumn> added = new EnumMap<>(ScrImageTypeView.Column.class);

        for (ScrImageTypeView.Column column : ScrImageTypeView.Column.values()) {
            added.put(column, addedColumns.apply(getColumnName(column)));
        }

        return new ScrImageTypeView(column -> images.getColumnOrEmpty(getColumnName(column)), (column, element) -> {
            DatColumn values = added.get(column);
            int addedElement = element - images.getColumnOrEmpty(getColumnName(column)).getLength();

            return addedElement >= 0 && addedElement < values.getLength()
                   ? values.get(addedElement)
                   : images.readNativeElement(getColumnName(column), element);
        });
    }

    /**
     * @param column a column of the image type table
     * @return the images.dat field name of the column
     */
    private static @NotNull String getColumnName(ScrImageTypeView.@NotNull Column column) {
        return switch (column) {
            case GRAPHICS_FILE -> GRAPHICS_FILE;
            case GRAPHIC_TURNS -> GRAPHIC_TURNS;
            case CLICKABLE -> CLICKABLE;
            case FULL_ISCRIPT -> FULL_ISCRIPT;
            case DRAW_IF_CLOAKED -> DRAW_IF_CLOAKED;
            case DRAW_FUNCTION -> DRAW_FUNCTION;
            case COLOR_SHIFT -> COLOR_SHIFT;
            case ISCRIPT -> ISCRIPT;
            case SHIELD_OVERLAY -> SHIELD_OVERLAY;
            case ATTACK_OVERLAY -> ATTACK_OVERLAY;
            case DAMAGE_OVERLAY -> DAMAGE_OVERLAY;
            case SPECIAL_OVERLAY -> SPECIAL_OVERLAY;
            case LANDING_DUST_OVERLAY -> LANDING_DUST_OVERLAY;
            case LIFTOFF_DUST_OVERLAY -> LIFTOFF_DUST_OVERLAY;
        };
    }


    private ImagesDat() {
    }
}
