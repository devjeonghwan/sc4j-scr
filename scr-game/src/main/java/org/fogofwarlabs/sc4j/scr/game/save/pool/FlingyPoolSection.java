package org.fogofwarlabs.sc4j.scr.game.save.pool;

import org.fogofwarlabs.sc4j.scr.game.save.SaveFileReader;
import org.fogofwarlabs.sc4j.scr.game.save.SaveFileWriter;
import org.fogofwarlabs.sc4j.scr.game.save.SaveGameContext;
import org.fogofwarlabs.sc4j.scr.game.save.SaveSection;
import org.jetbrains.annotations.NotNull;

import java.io.UncheckedIOException;

/**
 * Writes an empty flingy part and rejects saved flingy records that the simulation cannot represent.
 */
public final class FlingyPoolSection implements SaveSection {
    /**
     * The size of a classic flingy record.
     */
    private static final int RECORD_SIZE = 0x4C;
    /**
     * The size of an entry: the WORD slot and the record.
     */
    public static final int ENTRY_SIZE = Short.BYTES + RECORD_SIZE;

    /**
     * Creates a {@link FlingyPoolSection} instance.
     */
    public FlingyPoolSection() {
    }

    /**
     * Writes the part from the saved game.
     *
     * @param context the saved game
     * @param output  the file output
     */
    @Override
    public void write(@NotNull SaveGameContext context, @NotNull SaveFileWriter output) {
        output.writeWord(0);
    }

    /**
     * Reads the part into the restored game.
     *
     * @param context the restored game
     * @param input   the file input
     * @throws UncheckedIOException if the saved game keeps flingy records
     */
    @Override
    public void read(@NotNull SaveGameContext context, @NotNull SaveFileReader input) {
        int count = input.readWord();

        if (count != 0) {
            throw SaveFileReader.createFailure("The saved game keeps {0} flingies, which no game creates.", count);
        }
    }
}
