package org.fogofwarlabs.sc4j.scr.game.movement;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.game.path.PathPool;
import org.fogofwarlabs.sc4j.scr.game.path.UnitPath;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.terrain.MapTileFlags;
import org.fogofwarlabs.sc4j.scr.game.terrain.TerrainContours;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitInitializer;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitSpatialIndex;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.util.Arrays;

/**
 * Creates walkable test terrain, a map whose tiles and miniatures are all walkable with its terrain contours, and the stacked-unit world on it: a
 * Drone and an Egg at one position, which the unstacking and the unit finder tests separate.
 */
public final class WalkableTerrainScenario {
    /**
     * The pool index of the stacked Drone in the stacked-unit world.
     */
    public static final int UNIT_POOL_INDEX = 1;
    /**
     * The pool index of the Egg that blocks the Drone in the stacked-unit world.
     */
    public static final int BLOCKER_POOL_INDEX = 2;

    /**
     * The terrain groups of the smallest tileset.
     */
    static final int TILESET_GROUP_COUNT = 3;
    /**
     * The bytes of the miniature flag WORDs of one megatile.
     */
    static final int MEGATILE_FLAGS_SIZE = TerrainTileset.MINI_TILES_PER_ROW * TerrainTileset.MINI_TILES_PER_ROW * Short.BYTES;

    /**
     * The width and height in tiles of the map of the stacked-unit world.
     */
    private static final int STACK_MAP_DIMENSION = 64;
    /**
     * The unit count of the stacked-unit world.
     */
    private static final int STACK_UNIT_COUNT = 2;
    /**
     * The horizontal and vertical position of both units of the stacked-unit world.
     */
    private static final int STACK_POSITION = 512;

    /**
     * Creates a {@link GameMap} instance whose tiles are all walkable.
     *
     * @param dimension the width and height in tiles
     * @return the created {@link GameMap} instance
     */
    public static @NotNull GameMap createWalkableMap(int dimension) {
        int[] flags = new int[dimension * dimension];

        Arrays.fill(flags, MapTileFlags.WALKABLE);

        return new GameMap(dimension, dimension, 0, new int[dimension * dimension], flags);
    }

    /**
     * Creates a {@link TerrainContours} instance for a map whose megatiles have only walkable miniatures.
     *
     * @param map the map
     * @return the created {@link TerrainContours} instance
     * @throws IOException if the tileset input cannot be parsed
     */
    public static @NotNull TerrainContours createWalkableContours(@NotNull GameMap map) throws IOException {
        byte[] miniatureFlags = new byte[MEGATILE_FLAGS_SIZE];

        for (int miniatureIndex = 0; miniatureIndex < MEGATILE_FLAGS_SIZE / Short.BYTES; miniatureIndex++) {
            miniatureFlags[miniatureIndex * Short.BYTES] = TerrainTileset.MINI_TILE_WALKABLE;
        }

        return TerrainContours.create(map, TerrainTileset.parse(new byte[TILESET_GROUP_COUNT * TerrainTileset.CV5_RECORD_SIZE], miniatureFlags));
    }

    /**
     * Creates a {@link Game} instance of the stacked-unit world: a walkable map with its contours, a path pool and a unit path, and a completed
     * ground Drone ({@link #UNIT_POOL_INDEX}) and Egg ({@link #BLOCKER_POOL_INDEX}) at the same position without a path, both in the spatial index.
     *
     * @param gameData the installed data
     * @return the created {@link Game} instance
     * @throws IOException if the tileset input cannot be parsed
     */
    public static @NotNull Game createStackedUnitGame(@NotNull GameData gameData) throws IOException {
        GameMap map = createWalkableMap(STACK_MAP_DIMENSION);
        Game game = Game.create(gameData, map, new LcgRandom(0));

        game.getUnitSystem().initializeUnitPool(STACK_UNIT_COUNT);
        game.initializeUnitSpatialIndex();
        game.getPathSystem().setPathPool(new PathPool());
        game.getPathSystem().setUnitPath(new UnitPath());
        game.getPathSystem().setTerrainContours(createWalkableContours(map));

        UnitPool unitPool = game.getUnitSystem().getUnitPool();
        UnitSpatialIndex spatialIndex = game.getUnitSystem().getUnitSpatialIndex();

        Assertions.assertNotNull(unitPool);
        Assertions.assertNotNull(spatialIndex);

        for (int index = 1; index <= STACK_UNIT_COUNT; index++) {
            Unit unit = unitPool.getUnit(index);

            unit.setUnitId(index == UNIT_POOL_INDEX ? ScrUnitIds.ZERG_DRONE : ScrUnitIds.ZERG_EGG);
            unit.setStatus(UnitStatusFlags.COMPLETED | UnitStatusFlags.GROUND_UNIT);
            unit.setPathCollisionFlags(UnitInitializer.GROUND_ELEVATION_PATH_FLAG);
            unit.setPositionX(STACK_POSITION);
            unit.setPositionY(STACK_POSITION);
            unit.setMovementDestinationX(STACK_POSITION);
            unit.setMovementDestinationY(STACK_POSITION);
            unit.setSprite(SpriteScenario.createSprite(0, STACK_POSITION, STACK_POSITION, 0));
            unit.setPathIndex(-1);
            spatialIndex.insert(unit, gameData.getUnitTypes());
        }

        return game;
    }


    /**
     * Creates a {@link WalkableTerrainScenario} instance.
     */
    private WalkableTerrainScenario() {
    }
}
