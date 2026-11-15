package org.fogofwarlabs.sc4j.scr.game.save.section;

import org.fogofwarlabs.sc4j.scr.game.save.*;
import org.fogofwarlabs.sc4j.scr.game.trigger.TriggerContext;
import org.fogofwarlabs.sc4j.scr.game.trigger.TriggerRecordCodec;
import org.jetbrains.annotations.NotNull;

/**
 * Writes and restores running trigger records and unit properties.
 * <p>
 * A plain WORD counts the running records of players {@code 0}..{@code 7}; a section then holds the initially-active
 * bytes, the per-player counts and the records ({@link TriggerContext#writeSaveRecords}); a {@code 0x500}-byte section of
 * the map's unit property records follows.
 */
public final class TriggerSection implements SaveSection {
    /**
     * Creates a {@link TriggerSection} instance.
     */
    public TriggerSection() {
    }

    /**
     * Writes the part from the saved game.
     *
     * @param context the saved game
     * @param output  the file output
     */
    @Override
    public void write(@NotNull SaveGameContext context, @NotNull SaveFileWriter output) {
        TriggerContext triggers = SaveGameUtils.require(context.getGame().getTriggerContext(), "trigger context");

        output.writeWord(triggers.getSavedRecordCount());
        output.writeSection(triggers.writeSaveRecords());
        output.writeSection(triggers.getSavedUnitProperties());
    }

    /**
     * Reads the part into the restored game.
     *
     * @param context the restored game
     * @param input   the file input
     */
    @Override
    public void read(@NotNull SaveGameContext context, @NotNull SaveFileReader input) {
        TriggerContext triggers = SaveGameUtils.require(context.getGame().getTriggerContext(), "trigger context");
        int count = input.readWord();

        triggers.readSaveRecords(input.readSection(TriggerContext.SAVED_RECORDS_HEADER_SIZE + count * TriggerRecordCodec.RECORD_SIZE));
        triggers.restoreSavedUnitProperties(input.readSection(TriggerContext.UNIT_PROPERTIES_SIZE));
    }
}
