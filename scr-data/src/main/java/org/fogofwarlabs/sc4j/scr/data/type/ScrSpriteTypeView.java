package org.fogofwarlabs.sc4j.scr.data.type;

import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * Holds the {@code sprites.dat} data of every sprite, indexed by its {@code int} id.
 * <p>
 * Each accessor reads one value of a column ({@link DatColumn}) that a loader supplies. A game's columns are its own DAT memory, held by its engine
 * content: values keep the width and sign of the column, and EUD writes to the columns are visible. An id outside a column has no bound check: it
 * reads the DAT memory around the column through the loader's {@link ColumnOverrunReader}. Reads allocate nothing.
 */
public final class ScrSpriteTypeView {
    /**
     * The first sprite with a health bar and a selection circle.
     */
    public static final int FIRST_SELECTABLE_SPRITE = 130;

    /**
     * Names the columns a loader supplies to {@link ScrSpriteTypeView#ScrSpriteTypeView(Function, ColumnOverrunReader)}.
     */
    public enum Column {
        /**
         * The column of {@link ScrSpriteTypeView#getImage(int)}.
         */
        IMAGE,
        /**
         * The column of {@link ScrSpriteTypeView#getHealthBarWidth(int)}.
         */
        HEALTH_BAR_WIDTH,
        /**
         * The column of {@link ScrSpriteTypeView#getVisible(int)}.
         */
        VISIBLE,
        /**
         * The column of {@link ScrSpriteTypeView#getSelectionCircle(int)}.
         */
        SELECTION_CIRCLE,
        /**
         * The column of {@link ScrSpriteTypeView#getSelectionCircleOffset(int)}.
         */
        SELECTION_CIRCLE_OFFSET
    }

    private final @NotNull DatColumn image;

    private final @NotNull DatColumn healthBarWidth;
    private final @NotNull DatColumn visible;

    private final @NotNull DatColumn selectionCircle;
    private final @NotNull DatColumn selectionCircleOffset;

    private final @NotNull ColumnOverrunReader<Column> overrunReader;

    /**
     * Creates a {@link ScrSpriteTypeView} instance over the columns of a loader.
     *
     * @param columns       the column of each {@link Column}, shared and not copied
     * @param overrunReader the reader of an element outside a column
     */
    public ScrSpriteTypeView(@NotNull Function<Column, @NotNull DatColumn> columns, @NotNull ColumnOverrunReader<Column> overrunReader) {
        this.image = columns.apply(Column.IMAGE);

        this.healthBarWidth = columns.apply(Column.HEALTH_BAR_WIDTH);
        this.visible        = columns.apply(Column.VISIBLE);

        this.selectionCircle       = columns.apply(Column.SELECTION_CIRCLE);
        this.selectionCircleOffset = columns.apply(Column.SELECTION_CIRCLE_OFFSET);

        this.overrunReader = overrunReader;
    }

    /**
     * @return the number of entries
     */
    public int getCount() {
        return this.image.getLength();
    }

    /**
     * @param spriteTypeIdentifier the sprites.dat entry
     * @return the images.dat entry of the primary image
     */
    public int getImage(int spriteTypeIdentifier) {
        DatColumn values = this.image;

        return spriteTypeIdentifier >= 0 && spriteTypeIdentifier < values.getLength()
               ? values.get(spriteTypeIdentifier)
               : this.overrunReader.read(Column.IMAGE, spriteTypeIdentifier);
    }

    /**
     * @param spriteTypeIdentifier the sprites.dat entry
     * @return the health bar width in pixels (stored for sprites from {@link #FIRST_SELECTABLE_SPRITE} on)
     */
    public int getHealthBarWidth(int spriteTypeIdentifier) {
        int element = spriteTypeIdentifier - FIRST_SELECTABLE_SPRITE;
        DatColumn values = this.healthBarWidth;

        return element >= 0 && element < values.getLength()
               ? values.get(element)
               : this.overrunReader.read(Column.HEALTH_BAR_WIDTH, element);
    }

    /**
     * @param spriteTypeIdentifier the sprites.dat entry
     * @return non-zero if the sprite starts visible
     */
    public int getVisible(int spriteTypeIdentifier) {
        DatColumn values = this.visible;

        return spriteTypeIdentifier >= 0 && spriteTypeIdentifier < values.getLength()
               ? values.get(spriteTypeIdentifier)
               : this.overrunReader.read(Column.VISIBLE, spriteTypeIdentifier);
    }

    /**
     * @param spriteTypeIdentifier the sprites.dat entry
     * @return the selection circle (stored for sprites from {@link #FIRST_SELECTABLE_SPRITE} on)
     */
    public int getSelectionCircle(int spriteTypeIdentifier) {
        int element = spriteTypeIdentifier - FIRST_SELECTABLE_SPRITE;
        DatColumn values = this.selectionCircle;

        return element >= 0 && element < values.getLength()
               ? values.get(element)
               : this.overrunReader.read(Column.SELECTION_CIRCLE, element);
    }

    /**
     * @param spriteTypeIdentifier the sprites.dat entry
     * @return the signed vertical offset of the selection circle in pixels (stored for sprites from {@link #FIRST_SELECTABLE_SPRITE} on)
     */
    public int getSelectionCircleOffset(int spriteTypeIdentifier) {
        int element = spriteTypeIdentifier - FIRST_SELECTABLE_SPRITE;
        DatColumn values = this.selectionCircleOffset;

        return element >= 0 && element < values.getLength()
               ? values.get(element)
               : this.overrunReader.read(Column.SELECTION_CIRCLE_OFFSET, element);
    }
}
