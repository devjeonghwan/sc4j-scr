package org.fogofwarlabs.sc4j.scr.game.save.pool;

import org.fogofwarlabs.sc4j.scr.game.save.*;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImagePool;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.state.GameSetup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Writes and restores saved image records and their one-based links.
 * <p>
 * A plain WORD counts the images with graphics (a GRP pointer); a section then holds one entry per such image in slot
 * order: the WORD slot and the classic {@code 64}-byte image ({@link SpriteImage#writeSaveRecord}) whose list links (the
 * neighbors in the sprite's image list) and sprite are one-based indices. The reader clears the whole pool, restores the
 * saved images into their slots and links every other slot into the free list in ascending order; the sprites part then
 * rebuilds each sprite's image list from the saved links ({@link #getNextImageIndex}).
 */
public final class ImagePoolSection implements SaveSection {
    /**
     * The size of an entry: the WORD slot and the record.
     */
    public static final int ENTRY_SIZE = Short.BYTES + SpriteImage.SAVE_RECORD_SIZE;

    private static final int RECORD_PREVIOUS = 0x00;
    private static final int RECORD_NEXT = 0x04;
    private static final int RECORD_SPRITE = 0x3C;
    private static final int WORD_MASK = 0xFFFF;

    /**
     * Encodes an image as its one-based slot, the link value of the saved image and sprite records.
     *
     * @param image an image, or {@code null}
     * @return its one-based slot, or {@code 0}
     */
    static int encodeImageIndex(@Nullable SpriteImage image) {
        return image == null ? 0 : image.getIndex() + 1;
    }


    /**
     * The one-based next links of the read images by slot, for {@link SpritePoolSection}.
     */
    private int @NotNull [] nextImageIndices;

    /**
     * Creates an {@link ImagePoolSection} instance.
     */
    public ImagePoolSection() {
        this.nextImageIndices = new int[0];
    }

    /**
     * Returns the saved next link of a read image.
     *
     * @param poolIndex the zero-based slot of the image
     * @return the zero-based slot of the next image in its sprite's list, or {@code -1} for none
     */
    int getNextImageIndex(int poolIndex) {
        return this.nextImageIndices[poolIndex] - 1;
    }

    /**
     * Writes the part from the saved game.
     *
     * @param context the saved game
     * @param output  the file output
     * @throws NullPointerException if the world was built without game data
     */
    @Override
    public void write(@NotNull SaveGameContext context, @NotNull SaveFileWriter output) {
        Game game = context.getGame();
        SpriteImagePool images = SaveGameUtils.require(game.getSpriteSystem().getImagePool(), "image pool");
        GameData gameData = game.requireGameData();
        int capacity = SaveGameUtils.getNativeCapacity(game, GameSetup.IMAGE_LIMIT, images.getCapacity());
        int count = 0;

        for (int poolIndex = 0; poolIndex < capacity; poolIndex++) {
            if (images.getImage(poolIndex).hasGraphics()) {
                count++;
            }
        }

        output.writeWord(count);

        if (count == 0) {
            return;
        }

        ByteBuffer entries = ByteBuffer.allocate(count * ENTRY_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        for (int poolIndex = 0; poolIndex < capacity; poolIndex++) {
            SpriteImage image = images.getImage(poolIndex);

            if (!image.hasGraphics()) {
                continue;
            }

            int entry = entries.position();
            ByteBuffer record = entries.slice(entry + Short.BYTES, SpriteImage.SAVE_RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN);
            Sprite sprite = image.getSprite();
            List<SpriteImage> siblings = sprite == null ? List.of() : sprite.getImages();
            int listIndex = siblings.indexOf(image);

            entries.putShort(entry, (short) poolIndex);
            image.writeSaveRecord(record, gameData);
            record.putInt(RECORD_PREVIOUS, encodeImageIndex(listIndex > 0 ? siblings.get(listIndex - 1) : null));
            record.putInt(RECORD_NEXT, encodeImageIndex(listIndex >= 0 && listIndex + 1 < siblings.size() ? siblings.get(listIndex + 1) : null));
            record.putInt(RECORD_SPRITE, SaveReferences.encodeSprite(sprite));
            entries.position(entry + ENTRY_SIZE);
        }

        output.writeSection(entries.array());
    }

    /**
     * Reads the part into the restored game.
     *
     * @param context the restored game
     * @param input   the file input
     * @throws UncheckedIOException if the saved image count or a saved slot does not fit the image pool
     * @throws NullPointerException if the world was built without game data
     */
    @Override
    public void read(@NotNull SaveGameContext context, @NotNull SaveFileReader input) {
        Game game = context.getGame();
        SpriteImagePool images = SaveGameUtils.require(game.getSpriteSystem().getImagePool(), "image pool");
        GameData gameData = game.requireGameData();
        int count = input.readWord();

        if (count >= images.getCapacity()) {
            throw SaveFileReader.createFailure("The saved game has {0} images for a pool of {1}.", count, images.getCapacity());
        }

        images.clear();
        this.nextImageIndices = new int[images.getCapacity()];

        if (count == 0) {
            images.rebuildFreeList();

            return;
        }

        ByteBuffer entries = ByteBuffer.wrap(input.readSection(count * ENTRY_SIZE)).order(ByteOrder.LITTLE_ENDIAN);

        for (int entryIndex = 0; entryIndex < count; entryIndex++) {
            int entry = entryIndex * ENTRY_SIZE;
            int poolIndex = entries.getShort(entry) & WORD_MASK;
            ByteBuffer record = entries.slice(entry + Short.BYTES, SpriteImage.SAVE_RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN);

            if (poolIndex >= images.getCapacity()) {
                throw SaveFileReader.createFailure("A saved image names the slot {0} of a pool of {1}.", poolIndex, images.getCapacity());
            }

            images.getImage(poolIndex).readSaveRecord(record, gameData);
            images.markLoaded(poolIndex);
            this.nextImageIndices[poolIndex] = record.getInt(RECORD_NEXT);
        }

        images.rebuildFreeList();
    }
}
