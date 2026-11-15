package org.fogofwarlabs.sc4j.scr.game.path;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.terrain.MapTileFlags;
import org.fogofwarlabs.sc4j.scr.game.terrain.TerrainContours;
import org.fogofwarlabs.sc4j.scr.game.terrain.TerrainRegionLookupUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitInitializer;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitSpatialIndex;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;

/**
 * Checks that a path update continues an exhausted single-region route in its path record.
 */
@RequiresInstallation
final class UnitPathUpdateTest {
    /**
     * The width and height of the test map in tiles.
     */
    private static final int MAP_SIDE_TILES = 32;
    private static final int TILE_COUNT = MAP_SIDE_TILES * MAP_SIDE_TILES;
    /**
     * The number of terrain tile groups, the miniature flag bytes and the walkable miniature tiles of the parsed tileset.
     */
    private static final int TILE_GROUP_COUNT = 3;
    private static final int MINIATURE_FLAG_BYTES = 32;
    private static final int WALKABLE_MINIATURE_TILES = 16;

    /**
     * The horizontal and vertical position of the Drone and the horizontal position of its destination.
     */
    private static final int UNIT_POSITION = 512;
    private static final int DESTINATION_X = 700;
    /**
     * The shift of the vertical WORD in a packed position.
     */
    private static final int POSITION_VERTICAL_SHIFT = 16;
    /**
     * The last collision handle of the path record.
     */
    private static final int COLLISION_HANDLE = 0x1234;
    /**
     * The packed destination and last waypoint of the updated path record: the horizontal {@code 700} and the vertical {@code 512}.
     */
    private static final int PACKED_DESTINATION = 0x020002BC;

    private static @Nullable GameData gameData;

    @BeforeAll
    static void loadGameData() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
    }

    @AfterAll
    static void closeGameData() {
        if (gameData != null) {
            gameData.close();
        }
    }


    @Test
    void update_should_reuse_exhausted_single_region_path_and_keep_collision_handle() throws IOException {
        int[] flags = new int[TILE_COUNT];

        Arrays.fill(flags, MapTileFlags.WALKABLE);

        GameMap map = new GameMap(MAP_SIDE_TILES, MAP_SIDE_TILES, 0, new int[TILE_COUNT], flags);
        Game game = Game.create(gameData, map, new LcgRandom(1));

        game.getUnitSystem().initializeUnitPool(1);
        game.initializeUnitSpatialIndex();
        game.getPathSystem().setPathPool(new PathPool());
        game.getPathSystem().setUnitPath(new UnitPath());
        game.getPathSystem().setLocalPathSearch(new LocalPathSearch());

        byte[] miniatureFlags = new byte[MINIATURE_FLAG_BYTES];

        for (int index = 0; index < WALKABLE_MINIATURE_TILES; index++) {
            miniatureFlags[index * Short.BYTES] = TerrainTileset.MINI_TILE_WALKABLE;
        }

        TerrainTileset tileset = TerrainTileset.parse(new byte[TILE_GROUP_COUNT * TerrainTileset.CV5_RECORD_SIZE], miniatureFlags);

        game.getPathSystem().setTerrainContours(TerrainContours.create(map, tileset));

        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit unit = unitPool.getUnit(1);

        unit.setUnitId(ScrUnitIds.ZERG_DRONE);
        unit.setStatus(UnitStatusFlags.COMPLETED | UnitStatusFlags.GROUND_UNIT);
        unit.setPathCollisionFlags(UnitInitializer.GROUND_ELEVATION_PATH_FLAG);
        unit.setPositionX(UNIT_POSITION);
        unit.setPositionY(UNIT_POSITION);
        unit.setSprite(SpriteScenario.createSprite(0, UNIT_POSITION, UNIT_POSITION, 0));
        unit.setMovementDestinationX(DESTINATION_X);
        unit.setMovementDestinationY(UNIT_POSITION);

        UnitSpatialIndex insertionIndex = game.getUnitSystem().getUnitSpatialIndex();

        Assertions.assertNotNull(insertionIndex);
        Assertions.assertNotNull(gameData);

        insertionIndex.insert(unit, gameData.getUnitTypes());

        PathPool paths = game.getPathSystem().getPathPool();

        Assertions.assertNotNull(paths);

        int pathIndex = paths.allocate();

        unit.setPathIndex(pathIndex);
        paths.setWaypointPosition(pathIndex, UNIT_POSITION | UNIT_POSITION << POSITION_VERTICAL_SHIFT);
        paths.setWaypointCount(pathIndex, 1);
        paths.setWaypointIndex(pathIndex, 0);
        paths.setRegionCount(pathIndex, 1);
        paths.setRegionIndex(pathIndex, 0);
        paths.setFullRegionCount(pathIndex, 1);
        paths.setDestination(pathIndex, DESTINATION_X | UNIT_POSITION << POSITION_VERTICAL_SHIFT);
        paths.setLastCollisionHandle(pathIndex, COLLISION_HANDLE);

        PathRequest request = new PathRequest();
        TerrainContours routeContours = game.getPathSystem().getTerrainContours();

        Assertions.assertNotNull(routeContours);

        request.setRegion(0, (short) TerrainRegionLookupUtils.lookup(routeContours.getRegions(), UNIT_POSITION, UNIT_POSITION));
        paths.copyRegionsFrom(request, pathIndex, 1, 1);

        UnitPath updateUnitPath = game.getPathSystem().getUnitPath();

        Assertions.assertNotNull(updateUnitPath);

        Assertions.assertTrue(updateUnitPath.update(game, unit, DESTINATION_X | UNIT_POSITION << POSITION_VERTICAL_SHIFT));

        Assertions.assertEquals(pathIndex, unit.getPathIndex());
        Assertions.assertEquals(COLLISION_HANDLE, paths.getLastCollisionHandle(pathIndex));
        Assertions.assertEquals(PACKED_DESTINATION, paths.getDestination(pathIndex));
        Assertions.assertTrue(unit.getMovementWaypointX() > UNIT_POSITION && unit.getMovementWaypointX() <= DESTINATION_X);
        Assertions.assertEquals(UNIT_POSITION, unit.getMovementWaypointY());
        Assertions.assertEquals(paths.getWaypoint(pathIndex, 0),
                                unit.getMovementWaypointX() | unit.getMovementWaypointY() << POSITION_VERTICAL_SHIFT);
        Assertions.assertEquals(PACKED_DESTINATION, paths.getWaypoint(pathIndex, paths.getWaypointCount(pathIndex) - 1));
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }
}
