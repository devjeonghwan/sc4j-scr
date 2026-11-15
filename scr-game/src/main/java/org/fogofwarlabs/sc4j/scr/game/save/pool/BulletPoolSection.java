package org.fogofwarlabs.sc4j.scr.game.save.pool;

import org.fogofwarlabs.sc4j.scr.game.combat.ProjectilePhase;
import org.fogofwarlabs.sc4j.scr.game.save.*;
import org.fogofwarlabs.sc4j.scr.game.state.GameSetup;
import org.jetbrains.annotations.NotNull;

import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Writes and restores saved bullet records in pool slot order.
 * <p>
 * A plain WORD counts the bullets with a sprite; a section then holds one entry per such bullet in slot order: the
 * WORD slot and the classic 112-byte bullet ({@link ProjectilePhase.Context#writeSaveRecord}). The reader clears the
 * pool, restores the saved bullets and rebuilds the free and active lists in ascending order.
 */
public final class BulletPoolSection implements SaveSection {
    /**
     * The size of an entry: the WORD slot and the record.
     */
    public static final int ENTRY_SIZE = Short.BYTES + ProjectilePhase.SAVE_RECORD_SIZE;

    private static final int WORD_MASK = 0xFFFF;

    /**
     * Creates a {@link BulletPoolSection} instance.
     */
    public BulletPoolSection() {
    }

    /**
     * Writes the part from the saved game.
     *
     * @param context the saved game
     * @param output  the file output
     */
    @Override
    public void write(@NotNull SaveGameContext context, @NotNull SaveFileWriter output) {
        ProjectilePhase.Context bullets = SaveGameUtils.require(context.getGame().getFrame(), "game frame").getProjectiles();
        SaveReferences references = new SaveReferences(context.getGame());
        int capacity = SaveGameUtils.getNativeCapacity(context.getGame(), GameSetup.BULLET_LIMIT, bullets.getCapacity());
        int count = 0;

        for (int index = 0; index < capacity; index++) {
            if (bullets.hasSprite(index)) {
                count++;
            }
        }

        output.writeWord(count);

        if (count == 0) {
            return;
        }

        ByteBuffer entries = ByteBuffer.allocate(count * ENTRY_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        for (int index = 0; index < capacity; index++) {
            if (!bullets.hasSprite(index)) {
                continue;
            }

            int entry = entries.position();

            entries.putShort(entry, (short) index);
            bullets.writeSaveRecord(index,
                                    entries.slice(entry + Short.BYTES, ProjectilePhase.SAVE_RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN),
                                    references);
            entries.position(entry + ENTRY_SIZE);
        }

        output.writeSection(entries.array());
    }

    /**
     * Reads the part into the restored game.
     *
     * @param context the restored game
     * @param input   the file input
     * @throws UncheckedIOException if the saved bullet count or a saved slot does not fit the bullet pool
     * @throws NullPointerException if the world was built without game data
     */
    @Override
    public void read(@NotNull SaveGameContext context, @NotNull SaveFileReader input) {
        ProjectilePhase.Context bullets = SaveGameUtils.require(context.getGame().getFrame(), "game frame").getProjectiles();
        SaveReferences references = new SaveReferences(context.getGame());
        int count = input.readWord();

        bullets.clear();

        if (count >= bullets.getCapacity()) {
            throw SaveFileReader.createFailure("The saved game has {0} bullets for a pool of {1}.", count, bullets.getCapacity());
        }

        if (count > 0) {
            ByteBuffer entries = ByteBuffer.wrap(input.readSection(count * ENTRY_SIZE)).order(ByteOrder.LITTLE_ENDIAN);

            for (int entryIndex = 0; entryIndex < count; entryIndex++) {
                int entry = entryIndex * ENTRY_SIZE;
                int index = entries.getShort(entry) & WORD_MASK;

                if (index >= bullets.getCapacity()) {
                    throw SaveFileReader.createFailure("A saved bullet names the slot {0} of a pool of {1}.", index, bullets.getCapacity());
                }

                bullets.readSaveRecord(index,
                                       entries.slice(entry + Short.BYTES, ProjectilePhase.SAVE_RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN),
                                       context.getGame().requireGameData().getWeaponTypes(),
                                       references);
            }
        }

        bullets.rebuildLists();
    }
}
