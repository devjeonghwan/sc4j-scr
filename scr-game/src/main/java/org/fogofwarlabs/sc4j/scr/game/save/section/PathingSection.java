package org.fogofwarlabs.sc4j.scr.game.save.section;

import org.fogofwarlabs.sc4j.scr.game.path.PathPool;
import org.fogofwarlabs.sc4j.scr.game.save.*;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.terrain.TerrainContours;
import org.fogofwarlabs.sc4j.scr.game.terrain.TerrainRegions;
import org.jetbrains.annotations.NotNull;

import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Writes and restores a size-prefixed section of terrain regions, contours and path records.
 * <p>
 * The section starts with the format WORD {@code 0x0E}, the WORD that says the contours follow and the DWORD free
 * path head (index + 1, {@code 0} for none); the regions ({@link TerrainRegions#writeSaveData}), the contours
 * ({@link TerrainContours#writeSaveData}) and the path records ({@link PathPool#writeSaveRecords}) follow.
 */
public final class PathingSection implements SaveSection {
    /**
     * The format WORD of the section.
     */
    private static final int FORMAT = 0x0E;
    /**
     * The contours WORD of a section whose contours follow.
     */
    private static final int CONTOURS_PRESENT = 1;
    /**
     * The size of the section header: the format WORD, the contours WORD and the free path head DWORD.
     */
    private static final int HEADER_SIZE = 2 * Short.BYTES + Integer.BYTES;
    private static final int WORD_MASK = 0xFFFF;

    /**
     * Creates a {@link PathingSection} instance.
     */
    public PathingSection() {
    }

    /**
     * Writes the part from the saved game.
     *
     * @param context the saved game
     * @param output  the file output
     * @throws UnsupportedOperationException if the path pool is not initialized
     */
    @Override
    public void write(@NotNull SaveGameContext context, @NotNull SaveFileWriter output) {
        Game game = context.getGame();
        TerrainContours contours = SaveGameUtils.require(game.getPathSystem().getTerrainContours(), "terrain contours");
        PathPool paths = game.getPathSystem().requirePathPool();
        int contoursOffset = HEADER_SIZE + TerrainRegions.SAVED_SIZE;
        int pathsOffset = contoursOffset + contours.getSavedSize();
        ByteBuffer data = ByteBuffer.allocate(pathsOffset + PathPool.SAVED_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        data.putShort((short) FORMAT);
        data.putShort((short) CONTOURS_PRESENT);
        data.putInt(paths.getFirstFreeIndex() + 1);
        contours.getRegions().writeSaveData(data.slice(HEADER_SIZE, TerrainRegions.SAVED_SIZE).order(ByteOrder.LITTLE_ENDIAN));
        contours.writeSaveData(data.slice(contoursOffset, pathsOffset - contoursOffset).order(ByteOrder.LITTLE_ENDIAN));
        paths.writeSaveRecords(data.slice(pathsOffset, PathPool.SAVED_SIZE).order(ByteOrder.LITTLE_ENDIAN));

        output.writeDword(data.capacity());
        output.writeSection(data.array());
    }

    /**
     * Reads the part into the restored game.
     *
     * @param context the restored game
     * @param input   the file input
     * @throws UncheckedIOException          if the saved section has another format or no contours
     * @throws UnsupportedOperationException if the path pool is not initialized
     */
    @Override
    public void read(@NotNull SaveGameContext context, @NotNull SaveFileReader input) {
        Game game = context.getGame();
        TerrainContours contours = SaveGameUtils.require(game.getPathSystem().getTerrainContours(), "terrain contours");
        int size = input.readDword();
        ByteBuffer data = ByteBuffer.wrap(input.readSection(size)).order(ByteOrder.LITTLE_ENDIAN);
        int format = data.getShort() & WORD_MASK;
        int contoursWord = data.getShort() & WORD_MASK;
        int firstFree = data.getInt();

        if (format != FORMAT || contoursWord != CONTOURS_PRESENT) {
            throw SaveFileReader.createFailure("The saved pathing has format {0} with contours {1}.", format, contoursWord);
        }

        PathPool paths = game.getPathSystem().requirePathPool();

        contours.getRegions().readSaveData(data.slice(HEADER_SIZE, TerrainRegions.SAVED_SIZE).order(ByteOrder.LITTLE_ENDIAN));
        contours.readSaveData(data.slice(HEADER_SIZE + TerrainRegions.SAVED_SIZE, size - HEADER_SIZE - TerrainRegions.SAVED_SIZE)
                                  .order(ByteOrder.LITTLE_ENDIAN));
        paths.readSaveRecords(data.slice(size - PathPool.SAVED_SIZE, PathPool.SAVED_SIZE).order(ByteOrder.LITTLE_ENDIAN), firstFree - 1);
    }
}
