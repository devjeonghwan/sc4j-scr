package org.fogofwarlabs.sc4j.scr.data.format.dat;

import org.fogofwarlabs.sc4j.scr.data.type.ScrSoundTypeView;
import org.jetbrains.annotations.NotNull;

/**
 * Provides the column schema of {@code arr/sfxdata.dat} (1,144 entries, 10,296 bytes).
 */
public final class SfxdataDat {
    /**
     * The number of sfxdata.dat entries.
     */
    public static final int SOUND_COUNT = 1144;
    /**
     * The {@code Sound File} column name.
     */
    public static final @NotNull String FILE = "Sound File";
    /**
     * The {@code Priority} column name.
     */
    public static final @NotNull String PRIORITY = "Priority";
    /**
     * The {@code Properties} column name.
     */
    public static final @NotNull String FLAGS = "Properties";
    /**
     * The {@code Portrait Duration} column name.
     */
    public static final @NotNull String PORTRAIT_DURATION = "Portrait Duration";
    /**
     * The {@code Volume} column name.
     */
    public static final @NotNull String VOLUME = "Volume";

    /**
     * Creates a {@link DatSchema} instance for {@code arr/sfxdata.dat}.
     *
     * @return the created {@link DatSchema} instance
     */
    public static @NotNull DatSchema createSchema() {
        return new DatSchema()
                .fieldU32(FILE, SOUND_COUNT)
                .fieldU8(PRIORITY, SOUND_COUNT)
                .fieldU8(FLAGS, SOUND_COUNT)
                .fieldS16(PORTRAIT_DURATION, SOUND_COUNT)
                .fieldS8(VOLUME, SOUND_COUNT);
    }

    /**
     * Creates a {@link ScrSoundTypeView} instance over the columns of a parsed table; later writes to the table (EUD) are visible through it. An id
     * outside a column reads {@link DatTable#readNativeElement(String, int)}; the game memory layout of the file is not verified, so it throws
     * {@link ArrayIndexOutOfBoundsException}. A column the table lacks (a partial schema) reads as an empty column, whose accessors throw
     * {@link IllegalArgumentException} for every id.
     *
     * @param sfxdata the parsed {@code sfxdata.dat} table
     * @return the created {@link ScrSoundTypeView} instance
     */
    public static @NotNull ScrSoundTypeView createTypeTable(@NotNull DatTable sfxdata) {
        return new ScrSoundTypeView(column -> sfxdata.getColumnOrEmpty(getColumnName(column)),
                                    (column, index) -> sfxdata.readNativeElement(getColumnName(column), index));
    }

    /**
     * @param column a column of the sound type table
     * @return the sfxdata.dat field name of the column
     */
    private static @NotNull String getColumnName(ScrSoundTypeView.@NotNull Column column) {
        return switch (column) {
            case FILE -> FILE;
            case PRIORITY -> PRIORITY;
            case FLAGS -> FLAGS;
            case PORTRAIT_DURATION -> PORTRAIT_DURATION;
            case VOLUME -> VOLUME;
        };
    }


    private SfxdataDat() {
    }
}
