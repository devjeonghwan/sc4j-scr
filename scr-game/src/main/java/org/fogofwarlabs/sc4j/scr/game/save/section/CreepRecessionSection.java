package org.fogofwarlabs.sc4j.scr.game.save.section;

import org.fogofwarlabs.sc4j.scr.game.creep.CreepRecession;
import org.fogofwarlabs.sc4j.scr.game.save.*;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Writes and restores four creep recession sections ({@link CreepRecession#writeSaveSections}).
 */
public final class CreepRecessionSection implements SaveSection {
    /**
     * The size of the record section.
     */
    public static final int RECORDS_SIZE = CreepRecession.RECORD_COUNT * CreepRecession.SAVED_RECORD_SIZE;
    /**
     * The size of the bucket section.
     */
    public static final int BUCKETS_SIZE = CreepRecession.BUCKET_COUNT * Integer.BYTES;
    /**
     * The size of the list head section.
     */
    public static final int LISTS_SIZE = CreepRecession.SAVED_LIST_COUNT * Integer.BYTES;
    /**
     * The size of the list count section.
     */
    public static final int COUNTS_SIZE = CreepRecession.SAVED_LIST_COUNT * Short.BYTES;

    /**
     * Creates a {@link CreepRecessionSection} instance.
     */
    public CreepRecessionSection() {
    }

    /**
     * Writes the part from the saved game.
     *
     * @param context the saved game
     * @param output  the file output
     */
    @Override
    public void write(@NotNull SaveGameContext context, @NotNull SaveFileWriter output) {
        CreepRecession recession = SaveGameUtils.require(context.getGame().getCreepRecession(), "creep recession");
        ByteBuffer records = ByteBuffer.allocate(RECORDS_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer buckets = ByteBuffer.allocate(BUCKETS_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer lists = ByteBuffer.allocate(LISTS_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer counts = ByteBuffer.allocate(COUNTS_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        recession.writeSaveSections(records, buckets, lists, counts);

        output.writeSection(records.array());
        output.writeSection(buckets.array());
        output.writeSection(lists.array());
        output.writeSection(counts.array());
    }

    /**
     * Reads the part into the restored game.
     *
     * @param context the restored game
     * @param input   the file input
     */
    @Override
    public void read(@NotNull SaveGameContext context, @NotNull SaveFileReader input) {
        Game game = context.getGame();
        CreepRecession recession = SaveGameUtils.require(game.getCreepRecession(), "creep recession");
        ByteBuffer records = ByteBuffer.wrap(input.readSection(RECORDS_SIZE)).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer buckets = ByteBuffer.wrap(input.readSection(BUCKETS_SIZE)).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer lists = ByteBuffer.wrap(input.readSection(LISTS_SIZE)).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer counts = ByteBuffer.wrap(input.readSection(COUNTS_SIZE)).order(ByteOrder.LITTLE_ENDIAN);

        recession.readSaveSections(records, buckets, lists, counts, game.getMap());
    }
}
