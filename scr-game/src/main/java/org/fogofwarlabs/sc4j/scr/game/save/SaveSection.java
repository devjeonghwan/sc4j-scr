package org.fogofwarlabs.sc4j.scr.game.save;

import org.jetbrains.annotations.NotNull;

/**
 * Writes and reads one saved-game part in the same order and grouping.
 */
public interface SaveSection {
    /**
     * Writes the part from the saved game.
     *
     * @param context the saved game
     * @param output  the file output
     */
    void write(@NotNull SaveGameContext context, @NotNull SaveFileWriter output);

    /**
     * Reads the part into the restored game.
     *
     * @param context the restored game
     * @param input   the file input
     */
    void read(@NotNull SaveGameContext context, @NotNull SaveFileReader input);
}
