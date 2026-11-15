package org.fogofwarlabs.sc4j.scr.data.type;

import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * Holds the {@code sfxdata.dat} data of every sound, indexed by its {@code int} id.
 * <p>
 * Each accessor reads one value of a column ({@link DatColumn}) that a loader supplies. A game's columns are its own DAT memory, held by its engine
 * content: values keep the width and sign of the column, and EUD writes to the columns are visible. An id outside a column goes to the loader's
 * {@link ColumnOverrunReader}; {@code sfxdata.dat} has no verified overrun layout, so that read throws {@link ArrayIndexOutOfBoundsException}. Reads
 * allocate nothing.
 */
public final class ScrSoundTypeView {
    /**
     * Names the columns a loader supplies to {@link ScrSoundTypeView#ScrSoundTypeView(Function, ColumnOverrunReader)}.
     */
    public enum Column {
        /**
         * The column of {@link ScrSoundTypeView#getFile(int)}.
         */
        FILE,
        /**
         * The column of {@link ScrSoundTypeView#getPriority(int)}.
         */
        PRIORITY,
        /**
         * The column of {@link ScrSoundTypeView#getFlags(int)}.
         */
        FLAGS,
        /**
         * The column of {@link ScrSoundTypeView#getPortraitDuration(int)}.
         */
        PORTRAIT_DURATION,
        /**
         * The column of {@link ScrSoundTypeView#getVolume(int)}.
         */
        VOLUME
    }

    private final @NotNull DatColumn file;

    private final @NotNull DatColumn priority;
    private final @NotNull DatColumn flags;

    private final @NotNull DatColumn portraitDuration;

    private final @NotNull DatColumn volume;

    private final @NotNull ColumnOverrunReader<Column> overrunReader;

    /**
     * Creates a {@link ScrSoundTypeView} instance over the columns of a loader.
     *
     * @param columns       the column of each {@link Column}, shared and not copied
     * @param overrunReader the reader of an element outside a column
     */
    public ScrSoundTypeView(@NotNull Function<Column, @NotNull DatColumn> columns, @NotNull ColumnOverrunReader<Column> overrunReader) {
        this.file = columns.apply(Column.FILE);

        this.priority = columns.apply(Column.PRIORITY);
        this.flags    = columns.apply(Column.FLAGS);

        this.portraitDuration = columns.apply(Column.PORTRAIT_DURATION);

        this.volume = columns.apply(Column.VOLUME);

        this.overrunReader = overrunReader;
    }

    /**
     * @return the number of entries
     */
    public int getCount() {
        return this.file.getLength();
    }

    /**
     * @param soundIdentifier the sfxdata.dat entry
     * @return the sfxdata.tbl string of the sound file
     */
    public int getFile(int soundIdentifier) {
        DatColumn values = this.file;

        return soundIdentifier >= 0 && soundIdentifier < values.getLength()
               ? values.get(soundIdentifier)
               : this.overrunReader.read(Column.FILE, soundIdentifier);
    }

    /**
     * @param soundIdentifier the sfxdata.dat entry
     * @return the playback priority
     */
    public int getPriority(int soundIdentifier) {
        DatColumn values = this.priority;

        return soundIdentifier >= 0 && soundIdentifier < values.getLength()
               ? values.get(soundIdentifier)
               : this.overrunReader.read(Column.PRIORITY, soundIdentifier);
    }

    /**
     * @param soundIdentifier the sfxdata.dat entry
     * @return the playback flags
     */
    public int getFlags(int soundIdentifier) {
        DatColumn values = this.flags;

        return soundIdentifier >= 0 && soundIdentifier < values.getLength()
               ? values.get(soundIdentifier)
               : this.overrunReader.read(Column.FLAGS, soundIdentifier);
    }

    /**
     * @param soundIdentifier the sfxdata.dat entry
     * @return the signed portrait talking duration
     */
    public int getPortraitDuration(int soundIdentifier) {
        DatColumn values = this.portraitDuration;

        return soundIdentifier >= 0 && soundIdentifier < values.getLength()
               ? values.get(soundIdentifier)
               : this.overrunReader.read(Column.PORTRAIT_DURATION, soundIdentifier);
    }

    /**
     * @param soundIdentifier the sfxdata.dat entry
     * @return the signed minimum volume
     */
    public int getVolume(int soundIdentifier) {
        DatColumn values = this.volume;

        return soundIdentifier >= 0 && soundIdentifier < values.getLength()
               ? values.get(soundIdentifier)
               : this.overrunReader.read(Column.VOLUME, soundIdentifier);
    }
}
