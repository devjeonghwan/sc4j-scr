package org.fogofwarlabs.sc4j.scr.game.save.section;

import org.fogofwarlabs.sc4j.scr.game.save.*;
import org.fogofwarlabs.sc4j.scr.game.terrain.Foliage;
import org.jetbrains.annotations.NotNull;

import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Writes and restores the foliage grid and settles its saved animations.
 * <p>
 * The plain tag {@code FOLI} and a plain DWORD {@link #VERSION} precede one zlib stream (level 6, no length) of the
 * foliage grid: WORD grid width, WORD grid height, DWORD their product, then 65536 cells of 24 bytes
 * ({@link Foliage#writeSaveCell}).
 */
public final class FoliageSection implements SaveSection {
    /**
     * The tag {@code FOLI}.
     */
    private static final int TAG = 'F' | 'O' << Byte.SIZE | 'L' << Short.SIZE | 'I' << (Integer.SIZE - Byte.SIZE);
    /**
     * The foliage format version.
     */
    private static final int VERSION = 1;
    private static final int GRID_WIDTH = 0x00;
    private static final int GRID_HEIGHT = 0x02;
    private static final int GRID_CELLS = 0x04;
    private static final int HEADER_SIZE = GRID_CELLS + Integer.BYTES;
    private static final int SIZE = HEADER_SIZE + Foliage.SAVED_CELL_COUNT * Foliage.SAVED_CELL_SIZE;
    private static final int WORD_MASK = 0xFFFF;

    /**
     * Creates a {@link FoliageSection} instance.
     */
    public FoliageSection() {
    }

    /**
     * Writes the part from the saved game.
     *
     * @param context the saved game
     * @param output  the file output
     */
    @Override
    public void write(@NotNull SaveGameContext context, @NotNull SaveFileWriter output) {
        Foliage foliage = SaveGameUtils.require(context.getGame().getFoliage(), "foliage");
        ByteBuffer grid = ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN);

        grid.putShort(GRID_WIDTH, (short) foliage.getGridWidth());
        grid.putShort(GRID_HEIGHT, (short) foliage.getGridHeight());
        grid.putInt(GRID_CELLS, foliage.getGridWidth() * foliage.getGridHeight());

        for (int cell = 0; cell < Foliage.SAVED_CELL_COUNT; cell++) {
            foliage.writeSaveCell(cell,
                                  grid.slice(HEADER_SIZE + cell * Foliage.SAVED_CELL_SIZE, Foliage.SAVED_CELL_SIZE).order(ByteOrder.LITTLE_ENDIAN));
        }

        output.writeDword(TAG);
        output.writeDword(VERSION);
        output.writeStream(grid.array());
    }

    /**
     * Reads the part into the restored game.
     *
     * @param context the restored game
     * @param input   the file input
     * @throws UncheckedIOException if the saved foliage has another tag or version
     */
    @Override
    public void read(@NotNull SaveGameContext context, @NotNull SaveFileReader input) {
        Foliage foliage = SaveGameUtils.require(context.getGame().getFoliage(), "foliage");
        int tag = input.readDword();
        int version = input.readDword();

        if (tag != TAG || version != VERSION) {
            throw SaveFileReader.createFailure("The saved foliage has the tag 0x{0} and version {1}.", Integer.toHexString(tag), version);
        }

        ByteBuffer grid = ByteBuffer.wrap(input.readStream(SIZE)).order(ByteOrder.LITTLE_ENDIAN);

        foliage.restoreSavedGrid(grid.getShort(GRID_WIDTH) & WORD_MASK, grid.getShort(GRID_HEIGHT) & WORD_MASK);

        for (int cell = 0; cell < Foliage.SAVED_CELL_COUNT; cell++) {
            foliage.readSaveCell(cell,
                                 grid.slice(HEADER_SIZE + cell * Foliage.SAVED_CELL_SIZE, Foliage.SAVED_CELL_SIZE).order(ByteOrder.LITTLE_ENDIAN));
        }

        foliage.settleSavedAnimations();
    }
}
