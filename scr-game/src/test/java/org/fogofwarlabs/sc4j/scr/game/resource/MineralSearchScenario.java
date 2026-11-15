package org.fogofwarlabs.sc4j.scr.game.resource;

import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.game.path.PathPool;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.terrain.MapTileFlags;
import org.fogofwarlabs.sc4j.scr.game.terrain.TerrainContours;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitInitializer;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitSpatialIndex;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.util.Arrays;

/**
 * Prepares the small mineral search games that the computer town searches and the worker orders share.
 */
public final class MineralSearchScenario {
    /**
     * The width and height of the mineral search map in tiles.
     */
    public static final int MAP_SIZE = 16;

    private static final int UNIT_CAPACITY = 4;
    private static final int ORDER_CAPACITY = 16;
    private static final int MINIATURE_TILES = 16;
    private static final int TERRAIN_GROUP_COUNT = 3;
    private static final int MINIATURE_FLAG_STRIDE = 2;

    /**
     * Creates tile flags that make every tile of the mineral search map walkable.
     *
     * @return the created tile flags, one per tile in row order
     */
    public static int @NotNull [] createWalkableFlags() {
        int[] flags = new int[MAP_SIZE * MAP_SIZE];

        Arrays.fill(flags, MapTileFlags.WALKABLE);

        return flags;
    }

    /**
     * Creates a {@link TerrainTileset} instance of three empty terrain groups whose first sixteen miniature tiles are walkable.
     *
     * @return the created {@link TerrainTileset} instance
     * @throws IOException if the tileset data cannot be read
     */
    public static @NotNull TerrainTileset createWalkableTileset() throws IOException {
        byte[] miniatureFlags = new byte[MINIATURE_TILES * MINIATURE_FLAG_STRIDE];

        for (int index = 0; index < MINIATURE_TILES; index++) {
            miniatureFlags[index * MINIATURE_FLAG_STRIDE] = TerrainTileset.MINI_TILE_WALKABLE;
        }

        return TerrainTileset.parse(new byte[TERRAIN_GROUP_COUNT * TerrainTileset.CV5_RECORD_SIZE], miniatureFlags);
    }

    /**
     * Creates a mineral search game: the map with the supplied tile flags, walkable terrain contours of one region
     * component, the unit pool and spatial index, unit accounting, the order pool and the path pool.
     *
     * @param gameData the installed game data
     * @param flags    the tile flags, one per tile in row order
     * @return the created {@link Game} instance
     * @throws IOException if the tileset data cannot be read
     */
    public static @NotNull Game createGame(@NotNull GameData gameData, int @NotNull [] flags) throws IOException {
        GameMap map = new GameMap(MAP_SIZE, MAP_SIZE, 0, new int[MAP_SIZE * MAP_SIZE], flags);

        map.initializeOverlayTiles();

        Game game = Game.create(gameData, map, new LcgRandom(0));

        game.getUnitSystem().initializeUnitPool(UNIT_CAPACITY);
        game.initializeUnitSpatialIndex();
        game.getPlayerTable().setUnitAccounting(PlayerProgressTestUtils.createUnitAccounting());
        game.initializeOrderPool(ORDER_CAPACITY);
        game.getPathSystem().setPathPool(new PathPool());
        game.getPathSystem().setTerrainContours(TerrainContours.create(map, createWalkableTileset()));

        return game;
    }

    /**
     * Places a ground unit with the ground elevation path flag in a pool slot of a mineral search game and inserts it
     * into the spatial index.
     *
     * @param game      the mineral search game
     * @param poolIndex the one-based pool index
     * @param type      the unit type
     * @param owner     the owning player
     * @param positionX the horizontal sprite position
     * @param positionY the vertical sprite position
     * @return the placed unit
     */
    public static @NotNull Unit placeUnit(@NotNull Game game, int poolIndex, int type, int owner, int positionX, int positionY) {
        UnitPool pool = game.getUnitSystem().getUnitPool();
        UnitSpatialIndex spatialIndex = game.getUnitSystem().getUnitSpatialIndex();

        Assertions.assertNotNull(pool);
        Assertions.assertNotNull(spatialIndex);

        Unit unit = pool.getUnit(poolIndex);

        unit.setUnitId(type);
        unit.setOwnerIndex(owner);
        unit.setSprite(SpriteScenario.createSprite(0, positionX, positionY, 0));
        unit.setPathCollisionFlags(UnitInitializer.GROUND_ELEVATION_PATH_FLAG);
        spatialIndex.insert(unit, game.requireGameData().getUnitTypes());

        return unit;
    }


    private MineralSearchScenario() {
    }
}
