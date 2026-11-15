package org.fogofwarlabs.sc4j.scr.data.type;

import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * Holds the {@code flingy.dat} data of every flingy, indexed by its {@code int} id.
 * <p>
 * Each accessor reads one value of a column ({@link DatColumn}) that a loader supplies. A game's columns are its own DAT memory, held by its engine
 * content: values keep the width and sign of the column, and EUD writes to the columns are visible. An id outside a column has no bound check: it
 * reads the DAT memory around the column through the loader's {@link ColumnOverrunReader}. Reads allocate nothing.
 */
public final class ScrFlingyTypeView {
    /**
     * The movement control of a flingy that its own top speed, acceleration and turn rate move.
     */
    public static final int MOVEMENT_CONTROL_FLINGY = 0;
    /**
     * The movement control of a partially mobile flingy (weapons): it stops at once at its target.
     */
    public static final int MOVEMENT_CONTROL_PARTIALLY_MOBILE = 1;
    /**
     * The movement control of a flingy whose iscript (the Walking animation) moves it.
     */
    public static final int MOVEMENT_CONTROL_ISCRIPT = 2;

    /**
     * Names the columns a loader supplies to {@link ScrFlingyTypeView#ScrFlingyTypeView(Function, ColumnOverrunReader)}.
     */
    public enum Column {
        /**
         * The column of {@link ScrFlingyTypeView#getSprite(int)}.
         */
        SPRITE,
        /**
         * The column of {@link ScrFlingyTypeView#getTopSpeed(int)}.
         */
        TOP_SPEED,
        /**
         * The column of {@link ScrFlingyTypeView#getAcceleration(int)}.
         */
        ACCELERATION,
        /**
         * The column of {@link ScrFlingyTypeView#getHaltDistance(int)}.
         */
        HALT_DISTANCE,
        /**
         * The column of {@link ScrFlingyTypeView#getTurnRate(int)}.
         */
        TURN_RATE,
        /**
         * The column of {@link ScrFlingyTypeView#getMovementControl(int)}.
         */
        MOVEMENT_CONTROL
    }

    private final @NotNull DatColumn sprite;

    private final @NotNull DatColumn topSpeed;
    private final @NotNull DatColumn acceleration;
    private final @NotNull DatColumn haltDistance;
    private final @NotNull DatColumn turnRate;
    private final @NotNull DatColumn movementControl;

    private final @NotNull ColumnOverrunReader<Column> overrunReader;

    /**
     * Creates a {@link ScrFlingyTypeView} instance over the columns of a loader.
     *
     * @param columns       the column of each {@link Column}, shared and not copied
     * @param overrunReader the reader of an element outside a column
     */
    public ScrFlingyTypeView(@NotNull Function<Column, @NotNull DatColumn> columns, @NotNull ColumnOverrunReader<Column> overrunReader) {
        this.sprite = columns.apply(Column.SPRITE);

        this.topSpeed        = columns.apply(Column.TOP_SPEED);
        this.acceleration    = columns.apply(Column.ACCELERATION);
        this.haltDistance    = columns.apply(Column.HALT_DISTANCE);
        this.turnRate        = columns.apply(Column.TURN_RATE);
        this.movementControl = columns.apply(Column.MOVEMENT_CONTROL);

        this.overrunReader = overrunReader;
    }

    /**
     * @return the number of entries
     */
    public int getCount() {
        return this.sprite.getLength();
    }

    /**
     * @param flingyTypeIdentifier the flingy.dat entry
     * @return the sprites.dat entry
     */
    public int getSprite(int flingyTypeIdentifier) {
        DatColumn values = this.sprite;

        return flingyTypeIdentifier >= 0 && flingyTypeIdentifier < values.getLength()
               ? values.get(flingyTypeIdentifier)
               : this.overrunReader.read(Column.SPRITE, flingyTypeIdentifier);
    }

    /**
     * @param flingyTypeIdentifier the flingy.dat entry
     * @return the top speed in 1/256 pixels per frame
     */
    public int getTopSpeed(int flingyTypeIdentifier) {
        DatColumn values = this.topSpeed;

        return flingyTypeIdentifier >= 0 && flingyTypeIdentifier < values.getLength()
               ? values.get(flingyTypeIdentifier)
               : this.overrunReader.read(Column.TOP_SPEED, flingyTypeIdentifier);
    }

    /**
     * @param flingyTypeIdentifier the flingy.dat entry
     * @return the acceleration in 1/256 pixels per frame per frame
     */
    public int getAcceleration(int flingyTypeIdentifier) {
        DatColumn values = this.acceleration;

        return flingyTypeIdentifier >= 0 && flingyTypeIdentifier < values.getLength()
               ? values.get(flingyTypeIdentifier)
               : this.overrunReader.read(Column.ACCELERATION, flingyTypeIdentifier);
    }

    /**
     * @param flingyTypeIdentifier the flingy.dat entry
     * @return the halt distance in 1/256 pixels
     */
    public int getHaltDistance(int flingyTypeIdentifier) {
        DatColumn values = this.haltDistance;

        return flingyTypeIdentifier >= 0 && flingyTypeIdentifier < values.getLength()
               ? values.get(flingyTypeIdentifier)
               : this.overrunReader.read(Column.HALT_DISTANCE, flingyTypeIdentifier);
    }

    /**
     * @param flingyTypeIdentifier the flingy.dat entry
     * @return the turn rate in 1/256 turns per frame
     */
    public int getTurnRate(int flingyTypeIdentifier) {
        DatColumn values = this.turnRate;

        return flingyTypeIdentifier >= 0 && flingyTypeIdentifier < values.getLength()
               ? values.get(flingyTypeIdentifier)
               : this.overrunReader.read(Column.TURN_RATE, flingyTypeIdentifier);
    }

    /**
     * @param flingyTypeIdentifier the flingy.dat entry
     * @return the movement control, {@link #MOVEMENT_CONTROL_FLINGY}, {@link #MOVEMENT_CONTROL_PARTIALLY_MOBILE} or {@link #MOVEMENT_CONTROL_ISCRIPT}
     */
    public int getMovementControl(int flingyTypeIdentifier) {
        DatColumn values = this.movementControl;

        return flingyTypeIdentifier >= 0 && flingyTypeIdentifier < values.getLength()
               ? values.get(flingyTypeIdentifier)
               : this.overrunReader.read(Column.MOVEMENT_CONTROL, flingyTypeIdentifier);
    }
}
