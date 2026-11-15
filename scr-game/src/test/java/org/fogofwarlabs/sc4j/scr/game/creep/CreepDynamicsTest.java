package org.fogofwarlabs.sc4j.scr.game.creep;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMapScenario;
import org.fogofwarlabs.sc4j.scr.game.terrain.MapTileFlags;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;

/**
 * Verifies creep growth, recession, exhausted-record retries and overlapping providers.
 */
@RequiresInstallation
final class CreepDynamicsTest {
    private static final int MAP_DIMENSION = 64;
    private static final int TILE_COUNT = MAP_DIMENSION * MAP_DIMENSION;
    private static final int PLAIN_TILE = 128;
    private static final int RANDOM_SEED = 0x13572468;
    private static final int CREEP_SEED = 0x2468ACE;
    private static final int UNIT_CAPACITY = 8;
    private static final int CENTER = 1024;
    /**
     * The tile in the creep area that a unit occupies before the creep spreads.
     */
    private static final int BLOCKED_TILE = 32 * MAP_DIMENSION + 37;
    private static final int SPREAD_RANDOM_SOURCE = 26;
    private static final int RECESSION_RANDOM_SOURCE = 27;
    /**
     * The runtime random draws of a spread that adds one tile.
     */
    private static final int ADDED_TILE_RUNTIME_DRAWS = 2;
    private static final int MINIMUM_CREEP_TILES = 20;
    private static final int FRAME_LIMIT = 20000;
    /**
     * The tile group of a creep tile identifier.
     */
    private static final int CREEP_TILE_GROUP = 0x10;
    /**
     * The tile group bits of a tile identifier.
     */
    private static final int TILE_GROUP_MASK = 0x7FF0;

    /**
     * The spread results: no tile added, one tile added, and growth blocked by an occupied tile.
     */
    private static final int SPREAD_NONE = 0;
    private static final int SPREAD_ADDED = 1;
    private static final int SPREAD_BLOCKED = 2;

    /**
     * The number of creep recession records.
     */
    private static final int RECESSION_RECORD_COUNT = 1024;
    /**
     * The recession countdowns by free record count, one per {@code 128} free records.
     */
    private static final int @NotNull [] PERIODS = {1, 3, 5, 6, 7, 8, 9, 9, 9};
    private static final int PERIOD_SHIFT = 7;
    private static final int COUNTDOWN_RETRY_CALLS = 3;

    /**
     * The horizontal distance from the removed provider to the sustaining provider.
     */
    private static final int SUSTAINING_OFFSET = 192;
    /**
     * The half-axes of the Creep Colony creep ellipse and the squared weights that scale them to a circle.
     */
    private static final int CREEP_HORIZONTAL_RADIUS = 320;
    private static final int CREEP_VERTICAL_RADIUS = 200;
    private static final int CREEP_HORIZONTAL_WEIGHT = 25;
    private static final int CREEP_VERTICAL_WEIGHT = 64;
    private static final int TILE_SIZE = 32;
    private static final int HALF_TILE = 16;

    private static @NotNull Game createGame(@NotNull GameData data, int freeRecords) throws IOException {
        int[] tiles = new int[TILE_COUNT];
        int[] flags = new int[TILE_COUNT];
        int[] cache = new int[TILE_COUNT];

        Arrays.fill(tiles, PLAIN_TILE);
        Arrays.fill(flags, MapTileFlags.NOT_VISIBLE_OR_EXPLORED | MapTileFlags.WALKABLE);

        GameMap map = new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, tiles, flags);

        GameMapScenario.setOverlays(map, new int[TILE_COUNT]);

        Game game = Game.create(data, map, new LcgRandom(RANDOM_SEED));

        game.getLocalVision().setComparisonMask(1);
        game.getRuntimeRandom().seed(CREEP_SEED);
        game.setCreepField(new CreepField(map, data.getTerrainTileset(0), cache));
        game.setCreepRecession(new CreepRecession(0, freeRecords, new int[CreepRecession.LIST_COUNT]));
        game.getUnitSystem().initializeUnitPool(UNIT_CAPACITY);
        game.initializeUnitSpatialIndex();
        game.getRandom().setEnabled(1);

        return game;
    }

    private static boolean hasCreep(@NotNull GameMap map, int tileIndex) {
        return (map.getTileIdentifier(tileIndex) & TILE_GROUP_MASK) == CREEP_TILE_GROUP;
    }

    private static int countCreep(@NotNull GameMap map) {
        int count = 0;

        for (int tileIndex = 0; tileIndex < map.getTileCount(); tileIndex++) {
            if (hasCreep(map, tileIndex)) {
                count++;
            }
        }

        return count;
    }

    private static void assertListsMatchNeighbors(@NotNull Game game) {
        GameMap map = game.getMap();
        CreepRecession recession = game.getCreepRecession();
        int total = 0;

        Assertions.assertNotNull(recession);

        for (int list = 0; list < CreepRecession.LIST_COUNT; list++) {
            for (int position = 0; position < recession.getBucketCount(list); position++) {
                int tileIndex = recession.getRecedingTile(list, position);

                Assertions.assertTrue(hasCreep(map, tileIndex));
                Assertions.assertNotEquals(0, map.getTileFlags(tileIndex) & MapTileFlags.CREEP_RECEDING);
                Assertions.assertEquals(list,
                                        CreepRecession.countNeighboringCreepTiles(map, tileIndex % MAP_DIMENSION, tileIndex / MAP_DIMENSION),
                                        "Receding tile " + tileIndex + " is filed under its neighbor count.");

                total++;
            }
        }

        Assertions.assertEquals(RECESSION_RECORD_COUNT - recession.getFreeCount(), total);
    }

    /**
     * Tests whether a tile center offset from a Creep Colony lies in its creep ellipse.
     *
     * @param offsetX the horizontal offset of the tile center from the Creep Colony
     * @param offsetY the vertical offset of the tile center from the Creep Colony
     * @return {@code true} if the offset lies in the creep ellipse, {@code false} otherwise
     */
    private static boolean isInCreepArea(int offsetX, int offsetY) {
        return offsetX * offsetX * CREEP_HORIZONTAL_WEIGHT + offsetY * offsetY * CREEP_VERTICAL_WEIGHT <=
               CREEP_HORIZONTAL_RADIUS * CREEP_HORIZONTAL_RADIUS * CREEP_HORIZONTAL_WEIGHT;
    }

    /**
     * Tests whether a tile lies in the tile bounds of the creep ellipse of a Creep Colony at a position.
     *
     * @param horizontal the horizontal tile index
     * @param vertical   the vertical tile index
     * @param positionX  the x coordinate of the Creep Colony
     * @param positionY  the y coordinate of the Creep Colony
     * @return {@code true} if the tile lies in the tile bounds of the creep ellipse, {@code false} otherwise
     */
    private static boolean isInCreepBounds(int horizontal, int vertical, int positionX, int positionY) {
        return horizontal >= (positionX - CREEP_HORIZONTAL_RADIUS) / TILE_SIZE &&
               horizontal <= (positionX + CREEP_HORIZONTAL_RADIUS) / TILE_SIZE &&
               vertical >= (positionY - CREEP_VERTICAL_RADIUS) / TILE_SIZE &&
               vertical <= (positionY + CREEP_VERTICAL_RADIUS) / TILE_SIZE;
    }


    @Test
    void creep_spreads_after_initialization_except_onto_occupied_tiles() throws Exception {
        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game game = createGame(data, CreepRecession.RECORD_COUNT);
            GameMap map = game.getMap();
            CreepField creep = game.getCreepField();

            map.occupyTile(BLOCKED_TILE);

            Assertions.assertNotNull(creep);

            creep.fill(game, ScrUnitIds.ZERG_CREEP_COLONY, CENTER, CENTER);
            creep.finishInitialization();

            Assertions.assertFalse(hasCreep(map, BLOCKED_TILE));

            int creepTiles = countCreep(map);
            int randomCalls = game.getRandom().getCallCount(SPREAD_RANDOM_SOURCE);
            int runtimeCalls = game.getRuntimeRandom().getDrawCount();

            Assertions.assertEquals(SPREAD_BLOCKED, creep.spread(game, ScrUnitIds.ZERG_CREEP_COLONY, CENTER, CENTER),
                                    "An occupied candidate keeps growth pending.");
            Assertions.assertFalse(creep.growOnce(game, ScrUnitIds.ZERG_CREEP_COLONY, CENTER, CENTER));
            Assertions.assertEquals(randomCalls, game.getRandom().getCallCount(SPREAD_RANDOM_SOURCE));

            map.vacateTile(BLOCKED_TILE);

            Assertions.assertEquals(SPREAD_ADDED, creep.spread(game, ScrUnitIds.ZERG_CREEP_COLONY, CENTER, CENTER),
                                    "Dynamic growth adds the freed tile.");
            Assertions.assertTrue(hasCreep(map, BLOCKED_TILE));
            Assertions.assertEquals(PLAIN_TILE, map.getOverlayTileIdentifier(BLOCKED_TILE));
            Assertions.assertEquals(creepTiles + 1, countCreep(map));
            Assertions.assertEquals(randomCalls + 1, game.getRandom().getCallCount(SPREAD_RANDOM_SOURCE));
            Assertions.assertEquals(runtimeCalls + ADDED_TILE_RUNTIME_DRAWS, game.getRuntimeRandom().getDrawCount());
            Assertions.assertEquals(SPREAD_NONE, creep.spread(game, ScrUnitIds.ZERG_CREEP_COLONY, CENTER, CENTER));
        }
    }

    @Test
    void unsustained_creep_recedes_tile_by_tile_and_restores_terrain_flags() throws Exception {
        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game game = createGame(data, CreepRecession.RECORD_COUNT);
            GameMap map = game.getMap();
            CreepField creep = game.getCreepField();
            CreepRecession recession = game.getCreepRecession();

            Assertions.assertNotNull(creep);

            creep.fill(game, ScrUnitIds.ZERG_CREEP_COLONY, CENTER, CENTER);
            creep.finishInitialization();

            int[] originalFlags = new int[TILE_COUNT];

            for (int tileIndex = 0; tileIndex < TILE_COUNT; tileIndex++) {
                originalFlags[tileIndex] = hasCreep(map, tileIndex) ? -1 : map.getTileFlags(tileIndex);
            }

            int creepTiles = countCreep(map);

            Assertions.assertTrue(creepTiles > MINIMUM_CREEP_TILES);
            Assertions.assertTrue(creep.removeProvider(game, ScrUnitIds.ZERG_CREEP_COLONY, CENTER, CENTER, true));
            Assertions.assertNotNull(recession);
            Assertions.assertEquals(RECESSION_RECORD_COUNT - creepTiles, recession.getFreeCount(), "Every unsustained creep tile recedes.");
            assertListsMatchNeighbors(game);

            creep.addProvider(game, ScrUnitIds.ZERG_CREEP_COLONY, CENTER, CENTER, true);

            Assertions.assertEquals(RECESSION_RECORD_COUNT, recession.getFreeCount(), "A new provider stops recession in its area.");

            for (int tileIndex = 0; tileIndex < TILE_COUNT; tileIndex++) {
                Assertions.assertEquals(0, map.getTileFlags(tileIndex) & MapTileFlags.CREEP_RECEDING);
            }

            Assertions.assertTrue(creep.removeProvider(game, ScrUnitIds.ZERG_CREEP_COLONY, CENTER, CENTER, true));

            int removed = 0;

            for (int frame = 0; frame < FRAME_LIMIT && recession.getFreeCount() < CreepRecession.RECORD_COUNT; frame++) {
                int countdown = recession.getCountdown();
                int expectedTile = -1;

                if (countdown == 0) {
                    int list = 0;

                    while (recession.getBucketCount(list) == 0) {
                        list++;
                    }

                    LcgRandom predictor = new LcgRandom(game.getRandom().getState());

                    predictor.setEnabled(1);
                    expectedTile = recession.getRecedingTile(list, predictor.next(RECESSION_RANDOM_SOURCE, 0, recession.getBucketCount(list) - 1));
                }

                int free = recession.getFreeCount();

                recession.update(game);

                if (countdown != 0) {
                    Assertions.assertEquals(countdown - 1, recession.getCountdown());

                    continue;
                }

                Assertions.assertEquals(PERIODS[free >>> PERIOD_SHIFT], recession.getCountdown());
                Assertions.assertEquals(free + 1, recession.getFreeCount());
                Assertions.assertFalse(hasCreep(map, expectedTile), "The randomly selected receding tile loses its creep.");
                Assertions.assertEquals(PLAIN_TILE, map.getTileIdentifier(expectedTile));
                Assertions.assertEquals(0, map.getOverlayTileIdentifier(expectedTile));
                Assertions.assertEquals(0, map.getTileFlags(expectedTile) & (MapTileFlags.CREEP_RECEDING | MapTileFlags.HAS_CREEP));
                assertListsMatchNeighbors(game);

                removed++;
            }

            Assertions.assertEquals(creepTiles, removed);
            Assertions.assertEquals(0, countCreep(map));

            for (int tileIndex = 0; tileIndex < TILE_COUNT; tileIndex++) {
                if (originalFlags[tileIndex] != -1) {
                    Assertions.assertEquals(originalFlags[tileIndex], map.getTileFlags(tileIndex), "Untouched terrain keeps its flags.");
                }
            }
        }
    }

    @Test
    void retry_provider_removal_should_rescan_every_fourth_call_after_records_run_out() throws Exception {
        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game probe = createGame(data, CreepRecession.RECORD_COUNT);
            CreepField probeCreep = probe.getCreepField();

            Assertions.assertNotNull(probeCreep);

            probeCreep.fill(probe, ScrUnitIds.ZERG_CREEP_COLONY, CENTER, CENTER);

            int creepTiles = countCreep(probe.getMap());

            Game game = createGame(data, creepTiles - 1);
            CreepField creep = game.getCreepField();
            CreepRecession recession = game.getCreepRecession();

            Assertions.assertNotNull(creep);

            creep.fill(game, ScrUnitIds.ZERG_CREEP_COLONY, CENTER, CENTER);
            creep.finishInitialization();

            Assertions.assertFalse(creep.removeProvider(game, ScrUnitIds.ZERG_CREEP_COLONY, CENTER, CENTER, true),
                                   "provider removal runs out of records");
            Assertions.assertNotNull(recession);
            Assertions.assertEquals(0, recession.getFreeCount());
            Assertions.assertFalse(creep.retryProviderRemoval(game, ScrUnitIds.ZERG_CREEP_COLONY, CENTER, CENTER, true),
                                   "the retry rescans without a record");

            Assertions.assertEquals(0, recession.getCountdown());

            recession.update(game);

            Assertions.assertEquals(1, recession.getFreeCount());

            for (int call = 0; call < COUNTDOWN_RETRY_CALLS; call++) {
                Assertions.assertFalse(creep.retryProviderRemoval(game, ScrUnitIds.ZERG_CREEP_COLONY, CENTER, CENTER, true),
                                       "the retry countdown runs");
                Assertions.assertEquals(1, recession.getFreeCount(), "no scan while the countdown runs");
            }

            Assertions.assertTrue(creep.retryProviderRemoval(game, ScrUnitIds.ZERG_CREEP_COLONY, CENTER, CENTER, true),
                                  "the fourth call rescans and completes");
            Assertions.assertEquals(0, recession.getFreeCount());
            Assertions.assertTrue(creep.retryProviderRemoval(game, ScrUnitIds.ZERG_CREEP_COLONY, CENTER, CENTER, true),
                                  "a completed scan cleared the countdown");
        }
    }

    @Test
    void remove_provider_should_keep_creep_sustained_by_another_grounded_zerg_building() throws Exception {
        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            Game game = createGame(data, CreepRecession.RECORD_COUNT);
            GameMap map = game.getMap();
            CreepField creep = game.getCreepField();

            Assertions.assertNotNull(creep);

            creep.fill(game, ScrUnitIds.ZERG_CREEP_COLONY, CENTER, CENTER);
            creep.fill(game, ScrUnitIds.ZERG_CREEP_COLONY, CENTER + SUSTAINING_OFFSET, CENTER);
            creep.finishInitialization();

            Unit sustaining = game.getUnitSystem().requireUnitPool().getUnit(1);

            sustaining.setUnitId(ScrUnitIds.ZERG_CREEP_COLONY);
            sustaining.setStatus(UnitStatusFlags.COMPLETED | UnitStatusFlags.GROUNDED_BUILDING);
            sustaining.setSprite(SpriteScenario.createSprite(0, CENTER + SUSTAINING_OFFSET, CENTER, 0));

            game.getUnitSystem().requireUnitSpatialIndex().insert(sustaining, data.getUnitTypes());

            Assertions.assertTrue(creep.removeProvider(game, ScrUnitIds.ZERG_CREEP_COLONY, CENTER, CENTER, true));

            int receding = 0;

            for (int vertical = 0; vertical < MAP_DIMENSION; vertical++) {
                for (int horizontal = 0; horizontal < MAP_DIMENSION; horizontal++) {
                    int tileIndex = vertical * MAP_DIMENSION + horizontal;

                    if (!hasCreep(map, tileIndex)) {
                        continue;
                    }

                    int removedX = horizontal * TILE_SIZE + HALF_TILE - CENTER;
                    int removedY = vertical * TILE_SIZE + HALF_TILE - CENTER;
                    int keptX = horizontal * TILE_SIZE + HALF_TILE - (CENTER + SUSTAINING_OFFSET);
                    boolean inRemovedArea = isInCreepArea(removedX, removedY) && isInCreepBounds(horizontal, vertical, CENTER, CENTER);
                    boolean sustained = isInCreepArea(keptX, removedY) && isInCreepBounds(horizontal, vertical, CENTER + SUSTAINING_OFFSET, CENTER);
                    boolean expected = inRemovedArea && !sustained;

                    Assertions.assertEquals(expected, (map.getTileFlags(tileIndex) & MapTileFlags.CREEP_RECEDING) != 0,
                                            "Tile " + horizontal + "," + vertical);

                    if (expected) {
                        receding++;
                    }
                }
            }

            Assertions.assertTrue(receding > 0);
            assertListsMatchNeighbors(game);
        }
    }
}
