package org.fogofwarlabs.sc4j.scr.game.unit;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.game.frame.GameExecutionScope;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccountingAssert;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitSpatialIndex;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

/**
 * Checks unit storage creation with installed data: retained slot reset, owner links, random draws, mineral field placement
 * and the failure of a Siege Tank without a slot for its turret.
 */
@RequiresInstallation
final class UnitCreationTest {
    private static final int MAP_DIMENSION = 64;
    private static final int MAP_TILE_COUNT = MAP_DIMENSION * MAP_DIMENSION;
    private static final int IMAGE_CAPACITY = 2;
    private static final int MINERAL_IMAGE_CAPACITY = 4;
    private static final int INSTRUCTION_LIMIT = 512;
    private static final int CREATION_X = 321;
    private static final int CREATION_Y = 654;
    private static final int OWNER = 3;
    private static final int CREATION_TAG = 0x12345678;
    /**
     * The creation tag WORD that the slot keeps from {@link #CREATION_TAG}.
     */
    private static final int STORED_CREATION_TAG = 0x5678;
    private static final int LARVA_SIDE_COUNT = 4;
    private static final int OWNER_COUNTER_OFFSET = 0x3234;
    private static final int CONSTRUCTION_START_DIVISOR = 10;
    private static final int PRIMARY_ORDER_OFFSET = 0x69;
    private static final int SECONDARY_ORDER_OFFSET = 0xEA;
    private static final int DETECTION_FLAGS_OFFSET = 0x148;
    private static final int NOTHING_ORDER = 23;
    private static final int DIE_ORDER = 0;
    private static final int SPRITE_HIDDEN_FLAG = 0x20;
    private static final int CREATION_RANDOM_SOURCE = 14;
    private static final int STORAGE_RANDOM_SOURCE = 15;
    private static final int STORAGE_RANDOM_DRAWS = 2;
    /**
     * The owner that an invalid owner of {@code -1} becomes.
     */
    private static final int NORMALIZED_OWNER = 11;
    private static final int MARINE_CREATION_TAG = 123;

    private static final int MINERAL_X = 320;
    private static final int MINERAL_Y = 640;
    private static final int EDGES_PER_UNIT = 2;
    private static final int STATUS_OFFSET = 0x140;
    private static final int MINERAL_STATUS_FLAGS = 0x40002;
    /**
     * The two tiles under a mineral field at {@link #MINERAL_X} and {@link #MINERAL_Y}.
     */
    private static final int FIRST_FOOTPRINT_TILE = 20 * MAP_DIMENSION + 9;
    private static final int SECOND_FOOTPRINT_TILE = 20 * MAP_DIMENSION + 10;
    private static final int OCCUPIED_TILE_FLAG = 0x08000000;

    private static final int TURRET_FAILURE_START_FRAME = 50;
    private static final int TURRET_FAILURE_X = 3600;
    private static final int TURRET_FAILURE_Y = 2400;
    /**
     * The creation tag of the human player's units: the player in both BYTEs.
     */
    private static final int HUMAN_CREATION_TAG = RunningGameScenario.HUMAN | RunningGameScenario.HUMAN << Byte.SIZE;

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
    void create_storage_should_reset_retained_slot_and_fail_without_free_slot() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]), new LcgRandom(0));

        game.getUnitSystem().initializeUnitPool(1);
        game.getSpriteSystem().initializeSpritePool(1);
        game.getSpriteSystem().initializeImagePool(IMAGE_CAPACITY);

        UnitAccounting accounting = PlayerProgressTestUtils.createUnitAccounting();
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        Unit retained = pool.getUnit(1);

        retained.setResources(0xFEDC);
        retained.setSecondLarvaSideCount(-1);
        retained.setThirdLarvaSideCount(123);
        retained.setFourthLarvaSideCount(456);
        retained.setParasiteFlags(0xFF);
        retained.setBlindnessState(1);
        retained.setMaelstromTimer(250);
        retained.setAcidSporeCount(9);

        for (int index = 0; index < Unit.ACID_SPORE_SLOTS; index++) {
            retained.setAcidSporeTimer(index, 123);
        }

        UnitStorageConstructor storageConstructor = game.getUnitStorageConstructor();
        int creationUnitType = ScrUnitIds.TERRAN_MARINE;
        UnitCreationContext creationInputs = new UnitCreationContext(false, false, false, false, false, 0);

        Unit unit = storageConstructor.createStorage(creationInputs, accounting, creationUnitType, CREATION_X, CREATION_Y, OWNER, CREATION_TAG,
                                                     INSTRUCTION_LIMIT);

        Assertions.assertNotNull(unit);
        Assertions.assertSame(retained, unit);
        Assertions.assertEquals(0, unit.getResources());

        for (int side = 0; side < LARVA_SIDE_COUNT; side++) {
            Assertions.assertEquals(0, unit.getLarvaSideCount(side));
        }

        Assertions.assertEquals(0, unit.getParasiteFlags());
        Assertions.assertEquals(0, unit.getBlindnessState());
        Assertions.assertEquals(0, unit.getMaelstromTimer());
        Assertions.assertEquals(0, unit.getAcidSporeCount());

        for (int index = 0; index < Unit.ACID_SPORE_SLOTS; index++) {
            Assertions.assertEquals(0, unit.getAcidSporeTimer(index));
        }

        Assertions.assertSame(pool.getUnit(1), unit);
        Assertions.assertEquals(1, pool.getAllocatedCount());
        Assertions.assertEquals(0, pool.getFirstFreeIndex());
        Assertions.assertEquals(1, pool.getGeneration(1));
        Assertions.assertEquals(STORED_CREATION_TAG, pool.getCreationTag(1));
        Assertions.assertEquals(1, pool.getFirstOwnerIndex(OWNER));
        Assertions.assertEquals(1, UnitAccountingAssert.readCounter(accounting, OWNER_COUNTER_OFFSET + OWNER * Integer.BYTES));
        Assertions.assertEquals(0, unit.getUnitId());
        Assertions.assertEquals(OWNER, unit.getOwnerIndex());
        Assertions.assertEquals(CREATION_X, unit.getPositionX());
        Assertions.assertEquals(CREATION_Y, unit.getPositionY());
        Assertions.assertNotNull(gameData);
        Assertions.assertEquals(Integer.divideUnsigned(gameData.getUnits().get("Hit Points", 0), CONSTRUCTION_START_DIVISOR), unit.getHitPoints());
        Assertions.assertEquals(NOTHING_ORDER, UnitMemoryScenario.of(unit).readMemoryUnsignedByte(PRIMARY_ORDER_OFFSET));
        Assertions.assertEquals(NOTHING_ORDER, UnitMemoryScenario.of(unit).readMemoryUnsignedByte(SECONDARY_ORDER_OFFSET));
        Assertions.assertEquals(-1, UnitMemoryScenario.of(unit).readMemoryInteger(DETECTION_FLAGS_OFFSET));

        Sprite sprite = unit.getSpriteOrNull();

        Assertions.assertNotNull(sprite);
        Assertions.assertEquals(SPRITE_HIDDEN_FLAG, sprite.getFlags() & SPRITE_HIDDEN_FLAG);
        Assertions.assertEquals(0, sprite.getVisibilityFlags());
        Assertions.assertEquals(1, game.getRandom().getCallCount(STORAGE_RANDOM_SOURCE));
        Assertions.assertEquals(0, game.getRandom().getCallCount(CREATION_RANDOM_SOURCE));
        Assertions.assertEquals(STORAGE_RANDOM_DRAWS, game.getRandom().getTotalRandomCounts());
        Assertions.assertFalse(game.hasInitializedWorldState());

        int randomState = game.getRandom().getState();

        UnitStorageConstructor nextStorageConstructor = game.getUnitStorageConstructor();
        int nextCreationUnitType = ScrUnitIds.TERRAN_MARINE;
        UnitCreationContext nextCreationInputs = new UnitCreationContext(false, false, false, false, false, 0);

        Assertions.assertNull(nextStorageConstructor.createStorage(nextCreationInputs, accounting, nextCreationUnitType, CREATION_X, CREATION_Y,
                                                                   OWNER, 0, INSTRUCTION_LIMIT));
        Assertions.assertEquals(randomState, game.getRandom().getState());
        Assertions.assertEquals(STORAGE_RANDOM_DRAWS, game.getRandom().getTotalRandomCounts());
        Assertions.assertEquals(1, pool.getGeneration(1));
    }

    @Test
    void create_should_link_marine_and_consume_random_source_fourteen_even_on_failure() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]), new LcgRandom(0));

        game.getUnitSystem().initializeUnitPool(1);
        game.getSpriteSystem().initializeSpritePool(1);
        game.getSpriteSystem().initializeImagePool(IMAGE_CAPACITY);

        UnitAccounting accounting = PlayerProgressTestUtils.createUnitAccounting();

        UnitStorageConstructor storageConstructor = game.getUnitStorageConstructor();
        int creationUnitType = ScrUnitIds.TERRAN_MARINE;
        UnitCreationContext creationInputs = new UnitCreationContext(false, false, false, false, false, 0);

        Unit unit = storageConstructor.create(creationInputs, accounting, creationUnitType, CREATION_X, CREATION_Y, -1, MARINE_CREATION_TAG,
                                              INSTRUCTION_LIMIT);

        Assertions.assertNotNull(unit);
        Assertions.assertEquals(NORMALIZED_OWNER, unit.getOwnerIndex());

        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);
        Assertions.assertEquals(1, pool.getFirstOwnerIndex(NORMALIZED_OWNER));
        Assertions.assertEquals(1, pool.getFirstCreatedIndex(false));
        Assertions.assertEquals(0, pool.getFirstCreatedIndex(true));
        Assertions.assertNull(unit.getSubunit());
        Assertions.assertEquals(1, game.getRandom().getCallCount(CREATION_RANDOM_SOURCE));
        Assertions.assertEquals(1, game.getRandom().getCallCount(STORAGE_RANDOM_SOURCE));
        Assertions.assertFalse(game.hasInitializedWorldState());

        UnitStorageConstructor nextStorageConstructor = game.getUnitStorageConstructor();
        int nextCreationUnitType = ScrUnitIds.TERRAN_MARINE;
        UnitCreationContext nextCreationInputs = new UnitCreationContext(false, false, false, false, false, 0);

        Assertions.assertNull(nextStorageConstructor.create(nextCreationInputs, accounting, nextCreationUnitType, CREATION_X, CREATION_Y, -1,
                                                            MARINE_CREATION_TAG, INSTRUCTION_LIMIT));
        Assertions.assertEquals(2, game.getRandom().getCallCount(CREATION_RANDOM_SOURCE));
        Assertions.assertEquals(1, game.getRandom().getCallCount(STORAGE_RANDOM_SOURCE));
        Assertions.assertEquals(1, pool.getAllocatedCount());
    }

    @Test
    void create_should_insert_mineral_fields_into_spatial_index_and_mark_their_tiles() {
        for (int unitType = ScrUnitIds.RESOURCE_MINERAL_FIELD; unitType <= ScrUnitIds.RESOURCE_MINERAL_FIELD_TYPE_3; unitType++) {
            GameMap map = new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT], new int[MAP_TILE_COUNT]);
            Game game = Game.create(gameData, map, new LcgRandom(0));

            game.getUnitSystem().initializeUnitPool(1);
            game.getSpriteSystem().initializeSpritePool(1);
            game.getSpriteSystem().initializeImagePool(MINERAL_IMAGE_CAPACITY);
            game.initializeUnitSpatialIndex();
            game.initializeExecutionScope();

            UnitAccounting accounting = PlayerProgressTestUtils.createUnitAccounting();

            game.getPlayerTable().setUnitAccounting(accounting);

            UnitStorageConstructor storageConstructor = game.getUnitStorageConstructor();
            UnitCreationContext creationInputs = new UnitCreationContext(false, false, false, false, false, 0);

            Unit unit = storageConstructor.create(creationInputs, accounting, unitType, MINERAL_X, MINERAL_Y, PlayerTable.NEUTRAL_PLAYER_ID, unitType,
                                                  INSTRUCTION_LIMIT);

            Assertions.assertNotNull(unit);
            Assertions.assertEquals(unitType, unit.getUnitId());

            UnitPool pool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(pool);
            Assertions.assertEquals(1, pool.getAllocatedCount());
            Assertions.assertEquals(1, pool.getGeneration(1));
            Assertions.assertEquals(1, pool.getFirstCreatedIndex(true));

            UnitSpatialIndex spatialIndex = game.getUnitSystem().getUnitSpatialIndex();

            Assertions.assertNotNull(spatialIndex);
            Assertions.assertEquals(EDGES_PER_UNIT, spatialIndex.getCount());
            Assertions.assertEquals(MINERAL_STATUS_FLAGS, UnitMemoryScenario.of(unit).readMemoryInteger(STATUS_OFFSET) & MINERAL_STATUS_FLAGS);

            Sprite sprite = unit.getSpriteOrNull();

            Assertions.assertNotNull(sprite);
            Assertions.assertEquals(0, sprite.getVisibilityFlags());
            Assertions.assertEquals(-1, UnitMemoryScenario.of(unit).readMemoryInteger(DETECTION_FLAGS_OFFSET));
            Assertions.assertEquals(1, game.getRandom().getCallCount(CREATION_RANDOM_SOURCE));
            Assertions.assertEquals(1, game.getRandom().getCallCount(STORAGE_RANDOM_SOURCE));
            Assertions.assertFalse(game.hasInitializedWorldState());

            for (int tileIndex = 0; tileIndex < MAP_TILE_COUNT; tileIndex++) {
                Assertions.assertEquals(tileIndex == FIRST_FOOTPRINT_TILE || tileIndex == SECOND_FOOTPRINT_TILE ? OCCUPIED_TILE_FLAG : 0,
                                        map.getTileFlags(tileIndex));
            }
        }
    }

    @Test
    void siege_tank_without_a_slot_for_its_turret_is_not_created_or_counted() throws IOException {
        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(GameTestResources.REPLAY_TERRAN_VS_COMPUTER)));

        Assertions.assertNotNull(gameData);

        ReplayRuntime runtime = RunningGameScenario.createRuntime(replay, gameData);

        runtime.start(0, 0);

        Game game = runtime.getGame();

        while (game.getCurrentFrame() < TURRET_FAILURE_START_FRAME) {
            Assertions.assertTrue(runtime.step(0, game.getCurrentFrame() * runtime.getFrameDuration()));
        }

        GameExecutionScope scope = game.getExecutionScope();

        Assertions.assertNotNull(scope);

        scope.beginCommandDispatch();

        try {
            UnitPool pool = game.getUnitSystem().getUnitPool();
            UnitCreationContext creation = game.getUnitSystem().getUnitCreationContext();
            UnitStorageConstructor constructor = game.getUnitStorageConstructor();
            UnitAccounting accounting = game.getPlayerTable().getUnitAccounting();

            Assertions.assertNotNull(pool);
            Assertions.assertNotNull(creation);
            Assertions.assertNotNull(accounting);

            while (pool.getAllocatedCount() < pool.getCapacity() - 1) {
                Assertions.assertNotNull(constructor.create(creation,
                                                            accounting,
                                                            ScrUnitIds.TERRAN_MARINE,
                                                            TURRET_FAILURE_X, TURRET_FAILURE_Y,
                                                            RunningGameScenario.HUMAN,
                                                            HUMAN_CREATION_TAG,
                                                            RunningGameScenario.INSTRUCTION_LIMIT));
            }

            int tanks = accounting.getTypeCount(ScrUnitIds.TERRAN_SIEGE_TANK_TANK_MODE, RunningGameScenario.HUMAN);
            Unit tank = constructor.create(creation,
                                           accounting,
                                           ScrUnitIds.TERRAN_SIEGE_TANK_TANK_MODE,
                                           TURRET_FAILURE_X, TURRET_FAILURE_Y,
                                           RunningGameScenario.HUMAN,
                                           HUMAN_CREATION_TAG,
                                           RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertNull(tank, "The creation fails without a turret slot.");
            Assertions.assertEquals(pool.getCapacity(), pool.getAllocatedCount(), "The tank itself took the last slot and waits on the dead list.");
            Assertions.assertEquals(tanks, accounting.getTypeCount(ScrUnitIds.TERRAN_SIEGE_TANK_TANK_MODE, RunningGameScenario.HUMAN),
                                    "The destroyed tank is removed from the counts.");

            for (int index = pool.getFirstCreatedIndex(false); index != 0; index = pool.getNextCreatedIndex(index)) {
                Unit unit = pool.getUnit(index);

                Assertions.assertFalse(unit.getUnitId() == ScrUnitIds.TERRAN_SIEGE_TANK_TANK_MODE &&
                                       unit.getPrimaryOrder() != DIE_ORDER,
                                       "The destroyed tank is not left on the hidden list.");
            }
        } finally {
            scope.endCommandDispatch();
        }
    }
}
