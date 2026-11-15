package org.fogofwarlabs.sc4j.scr.game.save.section;

import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.game.creep.CreepField;
import org.fogofwarlabs.sc4j.scr.game.save.SaveFileReader;
import org.fogofwarlabs.sc4j.scr.game.save.SaveFileWriter;
import org.fogofwarlabs.sc4j.scr.game.save.SaveGameContext;
import org.fogofwarlabs.sc4j.scr.game.save.SaveSection;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Writes and restores fixed tile, graphics cache and tile flag arrays.
 * <p>
 * The arrays have the fixed size of a 256 x 256 map and keep the map's tiles row-major from their start: the MTXM tile
 * WORDs ({@code 0x20000} bytes), the graphics cache WORDs (the megatile each tile shows,
 * {@link CreepField#getGraphicsCache}) and the tile flag DWORDs ({@code 0x40000} bytes, {@link GameMap#getStoredTileFlags}).
 * Past the map's tiles, a new game writes zero WORDs and {@link GameMap#UNUSED_TILE_FLAGS}; a loaded game retains the saved
 * array bytes ({@link GameMap#retainSavedArrays}).
 */
public final class MapArraysSection implements SaveSection {
    /**
     * The number of tiles of the fixed arrays (256 x 256).
     */
    public static final int TILE_CAPACITY = GameMap.MAXIMUM_TILE_COUNT;

    /**
     * Starts an array from the bytes a loaded game's reader filled, or from zeros.
     *
     * @param map   the map
     * @param array the array index
     * @param size  the array size in bytes
     * @return the array, little-endian
     */
    private static @NotNull ByteBuffer createSavedArray(@NotNull GameMap map, int array, int size) {
        byte[] saved = map.getSavedArray(array);

        return ByteBuffer.wrap(saved == null ? new byte[size] : saved).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static @NotNull TerrainTileset readTileset(@NotNull Game game) {
        try {
            return game.requireGameData().getTerrainTileset(game.getMap().getTileset());
        } catch (IOException exception) {
            throw SaveFileReader.createFailure("The tileset {0} cannot be loaded: {1}", game.getMap().getTileset(), exception.getMessage());
        }
    }


    /**
     * Creates a {@link MapArraysSection} instance.
     */
    public MapArraysSection() {
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
        GameMap map = game.getMap();
        TerrainTileset tileset = readTileset(game);
        int tileCount = map.getWidth() * map.getHeight();
        ByteBuffer tiles = createSavedArray(map, GameMap.SAVED_TILES, TILE_CAPACITY * Short.BYTES);
        ByteBuffer megatiles = createSavedArray(map, GameMap.SAVED_MEGATILES, TILE_CAPACITY * Short.BYTES);
        ByteBuffer flags = ByteBuffer.allocate(TILE_CAPACITY * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        CreepField creep = game.getCreepField();

        for (int tileIndex = 0; tileIndex < TILE_CAPACITY; tileIndex++) {
            if (tileIndex < tileCount) {
                int tile = map.getTileIdentifier(tileIndex);

                tiles.putShort(tileIndex * Short.BYTES, (short) tile);

                int megatile = creep == null ? tileset.getMegatileIdentifier(tile) : creep.getGraphicsCache(tileIndex);

                megatiles.putShort(tileIndex * Short.BYTES, (short) megatile);
            }

            flags.putInt(tileIndex * Integer.BYTES, map.getStoredTileFlags(tileIndex));
        }

        output.writeSection(tiles.array());
        output.writeSection(megatiles.array());
        output.writeSection(flags.array());
    }

    /**
     * Reads the part into the restored game.
     *
     * @param context the restored game
     * @param input   the file input
     */
    @Override
    public void read(@NotNull SaveGameContext context, @NotNull SaveFileReader input) {
        GameMap map = context.getGame().getMap();
        int tileCount = map.getWidth() * map.getHeight();
        byte[] tileArray = input.readSection(TILE_CAPACITY * Short.BYTES);
        byte[] megatileArray = input.readSection(TILE_CAPACITY * Short.BYTES);
        byte[] flagArray = input.readSection(TILE_CAPACITY * Integer.BYTES);
        ByteBuffer tiles = ByteBuffer.wrap(tileArray).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer megatiles = ByteBuffer.wrap(megatileArray).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer flags = ByteBuffer.wrap(flagArray).order(ByteOrder.LITTLE_ENDIAN);
        CreepField creep = context.getGame().getCreepField();

        map.retainSavedArrays(tileArray, megatileArray, flagArray);

        for (int tileIndex = 0; tileIndex < tileCount; tileIndex++) {
            map.restoreTileIdentifier(tileIndex, tiles.getShort(tileIndex * Short.BYTES));

            if (creep != null) {
                creep.restoreGraphicsCache(tileIndex, megatiles.getShort(tileIndex * Short.BYTES));
            }

            map.restoreTileFlags(tileIndex, flags.getInt(tileIndex * Integer.BYTES));
        }
    }
}
