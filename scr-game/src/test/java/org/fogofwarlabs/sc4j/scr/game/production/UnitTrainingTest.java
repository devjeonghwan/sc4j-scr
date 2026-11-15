package org.fogofwarlabs.sc4j.scr.game.production;

import org.fogofwarlabs.sc4j.base.exception.IgnoreException;
import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitGroupFlags;
import org.fogofwarlabs.sc4j.scr.game.ai.UnitStrengthTable;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.player.ProductionStatistics;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.IscriptExecutor;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitCreationContext;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;

/**
 * Checks unit training: supply waits, build time, completion, queue slots, rally points and Carrier training.
 */
@RequiresInstallation
final class UnitTrainingTest {
    /**
     * The tileset of the test maps.
     */
    private static final int TILESET = 0;

    private static final int CARRIER_X = 3600;
    private static final int CARRIER_Y = 2120;

    /**
     * The minerals that let a producer pay for its queued unit.
     */
    private static final int TRAINING_MINERALS = 10000;

    private static final int TRAINING_STATE_START = 0;
    private static final int TRAINING_STATE_RETRY = 1;
    private static final int TRAINING_STATE_BUILDING = 2;
    private static final int GENERATION_ALIAS_QUEUE_INDEX = 6;
    private static final int MAXIMUM_QUEUE_INDEX = 0xFF;
    private static final int RETAINED_GENERATION = 0x7D;
    private static final int RETAINED_ENERGY = 0x5A6B;

    private static final int SMALL_MAP_DIMENSION = 8;
    private static final int SMALL_MAP_TILE_COUNT = SMALL_MAP_DIMENSION * SMALL_MAP_DIMENSION;
    private static final int ORDER_POOL_CAPACITY = 16;

    /**
     * The instruction limit of the tests outside the running game.
     */
    private static final int INSTRUCTION_LIMIT = 512;

    private static final int PLAYER = 0;

    /**
     * The recorded issuer comparison value of the test player.
     */
    private static final int PLAYER_ID = 2;
    /**
     * The race of the test player, Zerg.
     */
    private static final int PLAYER_RACE = 0;
    /**
     * The team of the test player.
     */
    private static final int TEAM = 0;
    /**
     * The local player comparison value of the unit creation context, a slot that owns no test unit.
     */
    private static final int LOCAL_PLAYER = 8;

    /**
     * The position of the fixture sprites on both axes.
     */
    private static final int SPRITE_POSITION = 128;
    /**
     * The position of the completing worker's sprite on both axes.
     */
    private static final int COMPLETING_WORKER_POSITION = 100;
    /**
     * The pool indexes of the producer and of the trained worker.
     */
    private static final int PRODUCER_POOL_INDEX = 1;
    private static final int WORKER_POOL_INDEX = 2;

    private static final int HIT_POINT_INCREMENT = 10;
    private static final int COMPLETED_WORKER_HIT_POINTS = 1024;
    private static final int COMPLETED_WORKER_BUILD_TIME = 9;
    private static final int AIR_STRENGTH = 17;
    private static final int GROUND_STRENGTH = 29;
    private static final int RALLY_X = 180;
    private static final int RALLY_Y = 190;

    /**
     * The completed bit of the unit status.
     */
    private static final int COMPLETED_STATUS = 1;

    private static final int PROBE_TYPE = 64;
    private static final int CARRIER_TYPE = 72;
    private static final int INTERCEPTOR_TYPE = 73;

    /**
     * The empty unit type of a cleared queue slot, also held by the energy that the queue index past the last slot aliases.
     */
    private static final int EMPTY_UNIT_TYPE = 228;

    private static final int MOVE_ORDER = 6;
    private static final int NOTHING_ORDER = 23;
    private static final int TRAIN_ORDER = 38;

    /**
     * The queue indexes of the first, second and fifth queue slot, and the queue index past the last slot.
     */
    private static final int FIRST_QUEUE_INDEX = 0;
    private static final int SECOND_QUEUE_INDEX = 1;
    private static final int FIFTH_QUEUE_INDEX = 4;
    private static final int ENERGY_ALIAS_QUEUE_INDEX = 5;

    private static @Nullable GameData gameData;
    private static @Nullable ReplayRuntime runtime;

    @BeforeAll
    static void loadGameData() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
        runtime  = RunningGameScenario.start(gameData);
    }

    @AfterAll
    static void closeGameData() {
        GameData installedData = gameData;

        runtime = null;

        if (installedData != null) {
            IgnoreException.execute(
                    installedData::close
            );
        }

        gameData = null;
    }


    private static @NotNull Unit createProducer() {
        Unit producer = UnitTestUtils.createUnit(PRODUCER_POOL_INDEX);

        producer.setUnitId(ScrUnitIds.PROTOSS_NEXUS);
        producer.setOwnerIndex(PLAYER);
        producer.setStatus(UnitStatusFlags.COMPLETED | UnitStatusFlags.GROUNDED_BUILDING);
        producer.setSecondaryOrder(ScrOrderIds.TRAIN);
        producer.setSprite(SpriteScenario.createSprite(0, SPRITE_POSITION, SPRITE_POSITION, 0));

        return producer;
    }

    private static @NotNull Unit createPendingProducer(@NotNull Game game, int queueIndex) {
        Unit producer = RunningGameScenario.createUnit(game, ScrUnitIds.PROTOSS_CARRIER, CARRIER_X, CARRIER_Y, RunningGameScenario.HUMAN);
        Player player = game.getPlayerTable().getPlayer(RunningGameScenario.HUMAN);
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(player);
        Assertions.assertNotNull(pool);
        Assertions.assertTrue(pool.isAllocated(producer.getIndex()));
        Assertions.assertNotNull(producer.getSpriteOrNull());
        Assertions.assertNull(producer.getComputerRecord());

        player.setMinerals(TRAINING_MINERALS);
        producer.setBuildQueueIndex(queueIndex);

        Assertions.assertEquals(queueIndex, game.getUnitProduction().pushBuildQueue(producer, ScrUnitIds.PROTOSS_INTERCEPTOR));

        producer.setSecondaryOrder(ScrOrderIds.TRAIN);
        producer.setSecondaryOrderState(TRAINING_STATE_START);
        game.getUnitTraining().update(producer, RunningGameScenario.INSTRUCTION_LIMIT);

        Unit child = producer.getCurrentBuildUnit();

        Assertions.assertEquals(TRAINING_STATE_BUILDING, producer.getSecondaryOrderState());
        Assertions.assertNotNull(child);
        Assertions.assertTrue(pool.isAllocated(child.getIndex()));
        Assertions.assertNotNull(child.getSpriteOrNull());
        Assertions.assertEquals(RunningGameScenario.HUMAN, child.getOwnerIndex());
        Assertions.assertEquals(0, child.getStatus() & UnitStatusFlags.COMPLETED);

        child.setRemainingBuildTime(0);

        return producer;
    }


    @Test
    void update_should_wait_for_supply_without_allocating_or_consuming_random() {
        Game game = Game.create(gameData,
                                new GameMap(SMALL_MAP_DIMENSION, SMALL_MAP_DIMENSION, TILESET, new int[SMALL_MAP_TILE_COUNT]),
                                new LcgRandom(0));
        Unit producer = createProducer();
        UnitAccounting accounting = PlayerProgressTestUtils.createUnitAccounting();

        accounting.initializeForGame(null);
        game.getPlayerTable().setUnitAccounting(accounting);
        producer.setFirstBuildQueueType(ScrUnitIds.PROTOSS_PROBE);

        game.getUnitTraining().update(producer, INSTRUCTION_LIMIT);

        Assertions.assertEquals(TRAINING_STATE_RETRY, producer.getSecondaryOrderState());
        Assertions.assertNull(producer.getCurrentBuildUnit());
        Assertions.assertEquals(PROBE_TYPE, producer.getFirstBuildQueueType());
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void update_should_finish_empty_queue_and_reset_missing_construction_relation() {
        Game game = Game.create(gameData,
                                new GameMap(SMALL_MAP_DIMENSION, SMALL_MAP_DIMENSION, TILESET, new int[SMALL_MAP_TILE_COUNT]),
                                new LcgRandom(0));
        Unit producer = createProducer();

        producer.setFirstBuildQueueType(ScrUnitIds.NONE);

        game.getUnitTraining().update(producer, INSTRUCTION_LIMIT);

        Assertions.assertEquals(NOTHING_ORDER, producer.getSecondaryOrder());
        Assertions.assertEquals(TRAINING_STATE_START, producer.getSecondaryOrderState());

        producer.setSecondaryOrder(ScrOrderIds.TRAIN);
        producer.setSecondaryOrderState(TRAINING_STATE_BUILDING);

        game.getUnitTraining().update(producer, INSTRUCTION_LIMIT);

        Assertions.assertEquals(TRAINING_STATE_START, producer.getSecondaryOrderState());
        Assertions.assertEquals(TRAIN_ORDER, producer.getSecondaryOrder());
    }

    @Test
    void update_should_decrement_build_time_before_completion_and_cap_hit_points() {
        Game game = Game.create(gameData,
                                new GameMap(SMALL_MAP_DIMENSION, SMALL_MAP_DIMENSION, TILESET, new int[SMALL_MAP_TILE_COUNT]),
                                new LcgRandom(0));
        Unit producer = createProducer();
        Unit worker = UnitTestUtils.createUnit(WORKER_POOL_INDEX);

        worker.setUnitId(ScrUnitIds.PROTOSS_PROBE);
        worker.setSprite(SpriteScenario.createSprite(0, SPRITE_POSITION, SPRITE_POSITION, 0));

        Assertions.assertNotNull(gameData);

        worker.setHitPoints(gameData.getUnits().get("Hit Points", ScrUnitIds.PROTOSS_PROBE) - 1);
        worker.setConstructionHitPointIncrement(HIT_POINT_INCREMENT);
        worker.setRemainingBuildTime(1);
        worker.setAirStrength(AIR_STRENGTH);
        worker.setGroundStrength(GROUND_STRENGTH);

        producer.setCurrentBuildUnit(worker);
        producer.setSecondaryOrderState(TRAINING_STATE_BUILDING);

        game.getUnitTraining().update(producer, INSTRUCTION_LIMIT);

        Assertions.assertEquals(0, worker.getRemainingBuildTime());
        Assertions.assertEquals(gameData.getUnits().get("Hit Points", ScrUnitIds.PROTOSS_PROBE), worker.getHitPoints());
        Assertions.assertEquals(0, worker.getStatus() & UnitStatusFlags.COMPLETED);
        Assertions.assertEquals(AIR_STRENGTH, worker.getAirStrength());
        Assertions.assertEquals(GROUND_STRENGTH, worker.getGroundStrength());
        Assertions.assertSame(worker, producer.getCurrentBuildUnit());

        Assertions.assertThrows(UnsupportedOperationException.class, () -> game.getUnitTraining().update(producer, INSTRUCTION_LIMIT));

        Assertions.assertEquals(TRAINING_STATE_BUILDING, producer.getSecondaryOrderState());
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void update_should_refuse_completed_target_before_hit_point_side_effects() {
        Game game = Game.create(gameData,
                                new GameMap(SMALL_MAP_DIMENSION, SMALL_MAP_DIMENSION, TILESET, new int[SMALL_MAP_TILE_COUNT]),
                                new LcgRandom(0));
        Unit producer = createProducer();
        Unit worker = UnitTestUtils.createUnit(WORKER_POOL_INDEX);

        worker.setUnitId(ScrUnitIds.PROTOSS_PROBE);
        worker.setStatus(UnitStatusFlags.COMPLETED);
        worker.setHitPoints(COMPLETED_WORKER_HIT_POINTS);
        worker.setConstructionHitPointIncrement(HIT_POINT_INCREMENT);
        worker.setRemainingBuildTime(COMPLETED_WORKER_BUILD_TIME);
        worker.setAirStrength(AIR_STRENGTH);
        worker.setGroundStrength(GROUND_STRENGTH);

        producer.setCurrentBuildUnit(worker);
        producer.setSecondaryOrderState(TRAINING_STATE_BUILDING);

        Assertions.assertThrows(UnsupportedOperationException.class, () -> game.getUnitTraining().update(producer, INSTRUCTION_LIMIT));

        Assertions.assertEquals(COMPLETED_WORKER_HIT_POINTS, worker.getHitPoints());
        Assertions.assertEquals(COMPLETED_WORKER_BUILD_TIME, worker.getRemainingBuildTime());
        Assertions.assertEquals(AIR_STRENGTH, worker.getAirStrength());
        Assertions.assertEquals(GROUND_STRENGTH, worker.getGroundStrength());
        Assertions.assertSame(worker, producer.getCurrentBuildUnit());
    }

    @Test
    void update_should_complete_worker_and_consume_circular_queue_before_applying_point_rally() {
        Game game = Game.create(gameData,
                                new GameMap(SMALL_MAP_DIMENSION, SMALL_MAP_DIMENSION, TILESET, new int[SMALL_MAP_TILE_COUNT]),
                                new LcgRandom(0));

        game.initializeExecutionScope();
        game.getPlayerTable().setProductionStatistics(new ProductionStatistics(true,
                                                                               new int[ProductionStatistics.VALUE_COUNT],
                                                                               new float[ProductionStatistics.VALUE_COUNT]));
        game.initializeOrderPool(ORDER_POOL_CAPACITY);

        UnitAccounting accounting = PlayerProgressTestUtils.createUnitAccounting();

        accounting.initializeForGame(null);
        game.getPlayerTable().setUnitAccounting(accounting);

        Assertions.assertNotNull(gameData);

        game.setUnitStrengthTable(new UnitStrengthTable(gameData.getUnitTypes(), gameData.getWeaponTypes()));
        game.getUnitSystem().setUnitCreationContext(new UnitCreationContext(false, false, false, false, true, LOCAL_PLAYER));
        game.getPlayerTable().setPlayer(PLAYER, new Player(PLAYER, PLAYER_ID, PlayerTypes.HUMAN, PLAYER_RACE, TEAM));

        Unit producer = createProducer();

        producer.setBuildQueueIndex(Unit.FIFTH_QUEUE_SLOT);
        producer.setFifthBuildQueueType(ScrUnitIds.PROTOSS_PROBE);
        producer.setRallyPositionX(RALLY_X);
        producer.setRallyPositionY(RALLY_Y);

        Unit worker = UnitTestUtils.createUnit(WORKER_POOL_INDEX);

        worker.setUnitId(ScrUnitIds.PROTOSS_PROBE);
        worker.setOwnerIndex(PLAYER);
        worker.setPrimaryOrder(ScrOrderIds.NOTHING);
        worker.setHitPoints(gameData.getUnits().get("Hit Points", ScrUnitIds.PROTOSS_PROBE) - 1);
        worker.setConstructionHitPointIncrement(HIT_POINT_INCREMENT);

        Sprite sprite = SpriteScenario.createSprite(0, COMPLETING_WORKER_POSITION, COMPLETING_WORKER_POSITION, 0);
        SpriteImage image = SpriteScenario.createImage(0, 0, 0);

        image.setAnimationType(IscriptExecutor.ANIMATION_BUILT);
        sprite.setMainImage(image);
        worker.setSprite(sprite);
        producer.setCurrentBuildUnit(worker);
        producer.setSecondaryOrderState(TRAINING_STATE_BUILDING);

        game.getUnitTraining().update(producer, INSTRUCTION_LIMIT);

        Assertions.assertEquals(COMPLETED_STATUS, worker.getStatus() & UnitStatusFlags.COMPLETED);
        Assertions.assertEquals(gameData.getUnits().get("Hit Points", ScrUnitIds.PROTOSS_PROBE), worker.getHitPoints());
        Assertions.assertEquals(MOVE_ORDER, worker.getPrimaryOrder());
        Assertions.assertEquals(RALLY_X, worker.getOrderTargetX());
        Assertions.assertEquals(RALLY_Y, worker.getOrderTargetY());
        Assertions.assertEquals(EMPTY_UNIT_TYPE, producer.getBuildQueueUnitType(Unit.FIFTH_QUEUE_SLOT));
        Assertions.assertEquals(0, producer.getBuildQueueIndex());
        Assertions.assertEquals(TRAINING_STATE_START, producer.getSecondaryOrderState());
        Assertions.assertNull(producer.getCurrentBuildUnit());
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void update_should_let_carrier_train_its_queued_unit() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit carrier = RunningGameScenario.createUnit(game, ScrUnitIds.PROTOSS_CARRIER, CARRIER_X, CARRIER_Y, RunningGameScenario.COMPUTER);

            Player carrierPlayer = game.getPlayerTable().getPlayer(RunningGameScenario.COMPUTER);

            Assertions.assertNotNull(carrierPlayer);

            carrierPlayer.setMinerals(TRAINING_MINERALS);

            Assertions.assertTrue(game.getUnitProduction().pushBuildQueue(carrier, ScrUnitIds.PROTOSS_INTERCEPTOR) >= 0);

            carrier.setSecondaryOrder(ScrOrderIds.TRAIN);
            carrier.setSecondaryOrderState(TRAINING_STATE_START);

            game.getUnitTraining().update(carrier, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(TRAINING_STATE_BUILDING, carrier.getSecondaryOrderState(), "A Carrier creates the queued unit.");

            Unit child = carrier.getCurrentBuildUnit();

            Assertions.assertNotNull(child);
            Assertions.assertEquals(INTERCEPTOR_TYPE, child.getUnitId());
        });
    }

    @Test
    void update_should_advance_completed_training_with_an_empty_current_slot() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit producer = createPendingProducer(game, Unit.FIRST_QUEUE_SLOT);
            Unit child = producer.getCurrentBuildUnit();

            Assertions.assertNotNull(child);

            producer.setFirstBuildQueueType(ScrUnitIds.NONE);
            game.getUnitTraining().update(producer, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertNotEquals(0, child.getStatus() & UnitStatusFlags.COMPLETED);
            Assertions.assertEquals(EMPTY_UNIT_TYPE, producer.getFirstBuildQueueType());
            Assertions.assertEquals(SECOND_QUEUE_INDEX, producer.getBuildQueueIndex());
            Assertions.assertNull(producer.getCurrentBuildUnit());
            Assertions.assertEquals(TRAINING_STATE_START, producer.getSecondaryOrderState());
        });
    }

    @Test
    void update_should_clear_completed_training_slot_and_wrap_the_last_index() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit producer = createPendingProducer(game, Unit.FIFTH_QUEUE_SLOT);
            Unit child = producer.getCurrentBuildUnit();

            Assertions.assertNotNull(child);

            game.getUnitTraining().update(producer, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertNotEquals(0, child.getStatus() & UnitStatusFlags.COMPLETED);
            Assertions.assertEquals(EMPTY_UNIT_TYPE, producer.getFifthBuildQueueType());
            Assertions.assertEquals(FIRST_QUEUE_INDEX, producer.getBuildQueueIndex());
            Assertions.assertNull(producer.getCurrentBuildUnit());
            Assertions.assertEquals(TRAINING_STATE_START, producer.getSecondaryOrderState());
        });
    }

    @Test
    void update_should_reread_queue_index_after_clearing_generation_alias() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit producer = createPendingProducer(game, Unit.FIRST_QUEUE_SLOT);
            Unit child = producer.getCurrentBuildUnit();
            UnitPool pool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(child);
            Assertions.assertNotNull(pool);

            pool.setGeneration(producer.getIndex(), RETAINED_GENERATION);
            producer.setEnergy(RETAINED_ENERGY);
            producer.setBuildQueueIndex(GENERATION_ALIAS_QUEUE_INDEX);
            game.getUnitTraining().update(producer, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertNotEquals(0, child.getStatus() & UnitStatusFlags.COMPLETED);
            Assertions.assertEquals(FIFTH_QUEUE_INDEX, producer.getBuildQueueIndex());
            Assertions.assertEquals(0, pool.getGeneration(producer.getIndex()));
            Assertions.assertEquals(RETAINED_ENERGY, producer.getEnergy());
            Assertions.assertEquals(INTERCEPTOR_TYPE, producer.getFirstBuildQueueType());
            Assertions.assertNull(producer.getCurrentBuildUnit());
            Assertions.assertEquals(TRAINING_STATE_START, producer.getSecondaryOrderState());
        });
    }

    @Test
    void update_should_wrap_maximum_byte_queue_index_before_reducing_it() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit producer = createPendingProducer(game, Unit.FIRST_QUEUE_SLOT);
            Unit child = producer.getCurrentBuildUnit();
            UnitPool pool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(child);
            Assertions.assertNotNull(pool);

            pool.setGeneration(producer.getIndex(), RETAINED_GENERATION);
            producer.setEnergy(RETAINED_ENERGY);
            producer.setBuildQueueIndex(MAXIMUM_QUEUE_INDEX);
            game.getUnitTraining().update(producer, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertNotEquals(0, child.getStatus() & UnitStatusFlags.COMPLETED);
            Assertions.assertEquals(FIRST_QUEUE_INDEX, producer.getBuildQueueIndex());
            Assertions.assertEquals(RETAINED_GENERATION, pool.getGeneration(producer.getIndex()));
            Assertions.assertEquals(RETAINED_ENERGY, producer.getEnergy());
            Assertions.assertEquals(INTERCEPTOR_TYPE, producer.getFirstBuildQueueType());
            Assertions.assertNull(producer.getCurrentBuildUnit());
            Assertions.assertEquals(TRAINING_STATE_START, producer.getSecondaryOrderState());
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {TRAINING_STATE_START, TRAINING_STATE_RETRY})
    void update_should_read_an_empty_energy_alias(int trainingState) {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit carrier = RunningGameScenario.createUnit(game, ScrUnitIds.PROTOSS_CARRIER, CARRIER_X, CARRIER_Y, RunningGameScenario.COMPUTER);
            UnitPool pool = game.getUnitSystem().getUnitPool();
            Sprite sprite = carrier.getSpriteOrNull();

            Assertions.assertNotNull(pool);
            Assertions.assertTrue(pool.isAllocated(carrier.getIndex()));
            Assertions.assertEquals(CARRIER_TYPE, carrier.getUnitId());
            Assertions.assertEquals(RunningGameScenario.COMPUTER, carrier.getOwnerIndex());
            Assertions.assertTrue((carrier.getStatus() & UnitStatusFlags.COMPLETED) != 0);
            Assertions.assertEquals(0, carrier.getStatus() & UnitStatusFlags.DISABLED);
            Assertions.assertEquals(0, carrier.getLockdownTimer());
            Assertions.assertEquals(0, carrier.getStasisTimer());
            Assertions.assertEquals(0, carrier.getMaelstromTimer());
            Assertions.assertEquals(0, game.requireGameData().getUnitTypes().getGroupFlags(carrier.getUnitId()) & UnitGroupFlags.ZERG);
            Assertions.assertNotNull(sprite);
            Assertions.assertNotNull(sprite.getMainImage());
            Assertions.assertNull(carrier.getCurrentBuildUnit());

            carrier.setFirstBuildQueueType(ScrUnitIds.PROTOSS_INTERCEPTOR);
            carrier.setBuildQueueIndex(Unit.BUILD_QUEUE_SLOTS);
            carrier.setEnergy(ScrUnitIds.NONE);
            carrier.setSecondaryOrder(ScrOrderIds.TRAIN);
            carrier.setSecondaryOrderState(trainingState);

            Assertions.assertEquals(ENERGY_ALIAS_QUEUE_INDEX, carrier.getBuildQueueIndex());
            Assertions.assertEquals(EMPTY_UNIT_TYPE, carrier.getEnergy());

            Assertions.assertDoesNotThrow(() -> game.getUnitTraining().update(carrier, RunningGameScenario.INSTRUCTION_LIMIT));

            Assertions.assertEquals(NOTHING_ORDER, carrier.getSecondaryOrder());
            Assertions.assertEquals(TRAINING_STATE_START, carrier.getSecondaryOrderState());
            Assertions.assertNull(carrier.getCurrentBuildUnit());
            Assertions.assertEquals(ENERGY_ALIAS_QUEUE_INDEX, carrier.getBuildQueueIndex());
            Assertions.assertEquals(EMPTY_UNIT_TYPE, carrier.getEnergy());
            Assertions.assertEquals(INTERCEPTOR_TYPE, carrier.getFirstBuildQueueType());
        });
    }
}
