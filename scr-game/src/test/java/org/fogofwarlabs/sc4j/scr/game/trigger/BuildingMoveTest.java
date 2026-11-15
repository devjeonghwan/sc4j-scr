package org.fogofwarlabs.sc4j.scr.game.trigger;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.game.production.PlacementResult;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.MapTileFlags;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

/**
 * Checks building relocation, footprint occupancy and creep at the new site.
 */
final class BuildingMoveTest {
    private static final int HOST_TICK_INTERVAL = 42;

    private static final int MAP_EXTENT = 4096;

    private static final int DEPOT_FRAME = 3000;
    private static final int DEPOT_SEARCH_START_RADIUS = 4;
    private static final int DEPOT_SEARCH_END_RADIUS = 16;
    private static final int SPAWNING_POOL_FRAME = 4000;
    private static final int SPAWNING_POOL_SEARCH_START_RADIUS = 12;
    private static final int SPAWNING_POOL_SEARCH_END_RADIUS = 40;
    private static final int SETTLE_FRAMES = 30;

    /**
     * The divisor from a placement box extent to the distance between its edge and its center.
     */
    private static final int POSITION_CENTER_DIVISOR = 2;

    /**
     * The value of the building power refresh gate after a refresh request.
     */
    private static final int POWER_REFRESH_REQUESTED = 1;

    /**
     * The placement result code of a footprint that another unit occupies.
     */
    private static final int PLACEMENT_BLOCKED_CODE = 4;

    /**
     * The tile group of a creep tile.
     */
    private static final int CREEP_TILE_GROUP = 16;
    /**
     * The tile group bits of a tile identifier.
     */
    private static final int TILE_GROUP_MASK = 0x7FF0;

    @Test
    void clamp_to_map_and_snap_to_tile_grid_keep_extents_inside_map() {
        Assertions.assertEquals(20, UnitBuildingMove.clampToMap(5, 20, 20, MAP_EXTENT));
        // The right extent reaches the map edge: X = mapWidth - right - 1.
        Assertions.assertEquals(MAP_EXTENT - 20 - 1, UnitBuildingMove.clampToMap(4090, 20, 20, MAP_EXTENT));
        Assertions.assertEquals(1000, UnitBuildingMove.clampToMap(1000, 20, 20, MAP_EXTENT));
        Assertions.assertEquals(1000 - 32 - (1000 - 32) % 32 + 32, UnitBuildingMove.snapToTileGrid(1000, 32, MAP_EXTENT));
        Assertions.assertEquals(48, UnitBuildingMove.snapToTileGrid(10, 48, MAP_EXTENT), "A left edge below 0 becomes 0.");
    }

    @RequiresInstallation
    @Test
    void terran_building_move_relocates_occupied_footprint() throws Exception {
        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(GameTestResources.REPLAY_TVT_BAEKDUDAEGAN)));

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            byte[] mapData = replay.getMapData();

            Assertions.assertNotNull(mapData);

            ReplayRuntime runtime = new ReplayRuntime(replay, ChkFile.parseTolerant(mapData), gameData,
                                                      RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                      RunningGameScenario.INSTRUCTION_LIMIT);

            runtime.start(0, 0);

            Assertions.assertNull(runtime.getFailure());

            Game game = runtime.getGame();
            int tick = 0;

            while (game.getCurrentFrame() < DEPOT_FRAME) {
                Assertions.assertTrue(runtime.step(HOST_TICK_INTERVAL, tick += HOST_TICK_INTERVAL));
            }

            UnitPool pool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(pool);

            Unit depot = null;

            for (int index = pool.getFirstCreatedIndex(true); index != 0 && depot == null; index = pool.getNextCreatedIndex(index)) {
                Unit unit = pool.getUnit(index);

                if (unit.getUnitId() == ScrUnitIds.TERRAN_SUPPLY_DEPOT && (unit.getStatus() & UnitStatusFlags.COMPLETED) != 0) {
                    depot = unit;
                }
            }

            Assertions.assertNotNull(depot, "A completed Supply Depot exists by frame 3000.");

            int previousX = depot.getPositionX();
            int previousY = depot.getPositionY();
            int type = depot.getUnitId();
            GameData placementData = game.getGameData();

            Assertions.assertNotNull(placementData);

            int halfWidth = placementData.getUnits().get("Placement Box Width", type) / POSITION_CENTER_DIVISOR;
            int halfHeight = placementData.getUnits().get("Placement Box Height", type) / POSITION_CENTER_DIVISOR;
            int targetX = -1;
            int targetY = -1;

            for (int radius = DEPOT_SEARCH_START_RADIUS; radius <= DEPOT_SEARCH_END_RADIUS && targetX < 0; radius++) {
                for (int[] direction : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int x = previousX + direction[0] * radius * TerrainTileset.TILE_SIZE;
                    int y = previousY + direction[1] * radius * TerrainTileset.TILE_SIZE;
                    int snappedX = UnitBuildingMove.snapToTileGrid(x, halfWidth, game.getMap().getWidth() * TerrainTileset.TILE_SIZE);
                    int snappedY = UnitBuildingMove.snapToTileGrid(y, halfHeight, game.getMap().getHeight() * TerrainTileset.TILE_SIZE);

                    if (game.getUnitPlacement().evaluateTriggerMove(depot.getOwnerIndex(), type,
                                                                    snappedX / TerrainTileset.TILE_SIZE - halfWidth / TerrainTileset.TILE_SIZE,
                                                                    snappedY / TerrainTileset.TILE_SIZE -
                                                                    halfHeight / TerrainTileset.TILE_SIZE) == PlacementResult.VALID) {
                        targetX = x;
                        targetY = y;

                        break;
                    }
                }
            }

            Assertions.assertTrue(targetX >= 0, "An open site exists near the depot.");
            Assertions.assertEquals(PLACEMENT_BLOCKED_CODE,
                                    game.getUnitPlacement().evaluateTriggerMove(depot.getOwnerIndex(), type,
                                                                                previousX / TerrainTileset.TILE_SIZE -
                                                                                halfWidth / TerrainTileset.TILE_SIZE,
                                                                                previousY / TerrainTileset.TILE_SIZE -
                                                                                halfHeight / TerrainTileset.TILE_SIZE),
                                    "The depot's own occupied footprint blocks (flag 0x08000000 in the terrain mask).");

            game.getUnitBuildingMove().move(depot, targetX, targetY, RunningGameScenario.INSTRUCTION_LIMIT);

            int relocatedX = depot.getPositionX();
            int relocatedY = depot.getPositionY();

            Assertions.assertEquals(UnitBuildingMove.snapToTileGrid(targetX, halfWidth, game.getMap().getWidth() * TerrainTileset.TILE_SIZE),
                                    relocatedX);
            Assertions.assertEquals(UnitBuildingMove.snapToTileGrid(targetY, halfHeight, game.getMap().getHeight() * TerrainTileset.TILE_SIZE),
                                    relocatedY);

            int width = game.getMap().getWidth();

            Assertions.assertEquals(0,
                                    game.getMap().getTileFlags((previousY / TerrainTileset.TILE_SIZE) * width +
                                                               previousX / TerrainTileset.TILE_SIZE) & MapTileFlags.OCCUPIED,
                                    "The old footprint is vacated.");
            Assertions.assertNotEquals(0,
                                       game.getMap().getTileFlags((relocatedY / TerrainTileset.TILE_SIZE) * width +
                                                                  relocatedX / TerrainTileset.TILE_SIZE) & MapTileFlags.OCCUPIED,
                                       "The new footprint is occupied.");

            int until = game.getCurrentFrame() + SETTLE_FRAMES;

            while (game.getCurrentFrame() < until) {
                Assertions.assertTrue(runtime.step(HOST_TICK_INTERVAL, tick += HOST_TICK_INTERVAL));
                Assertions.assertNull(runtime.getFailure());
            }
        }
    }

    @RequiresInstallation
    @Test
    void zerg_building_move_fills_creep_and_refreshes_power_at_new_site() throws Exception {
        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(GameTestResources.REPLAY_ZVZ_HEARTBREAK_RIDGE)));

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            byte[] mapData = replay.getMapData();

            Assertions.assertNotNull(mapData);

            ReplayRuntime runtime = new ReplayRuntime(replay, ChkFile.parseTolerant(mapData), gameData,
                                                      RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                      RunningGameScenario.INSTRUCTION_LIMIT);

            runtime.start(0, 0);

            Assertions.assertNull(runtime.getFailure());

            Game game = runtime.getGame();
            int tick = 0;

            while (game.getCurrentFrame() < SPAWNING_POOL_FRAME) {
                Assertions.assertTrue(runtime.step(HOST_TICK_INTERVAL, tick += HOST_TICK_INTERVAL));
            }

            UnitPool pool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(pool);

            Unit spawningPool = null;

            for (int index = pool.getFirstCreatedIndex(true); index != 0 && spawningPool == null; index = pool.getNextCreatedIndex(index)) {
                Unit unit = pool.getUnit(index);

                if (unit.getUnitId() == ScrUnitIds.ZERG_SPAWNING_POOL && (unit.getStatus() & UnitStatusFlags.COMPLETED) != 0) {
                    spawningPool = unit;
                }
            }

            Assertions.assertNotNull(spawningPool, "A completed Spawning Pool exists by frame 4000.");

            int type = spawningPool.getUnitId();
            GameData placementData = game.getGameData();

            Assertions.assertNotNull(placementData);

            int halfWidth = placementData.getUnits().get("Placement Box Width", type) / POSITION_CENTER_DIVISOR;
            int halfHeight = placementData.getUnits().get("Placement Box Height", type) / POSITION_CENTER_DIVISOR;
            int mapWidth = game.getMap().getWidth();
            int targetX = -1;
            int targetY = -1;

            for (int radius = SPAWNING_POOL_SEARCH_START_RADIUS; radius <= SPAWNING_POOL_SEARCH_END_RADIUS && targetX < 0; radius++) {
                for (int[] direction : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int x = spawningPool.getPositionX() + direction[0] * radius * TerrainTileset.TILE_SIZE;
                    int y = spawningPool.getPositionY() + direction[1] * radius * TerrainTileset.TILE_SIZE;
                    int snappedX = UnitBuildingMove.snapToTileGrid(x, halfWidth, mapWidth * TerrainTileset.TILE_SIZE);
                    int snappedY = UnitBuildingMove.snapToTileGrid(y, halfHeight, game.getMap().getHeight() * TerrainTileset.TILE_SIZE);
                    int centerTile = (snappedY / TerrainTileset.TILE_SIZE) * mapWidth + snappedX / TerrainTileset.TILE_SIZE;

                    if (!TerrainTileset.isCreepTile(game.getMap().getTileIdentifier(centerTile)) &&
                        game.getUnitPlacement().evaluateTriggerMove(spawningPool.getOwnerIndex(), type,
                                                                    snappedX / TerrainTileset.TILE_SIZE - halfWidth / TerrainTileset.TILE_SIZE,
                                                                    snappedY / TerrainTileset.TILE_SIZE -
                                                                    halfHeight / TerrainTileset.TILE_SIZE) == PlacementResult.VALID) {
                        targetX = x;
                        targetY = y;

                        break;
                    }
                }
            }

            Assertions.assertTrue(targetX >= 0, "An open site without creep exists.");

            game.getUnitBuildingMove().move(spawningPool, targetX, targetY, RunningGameScenario.INSTRUCTION_LIMIT);

            int centerTile = (spawningPool.getPositionY() / TerrainTileset.TILE_SIZE) * mapWidth + spawningPool.getPositionX() / TerrainTileset.TILE_SIZE;

            Assertions.assertEquals(CREEP_TILE_GROUP, game.getMap().getTileIdentifier(centerTile) & TILE_GROUP_MASK,
                                    "Moving the building fills creep under it.");
            Assertions.assertEquals(POWER_REFRESH_REQUESTED, game.getBuildingPowerRefresh(), "Moving the building requests the power refresh.");

            int until = game.getCurrentFrame() + SETTLE_FRAMES;

            while (game.getCurrentFrame() < until) {
                Assertions.assertTrue(runtime.step(HOST_TICK_INTERVAL, tick += HOST_TICK_INTERVAL));
                Assertions.assertNull(runtime.getFailure());
            }
        }
    }
}
