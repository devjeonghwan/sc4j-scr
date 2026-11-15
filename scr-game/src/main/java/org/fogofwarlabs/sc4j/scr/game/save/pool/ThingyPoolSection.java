package org.fogofwarlabs.sc4j.scr.game.save.pool;

import org.fogofwarlabs.sc4j.scr.game.frame.GameFrame;
import org.fogofwarlabs.sc4j.scr.game.save.*;
import org.fogofwarlabs.sc4j.scr.game.sprite.*;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Writes and restores the thingy list ({@link SpriteSystem#getSpriteEffectPool()}) and then the secondary list
 * ({@link GameFrame#getSecondarySpriteEffects()}).
 * <p>
 * Per list a plain WORD counts the thingies with a sprite; a section then holds one 18-byte entry per such thingy in slot
 * order: the WORD slot, the one-based list links, the life DWORD and the one-based sprite. The reader clears each list
 * and restores the saved thingies, then rebuilds every free and active list in ascending slot order
 * ({@link SpriteEffectPool#rebuildLists}).
 */
public final class ThingyPoolSection implements SaveSection {
    private static final int ENTRY_PREVIOUS = 0x02;
    private static final int ENTRY_NEXT = 0x06;
    private static final int ENTRY_LIFE = 0x0A;
    private static final int ENTRY_SPRITE = 0x0E;

    /**
     * The size of an entry: the WORD slot, the two links, the life and the sprite.
     */
    public static final int ENTRY_SIZE = ENTRY_SPRITE + Integer.BYTES;

    private static final int WORD_MASK = 0xFFFF;

    private static void writeList(@NotNull SaveFileWriter output, @NotNull SpriteEffectPool thingies) {
        int count = 0;

        for (int poolIndex = 0; poolIndex < thingies.getCapacity(); poolIndex++) {
            if (thingies.getSpriteEffect(poolIndex).getSpriteOrNull() != null) {
                count++;
            }
        }

        output.writeWord(count);

        if (count == 0) {
            return;
        }

        ByteBuffer entries = ByteBuffer.allocate(count * ENTRY_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        for (int poolIndex = 0; poolIndex < thingies.getCapacity(); poolIndex++) {
            SpriteEffect thingy = thingies.getSpriteEffect(poolIndex);
            Sprite sprite = thingy.getSpriteOrNull();

            if (sprite == null) {
                continue;
            }

            int entry = entries.position();

            entries.putShort(entry, (short) poolIndex);
            entries.putInt(entry + ENTRY_PREVIOUS, thingies.getPreviousIndex(poolIndex) + 1);
            entries.putInt(entry + ENTRY_NEXT, thingies.getNextIndex(poolIndex) + 1);
            entries.putInt(entry + ENTRY_LIFE, thingy.getLife());
            entries.putInt(entry + ENTRY_SPRITE, SaveReferences.encodeSprite(sprite));
            entries.position(entry + ENTRY_SIZE);
        }

        output.writeSection(entries.array());
    }

    private static void readList(@NotNull SaveFileReader input, @NotNull SpriteEffectPool thingies, @NotNull SpritePool sprites) {
        int count = input.readWord();

        if (count >= thingies.getCapacity()) {
            throw SaveFileReader.createFailure("The saved game has {0} thingies for a list of {1}.", count, thingies.getCapacity());
        }

        thingies.clear();

        if (count == 0) {
            return;
        }

        ByteBuffer entries = ByteBuffer.wrap(input.readSection(count * ENTRY_SIZE)).order(ByteOrder.LITTLE_ENDIAN);

        for (int entryIndex = 0; entryIndex < count; entryIndex++) {
            int entry = entryIndex * ENTRY_SIZE;
            int poolIndex = entries.getShort(entry) & WORD_MASK;

            if (poolIndex >= thingies.getCapacity()) {
                throw SaveFileReader.createFailure("A saved sprite effect names the slot {0} of a list of {1}.", poolIndex, thingies.getCapacity());
            }

            thingies.restoreSavedSpriteEffect(poolIndex,
                                              entries.getInt(entry + ENTRY_LIFE),
                                              decodeSprite(sprites, entries.getInt(entry + ENTRY_SPRITE) - 1));
        }
    }

    private static @Nullable Sprite decodeSprite(@NotNull SpritePool sprites, int poolIndex) {
        if (poolIndex < 0) {
            return null;
        }

        if (poolIndex >= sprites.getCapacity()) {
            throw SaveFileReader.createFailure("A saved sprite effect names the sprite slot {0}.", poolIndex);
        }

        return sprites.getSprite(poolIndex);
    }


    /**
     * Creates a {@link ThingyPoolSection} instance.
     */
    public ThingyPoolSection() {
    }

    /**
     * Writes the part from the saved game.
     *
     * @param context the saved game
     * @param output  the file output
     */
    @Override
    public void write(@NotNull SaveGameContext context, @NotNull SaveFileWriter output) {
        Game game = context.getGame();

        writeList(output, SaveGameUtils.require(game.getSpriteSystem().getSpriteEffectPool(), "sprite effect pool"));
        writeList(output, SaveGameUtils.require(game.getFrame(), "game frame").getSecondarySpriteEffects());
    }

    /**
     * Reads the part into the restored game.
     *
     * @param context the restored game
     * @param input   the file input
     * @throws IllegalStateException if the game has no sprite pool, sprite effect pool or game frame
     * @throws UncheckedIOException  if a saved sprite effect count, sprite effect slot or sprite slot does not fit its pool
     */
    @Override
    public void read(@NotNull SaveGameContext context, @NotNull SaveFileReader input) {
        Game game = context.getGame();
        SpritePool sprites = SaveGameUtils.require(game.getSpriteSystem().getSpritePool(), "sprite pool");
        SpriteEffectPool thingies = SaveGameUtils.require(game.getSpriteSystem().getSpriteEffectPool(), "sprite effect pool");
        SpriteEffectPool secondarySpriteEffects = SaveGameUtils.require(game.getFrame(), "game frame").getSecondarySpriteEffects();

        readList(input, thingies, sprites);
        readList(input, secondarySpriteEffects, sprites);

        thingies.rebuildLists();
        secondarySpriteEffects.rebuildLists();
    }
}
