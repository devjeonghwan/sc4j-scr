package org.fogofwarlabs.sc4j.scr.data.type;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * Holds the {@code images.dat} data of every image, indexed by its {@code int} id.
 * <p>
 * Each accessor reads one value of a column ({@link DatColumn}) that a loader supplies. A game's columns are its own DAT memory, held by its engine
 * content: values keep the width and sign of the column, and EUD writes to the columns are visible. An id outside a column has no bound check: it
 * reads the DAT memory around the column through the loader's {@link ColumnOverrunReader}. Reads allocate nothing.
 */
public final class ScrImageTypeView {
    /**
     * The overlay table of {@link #getAttackOverlay(int)} (LO table {@code 0}).
     */
    public static final int OVERLAY_ATTACK = 0;
    /**
     * The overlay table of {@link #getDamageOverlay(int)} (LO table {@code 1}).
     */
    public static final int OVERLAY_DAMAGE = 1;
    /**
     * The overlay table of {@link #getSpecialOverlay(int)} (LO table {@code 2}).
     */
    public static final int OVERLAY_SPECIAL = 2;
    /**
     * The overlay table of {@link #getLandingDustOverlay(int)} (LO table {@code 3}).
     */
    public static final int OVERLAY_LANDING_DUST = 3;
    /**
     * The overlay table of {@link #getLiftoffDustOverlay(int)} (LO table {@code 4}).
     */
    public static final int OVERLAY_LIFTOFF_DUST = 4;
    /**
     * The overlay table of {@link #getShieldOverlay(int)} (LO table {@code 5}).
     */
    public static final int OVERLAY_SHIELD = 5;

    /**
     * Names the columns a loader supplies to {@link ScrImageTypeView#ScrImageTypeView(Function, ColumnOverrunReader)}.
     */
    public enum Column {
        /**
         * The column of {@link ScrImageTypeView#getGraphicsFile(int)}.
         */
        GRAPHICS_FILE,
        /**
         * The column of {@link ScrImageTypeView#getGraphicTurns(int)}.
         */
        GRAPHIC_TURNS,
        /**
         * The column of {@link ScrImageTypeView#getClickable(int)}.
         */
        CLICKABLE,
        /**
         * The column of {@link ScrImageTypeView#getFullIscript(int)}.
         */
        FULL_ISCRIPT,
        /**
         * The column of {@link ScrImageTypeView#getDrawIfCloaked(int)}.
         */
        DRAW_IF_CLOAKED,
        /**
         * The column of {@link ScrImageTypeView#getDrawFunction(int)}.
         */
        DRAW_FUNCTION,
        /**
         * The column of {@link ScrImageTypeView#getColorShift(int)}.
         */
        COLOR_SHIFT,
        /**
         * The column of {@link ScrImageTypeView#getIscript(int)}.
         */
        ISCRIPT,
        /**
         * The column of {@link ScrImageTypeView#getShieldOverlay(int)}.
         */
        SHIELD_OVERLAY,
        /**
         * The column of {@link ScrImageTypeView#getAttackOverlay(int)}.
         */
        ATTACK_OVERLAY,
        /**
         * The column of {@link ScrImageTypeView#getDamageOverlay(int)}.
         */
        DAMAGE_OVERLAY,
        /**
         * The column of {@link ScrImageTypeView#getSpecialOverlay(int)}.
         */
        SPECIAL_OVERLAY,
        /**
         * The column of {@link ScrImageTypeView#getLandingDustOverlay(int)}.
         */
        LANDING_DUST_OVERLAY,
        /**
         * The column of {@link ScrImageTypeView#getLiftoffDustOverlay(int)}.
         */
        LIFTOFF_DUST_OVERLAY
    }

    private final @NotNull DatColumn graphicsFile;
    private final @NotNull DatColumn graphicTurns;
    private final @NotNull DatColumn clickable;
    private final @NotNull DatColumn fullIscript;
    private final @NotNull DatColumn drawIfCloaked;
    private final @NotNull DatColumn drawFunction;
    private final @NotNull DatColumn colorShift;
    private final @NotNull DatColumn iscript;

    private final @NotNull DatColumn shieldOverlay;
    private final @NotNull DatColumn attackOverlay;
    private final @NotNull DatColumn damageOverlay;
    private final @NotNull DatColumn specialOverlay;
    private final @NotNull DatColumn landingDustOverlay;
    private final @NotNull DatColumn liftoffDustOverlay;

    private final @NotNull ColumnOverrunReader<Column> overrunReader;

    /**
     * Creates a {@link ScrImageTypeView} instance over the columns of a loader.
     *
     * @param columns       the column of each {@link Column}, shared and not copied
     * @param overrunReader the reader of an element outside a column
     */
    public ScrImageTypeView(@NotNull Function<Column, @NotNull DatColumn> columns, @NotNull ColumnOverrunReader<Column> overrunReader) {
        this.graphicsFile  = columns.apply(Column.GRAPHICS_FILE);
        this.graphicTurns  = columns.apply(Column.GRAPHIC_TURNS);
        this.clickable     = columns.apply(Column.CLICKABLE);
        this.fullIscript   = columns.apply(Column.FULL_ISCRIPT);
        this.drawIfCloaked = columns.apply(Column.DRAW_IF_CLOAKED);
        this.drawFunction  = columns.apply(Column.DRAW_FUNCTION);
        this.colorShift    = columns.apply(Column.COLOR_SHIFT);
        this.iscript       = columns.apply(Column.ISCRIPT);

        this.shieldOverlay      = columns.apply(Column.SHIELD_OVERLAY);
        this.attackOverlay      = columns.apply(Column.ATTACK_OVERLAY);
        this.damageOverlay      = columns.apply(Column.DAMAGE_OVERLAY);
        this.specialOverlay     = columns.apply(Column.SPECIAL_OVERLAY);
        this.landingDustOverlay = columns.apply(Column.LANDING_DUST_OVERLAY);
        this.liftoffDustOverlay = columns.apply(Column.LIFTOFF_DUST_OVERLAY);

        this.overrunReader = overrunReader;
    }

    /**
     * @return the number of entries
     */
    public int getCount() {
        return this.graphicsFile.getLength();
    }

    /**
     * @param imageTypeIdentifier the images.dat entry
     * @return the images.tbl string of the GRP file
     */
    public int getGraphicsFile(int imageTypeIdentifier) {
        DatColumn values = this.graphicsFile;

        return imageTypeIdentifier >= 0 && imageTypeIdentifier < values.getLength()
               ? values.get(imageTypeIdentifier)
               : this.overrunReader.read(Column.GRAPHICS_FILE, imageTypeIdentifier);
    }

    /**
     * @param imageTypeIdentifier the images.dat entry
     * @return non-zero if the frames have directions
     */
    public int getGraphicTurns(int imageTypeIdentifier) {
        DatColumn values = this.graphicTurns;

        return imageTypeIdentifier >= 0 && imageTypeIdentifier < values.getLength()
               ? values.get(imageTypeIdentifier)
               : this.overrunReader.read(Column.GRAPHIC_TURNS, imageTypeIdentifier);
    }

    /**
     * @param imageTypeIdentifier the images.dat entry
     * @return non-zero if the image can be clicked
     */
    public int getClickable(int imageTypeIdentifier) {
        DatColumn values = this.clickable;

        return imageTypeIdentifier >= 0 && imageTypeIdentifier < values.getLength()
               ? values.get(imageTypeIdentifier)
               : this.overrunReader.read(Column.CLICKABLE, imageTypeIdentifier);
    }

    /**
     * @param imageTypeIdentifier the images.dat entry
     * @return non-zero if the image runs the full iscript animation set
     */
    public int getFullIscript(int imageTypeIdentifier) {
        DatColumn values = this.fullIscript;

        return imageTypeIdentifier >= 0 && imageTypeIdentifier < values.getLength()
               ? values.get(imageTypeIdentifier)
               : this.overrunReader.read(Column.FULL_ISCRIPT, imageTypeIdentifier);
    }

    /**
     * @param imageTypeIdentifier the images.dat entry
     * @return non-zero if the image is drawn while cloaked
     */
    public int getDrawIfCloaked(int imageTypeIdentifier) {
        DatColumn values = this.drawIfCloaked;

        return imageTypeIdentifier >= 0 && imageTypeIdentifier < values.getLength()
               ? values.get(imageTypeIdentifier)
               : this.overrunReader.read(Column.DRAW_IF_CLOAKED, imageTypeIdentifier);
    }

    /**
     * @param imageTypeIdentifier the images.dat entry
     * @return the draw function
     */
    public int getDrawFunction(int imageTypeIdentifier) {
        DatColumn values = this.drawFunction;

        return imageTypeIdentifier >= 0 && imageTypeIdentifier < values.getLength()
               ? values.get(imageTypeIdentifier)
               : this.overrunReader.read(Column.DRAW_FUNCTION, imageTypeIdentifier);
    }

    /**
     * @param imageTypeIdentifier the images.dat entry
     * @return the color remapping
     */
    public int getColorShift(int imageTypeIdentifier) {
        DatColumn values = this.colorShift;

        return imageTypeIdentifier >= 0 && imageTypeIdentifier < values.getLength()
               ? values.get(imageTypeIdentifier)
               : this.overrunReader.read(Column.COLOR_SHIFT, imageTypeIdentifier);
    }

    /**
     * @param imageTypeIdentifier the images.dat entry
     * @return the iscript.bin entry
     */
    public int getIscript(int imageTypeIdentifier) {
        DatColumn values = this.iscript;

        return imageTypeIdentifier >= 0 && imageTypeIdentifier < values.getLength()
               ? values.get(imageTypeIdentifier)
               : this.overrunReader.read(Column.ISCRIPT, imageTypeIdentifier);
    }

    /**
     * @param imageTypeIdentifier the images.dat entry
     * @return the images.tbl string of the shield overlay offsets
     */
    public int getShieldOverlay(int imageTypeIdentifier) {
        DatColumn values = this.shieldOverlay;

        return imageTypeIdentifier >= 0 && imageTypeIdentifier < values.getLength()
               ? values.get(imageTypeIdentifier)
               : this.overrunReader.read(Column.SHIELD_OVERLAY, imageTypeIdentifier);
    }

    /**
     * @param imageTypeIdentifier the images.dat entry
     * @return the images.tbl string of the attack overlay offsets
     */
    public int getAttackOverlay(int imageTypeIdentifier) {
        DatColumn values = this.attackOverlay;

        return imageTypeIdentifier >= 0 && imageTypeIdentifier < values.getLength()
               ? values.get(imageTypeIdentifier)
               : this.overrunReader.read(Column.ATTACK_OVERLAY, imageTypeIdentifier);
    }

    /**
     * @param imageTypeIdentifier the images.dat entry
     * @return the images.tbl string of the damage overlay offsets
     */
    public int getDamageOverlay(int imageTypeIdentifier) {
        DatColumn values = this.damageOverlay;

        return imageTypeIdentifier >= 0 && imageTypeIdentifier < values.getLength()
               ? values.get(imageTypeIdentifier)
               : this.overrunReader.read(Column.DAMAGE_OVERLAY, imageTypeIdentifier);
    }

    /**
     * @param imageTypeIdentifier the images.dat entry
     * @return the images.tbl string of the special overlay offsets
     */
    public int getSpecialOverlay(int imageTypeIdentifier) {
        DatColumn values = this.specialOverlay;

        return imageTypeIdentifier >= 0 && imageTypeIdentifier < values.getLength()
               ? values.get(imageTypeIdentifier)
               : this.overrunReader.read(Column.SPECIAL_OVERLAY, imageTypeIdentifier);
    }

    /**
     * @param imageTypeIdentifier the images.dat entry
     * @return the images.tbl string of the landing dust overlay offsets
     */
    public int getLandingDustOverlay(int imageTypeIdentifier) {
        DatColumn values = this.landingDustOverlay;

        return imageTypeIdentifier >= 0 && imageTypeIdentifier < values.getLength()
               ? values.get(imageTypeIdentifier)
               : this.overrunReader.read(Column.LANDING_DUST_OVERLAY, imageTypeIdentifier);
    }

    /**
     * @param imageTypeIdentifier the images.dat entry
     * @return the images.tbl string of the liftoff dust overlay offsets
     */
    public int getLiftoffDustOverlay(int imageTypeIdentifier) {
        DatColumn values = this.liftoffDustOverlay;

        return imageTypeIdentifier >= 0 && imageTypeIdentifier < values.getLength()
               ? values.get(imageTypeIdentifier)
               : this.overrunReader.read(Column.LIFTOFF_DUST_OVERLAY, imageTypeIdentifier);
    }

    /**
     * Returns the images.tbl string of an overlay offset table selected by its LO table index.
     *
     * @param imageTypeIdentifier the images.dat entry
     * @param overlayType         the overlay table, {@link #OVERLAY_ATTACK} through {@link #OVERLAY_SHIELD}
     * @return the images.tbl string of the overlay offsets
     * @throws IllegalArgumentException if the overlay table is unknown
     */
    public int getOverlay(int imageTypeIdentifier, int overlayType) {
        return switch (overlayType) {
            case OVERLAY_ATTACK -> this.getAttackOverlay(imageTypeIdentifier);
            case OVERLAY_DAMAGE -> this.getDamageOverlay(imageTypeIdentifier);
            case OVERLAY_SPECIAL -> this.getSpecialOverlay(imageTypeIdentifier);
            case OVERLAY_LANDING_DUST -> this.getLandingDustOverlay(imageTypeIdentifier);
            case OVERLAY_LIFTOFF_DUST -> this.getLiftoffDustOverlay(imageTypeIdentifier);
            case OVERLAY_SHIELD -> this.getShieldOverlay(imageTypeIdentifier);
            default -> throw Exceptions.newIllegalArgumentException("Unsupported overlay table: {0}", overlayType);
        };
    }
}
