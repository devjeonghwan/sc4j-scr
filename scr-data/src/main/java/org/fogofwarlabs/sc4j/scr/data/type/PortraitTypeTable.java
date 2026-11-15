package org.fogofwarlabs.sc4j.scr.data.type;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * Holds the {@code portdata.dat} data of every portrait, indexed by its {@code int} id.
 * <p>
 * Each accessor reads one value of a column ({@link DatColumn}) that a loader supplies. A game's columns are its own DAT memory, held by its engine
 * content: values keep the width and sign of the column, and EUD writes to the columns are visible. An id outside a column has no bound check: it
 * reads the DAT memory around the column through the loader's {@link ColumnOverrunReader}. Reads allocate nothing.
 */
public final class PortraitTypeTable {
    /**
     * The number of idle clips with a weight.
     */
    public static final int IDLE_CLIP_COUNT = 4;

    private static final int FIRST_IDLE_CLIP = 0;
    private static final int SECOND_IDLE_CLIP = 1;
    private static final int THIRD_IDLE_CLIP = 2;
    private static final int FOURTH_IDLE_CLIP = 3;

    /**
     * Names the columns a loader supplies to {@link PortraitTypeTable#PortraitTypeTable(Function, ColumnOverrunReader)}.
     */
    public enum Column {
        /**
         * The column of {@link PortraitTypeTable#getIdleDirectory(int)}.
         */
        IDLE_DIRECTORY,
        /**
         * The column of {@link PortraitTypeTable#getTalkingDirectory(int)}.
         */
        TALKING_DIRECTORY,
        /**
         * The column of {@link PortraitTypeTable#getFirstIdleChange(int)}.
         */
        FIRST_IDLE_CHANGE,
        /**
         * The column of {@link PortraitTypeTable#getSecondIdleChange(int)}.
         */
        SECOND_IDLE_CHANGE,
        /**
         * The column of {@link PortraitTypeTable#getThirdIdleChange(int)}.
         */
        THIRD_IDLE_CHANGE,
        /**
         * The column of {@link PortraitTypeTable#getFourthIdleChange(int)}.
         */
        FOURTH_IDLE_CHANGE
    }

    private final @NotNull DatColumn idleDirectory;
    private final @NotNull DatColumn talkingDirectory;

    private final @NotNull DatColumn firstIdleChange;
    private final @NotNull DatColumn secondIdleChange;
    private final @NotNull DatColumn thirdIdleChange;
    private final @NotNull DatColumn fourthIdleChange;

    private final @NotNull ColumnOverrunReader<Column> overrunReader;

    /**
     * Creates a {@link PortraitTypeTable} instance over the columns of a loader.
     *
     * @param columns       the column of each {@link Column}, shared and not copied
     * @param overrunReader the reader of an element outside a column
     */
    public PortraitTypeTable(@NotNull Function<Column, @NotNull DatColumn> columns, @NotNull ColumnOverrunReader<Column> overrunReader) {
        this.idleDirectory    = columns.apply(Column.IDLE_DIRECTORY);
        this.talkingDirectory = columns.apply(Column.TALKING_DIRECTORY);

        this.firstIdleChange  = columns.apply(Column.FIRST_IDLE_CHANGE);
        this.secondIdleChange = columns.apply(Column.SECOND_IDLE_CHANGE);
        this.thirdIdleChange  = columns.apply(Column.THIRD_IDLE_CHANGE);
        this.fourthIdleChange = columns.apply(Column.FOURTH_IDLE_CHANGE);

        this.overrunReader = overrunReader;
    }

    /**
     * @return the number of entries
     */
    public int getCount() {
        return this.idleDirectory.getLength();
    }

    /**
     * @param portraitIdentifier the portdata.dat entry
     * @return the portdata.tbl string of the idle animation directory
     */
    public int getIdleDirectory(int portraitIdentifier) {
        DatColumn values = this.idleDirectory;

        return portraitIdentifier >= 0 && portraitIdentifier < values.getLength()
               ? values.get(portraitIdentifier)
               : this.overrunReader.read(Column.IDLE_DIRECTORY, portraitIdentifier);
    }

    /**
     * @param portraitIdentifier the portdata.dat entry
     * @return the portdata.tbl string of the talking animation directory
     */
    public int getTalkingDirectory(int portraitIdentifier) {
        DatColumn values = this.talkingDirectory;

        return portraitIdentifier >= 0 && portraitIdentifier < values.getLength()
               ? values.get(portraitIdentifier)
               : this.overrunReader.read(Column.TALKING_DIRECTORY, portraitIdentifier);
    }

    /**
     * @param portraitIdentifier the portdata.dat entry
     * @return the weight of the first idle clip
     */
    public int getFirstIdleChange(int portraitIdentifier) {
        DatColumn values = this.firstIdleChange;

        return portraitIdentifier >= 0 && portraitIdentifier < values.getLength()
               ? values.get(portraitIdentifier)
               : this.overrunReader.read(Column.FIRST_IDLE_CHANGE, portraitIdentifier);
    }

    /**
     * @param portraitIdentifier the portdata.dat entry
     * @return the weight of the second idle clip
     */
    public int getSecondIdleChange(int portraitIdentifier) {
        DatColumn values = this.secondIdleChange;

        return portraitIdentifier >= 0 && portraitIdentifier < values.getLength()
               ? values.get(portraitIdentifier)
               : this.overrunReader.read(Column.SECOND_IDLE_CHANGE, portraitIdentifier);
    }

    /**
     * @param portraitIdentifier the portdata.dat entry
     * @return the weight of the third idle clip
     */
    public int getThirdIdleChange(int portraitIdentifier) {
        DatColumn values = this.thirdIdleChange;

        return portraitIdentifier >= 0 && portraitIdentifier < values.getLength()
               ? values.get(portraitIdentifier)
               : this.overrunReader.read(Column.THIRD_IDLE_CHANGE, portraitIdentifier);
    }

    /**
     * @param portraitIdentifier the portdata.dat entry
     * @return the weight of the fourth idle clip
     */
    public int getFourthIdleChange(int portraitIdentifier) {
        DatColumn values = this.fourthIdleChange;

        return portraitIdentifier >= 0 && portraitIdentifier < values.getLength()
               ? values.get(portraitIdentifier)
               : this.overrunReader.read(Column.FOURTH_IDLE_CHANGE, portraitIdentifier);
    }

    /**
     * Returns the weight of an idle clip.
     *
     * @param portraitIdentifier the portdata.dat entry
     * @param clip               the idle clip, from {@code 0} below {@link #IDLE_CLIP_COUNT}
     * @return the weight of the clip
     * @throws IllegalArgumentException if the clip is out of range
     */
    public int getIdleChange(int portraitIdentifier, int clip) {
        return switch (clip) {
            case FIRST_IDLE_CLIP -> this.getFirstIdleChange(portraitIdentifier);
            case SECOND_IDLE_CLIP -> this.getSecondIdleChange(portraitIdentifier);
            case THIRD_IDLE_CLIP -> this.getThirdIdleChange(portraitIdentifier);
            case FOURTH_IDLE_CLIP -> this.getFourthIdleChange(portraitIdentifier);
            default -> throw Exceptions.newIllegalArgumentException("Unsupported idle clip: {0}", clip);
        };
    }
}
