package org.fogofwarlabs.sc4j.scr.game.save.pool;

import org.fogofwarlabs.sc4j.scr.game.save.*;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImagePool;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpritePool;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameSetup;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes and restores sprite records, image links and map rows.
 * <p>
 * A plain WORD counts the sprites with images (an image tail); a section then holds one entry per such sprite in slot
 * order: the WORD slot and the classic 36-byte sprite ({@link Sprite#writeSaveRecord}) whose row links and primary, head
 * and tail images are one-based indices. Two plain arrays of 256 DWORDs follow, the one-based heads and tails of the map
 * rows (rows past the map height zero). Maps higher than 256 rows are rejected. The reader clears the pool, restores
 * the saved sprites and rows, links every other slot into the free list in ascending order and stores each slot's index
 * into the sprite's index WORD.
 */
public final class SpritePoolSection implements SaveSection {
    /**
     * The size of an entry: the WORD slot and the record.
     */
    public static final int ENTRY_SIZE = Short.BYTES + Sprite.SAVE_RECORD_SIZE;

    /**
     * The number of rows of the row arrays.
     */
    private static final int ROW_COUNT = GameMap.MAXIMUM_DIMENSION;
    /**
     * The size of the two plain row arrays after the section: the heads, then the tails.
     */
    public static final int ROW_ARRAYS_SIZE = 2 * ROW_COUNT * Integer.BYTES;

    private static final int RECORD_PREVIOUS = 0x00;
    private static final int RECORD_NEXT = 0x04;
    private static final int RECORD_MAIN_IMAGE = 0x18;
    private static final int RECORD_FIRST_IMAGE = 0x1C;
    private static final int RECORD_LAST_IMAGE = 0x20;
    private static final int WORD_MASK = 0xFFFF;

    private static @Nullable SpriteImage decodeImage(@NotNull SpriteImagePool images, int poolIndex) {
        if (poolIndex < 0) {
            return null;
        }

        if (poolIndex >= images.getCapacity()) {
            throw SaveFileReader.createFailure("A saved sprite names the image slot {0}.", poolIndex);
        }

        return images.getImage(poolIndex);
    }

    private static byte @NotNull [] createRowArray(@NotNull SpritePool sprites, int height, boolean first) {
        ByteBuffer rows = ByteBuffer.allocate(ROW_COUNT * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);

        for (int row = 0; row < height; row++) {
            rows.putInt(row * Integer.BYTES, (first ? sprites.getFirstRowIndex(row) : sprites.getLastRowIndex(row)) + 1);
        }

        return rows.array();
    }


    private final @NotNull ImagePoolSection imageSection;

    /**
     * Creates a {@link SpritePoolSection} instance.
     *
     * @param imageSection the image part read before, which keeps the saved image links
     */
    public SpritePoolSection(@NotNull ImagePoolSection imageSection) {
        this.imageSection = imageSection;
    }

    /**
     * Writes the part from the saved game.
     *
     * @param context the saved game
     * @param output  the file output
     * @throws UncheckedIOException if the map has more rows than the row arrays hold
     */
    @Override
    public void write(@NotNull SaveGameContext context, @NotNull SaveFileWriter output) {
        Game game = context.getGame();
        SpritePool sprites = SaveGameUtils.require(game.getSpriteSystem().getSpritePool(), "sprite pool");
        int height = game.getMap().getHeight();
        int capacity = SaveGameUtils.getNativeCapacity(game, GameSetup.SPRITE_LIMIT, sprites.getCapacity());
        int count = 0;

        for (int poolIndex = 0; poolIndex < capacity; poolIndex++) {
            if (!sprites.getSprite(poolIndex).getImages().isEmpty()) {
                count++;
            }
        }

        output.writeWord(count);

        if (count > 0) {
            ByteBuffer entries = ByteBuffer.allocate(count * ENTRY_SIZE).order(ByteOrder.LITTLE_ENDIAN);

            for (int poolIndex = 0; poolIndex < capacity; poolIndex++) {
                Sprite sprite = sprites.getSprite(poolIndex);

                if (sprite.getImages().isEmpty()) {
                    continue;
                }

                int entry = entries.position();
                ByteBuffer record = entries.slice(entry + Short.BYTES, Sprite.SAVE_RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN);
                List<SpriteImage> images = sprite.getImages();

                entries.putShort(entry, (short) poolIndex);
                sprite.writeSaveRecord(record);
                record.putInt(RECORD_PREVIOUS, sprites.getPreviousRowIndex(poolIndex) + 1);
                record.putInt(RECORD_NEXT, sprites.getNextRowIndex(poolIndex) + 1);
                record.putInt(RECORD_MAIN_IMAGE, ImagePoolSection.encodeImageIndex(sprite.getMainImage()));
                record.putInt(RECORD_FIRST_IMAGE, ImagePoolSection.encodeImageIndex(images.getFirst()));
                record.putInt(RECORD_LAST_IMAGE, ImagePoolSection.encodeImageIndex(images.getLast()));
                entries.position(entry + ENTRY_SIZE);
            }

            output.writeSection(entries.array());
        }

        if (height > ROW_COUNT) {
            throw SaveFileReader.createFailure("A map of {0} rows cannot be saved.", height);
        }

        output.writeBytes(createRowArray(sprites, height, true));
        output.writeBytes(createRowArray(sprites, height, false));
    }

    /**
     * Reads the part into the restored game.
     *
     * @param context the restored game
     * @param input   the file input
     * @throws UncheckedIOException if a saved slot does not fit the sprite pool or the map has more rows than the row arrays hold
     */
    @Override
    public void read(@NotNull SaveGameContext context, @NotNull SaveFileReader input) {
        Game game = context.getGame();
        SpritePool sprites = SaveGameUtils.require(game.getSpriteSystem().getSpritePool(), "sprite pool");
        SpriteImagePool images = SaveGameUtils.require(game.getSpriteSystem().getImagePool(), "image pool");
        GameMap map = game.getMap();
        int count = input.readWord();

        sprites.clear();

        if (count > 0) {
            ByteBuffer entries = ByteBuffer.wrap(input.readSection(count * ENTRY_SIZE)).order(ByteOrder.LITTLE_ENDIAN);

            for (int entryIndex = 0; entryIndex < count; entryIndex++) {
                int entry = entryIndex * ENTRY_SIZE;
                int poolIndex = entries.getShort(entry) & WORD_MASK;
                ByteBuffer record = entries.slice(entry + Short.BYTES, Sprite.SAVE_RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN);

                if (poolIndex >= sprites.getCapacity()) {
                    throw SaveFileReader.createFailure("A saved sprite names the slot {0} of a pool of {1}.", poolIndex, sprites.getCapacity());
                }

                Sprite sprite = sprites.getSprite(poolIndex);

                sprite.readSaveRecord(record);
                sprites.markLoaded(poolIndex, record.getInt(RECORD_PREVIOUS) - 1, record.getInt(RECORD_NEXT) - 1);
                sprite.restoreSavedImages(this.createImageList(images, record.getInt(RECORD_FIRST_IMAGE) - 1),
                                          decodeImage(images, record.getInt(RECORD_MAIN_IMAGE) - 1));
            }
        }

        sprites.rebuildFreeList();

        if (map.getHeight() > ROW_COUNT) {
            throw SaveFileReader.createFailure("A map of {0} rows cannot be loaded.", map.getHeight());
        }

        ByteBuffer firstRows = ByteBuffer.wrap(input.readBytes(ROW_COUNT * Integer.BYTES)).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer lastRows = ByteBuffer.wrap(input.readBytes(ROW_COUNT * Integer.BYTES)).order(ByteOrder.LITTLE_ENDIAN);

        for (int row = 0; row < map.getHeight(); row++) {
            sprites.restoreRow(row, firstRows.getInt(row * Integer.BYTES) - 1, lastRows.getInt(row * Integer.BYTES) - 1);
        }
    }

    private @NotNull List<SpriteImage> createImageList(@NotNull SpriteImagePool images, int firstIndex) {
        List<SpriteImage> list = new ArrayList<>();

        for (int poolIndex = firstIndex; poolIndex >= 0; poolIndex = this.imageSection.getNextImageIndex(poolIndex)) {
            if (poolIndex >= images.getCapacity() || list.size() >= images.getCapacity()) {
                throw SaveFileReader.createFailure("A saved sprite's image list is broken at the slot {0}.", poolIndex);
            }

            list.add(images.getImage(poolIndex));
        }

        return list;
    }
}
