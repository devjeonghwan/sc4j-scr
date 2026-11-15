package org.fogofwarlabs.sc4j.scr.game.save.section;

import org.fogofwarlabs.sc4j.scr.game.save.SaveFileReader;
import org.fogofwarlabs.sc4j.scr.game.save.SaveFileWriter;
import org.fogofwarlabs.sc4j.scr.game.save.SaveGameContext;
import org.fogofwarlabs.sc4j.scr.game.save.SaveSection;
import org.jetbrains.annotations.NotNull;

/**
 * Writes an empty extension block between triggers and selections.
 * A nonzero saved DWORD size is followed by a section that is read and discarded.
 */
public final class ExtensionBlockSection implements SaveSection {
    /**
     * Creates an {@link ExtensionBlockSection} instance.
     */
    public ExtensionBlockSection() {
    }

    /**
     * Writes the part from the saved game.
     *
     * @param context the saved game
     * @param output  the file output
     */
    @Override
    public void write(@NotNull SaveGameContext context, @NotNull SaveFileWriter output) {
        output.writeDword(0);
    }

    /**
     * Reads the part into the restored game.
     *
     * @param context the restored game
     * @param input   the file input
     */
    @Override
    public void read(@NotNull SaveGameContext context, @NotNull SaveFileReader input) {
        int size = input.readDword();

        if (size != 0) {
            input.readSection(size);
        }
    }
}
