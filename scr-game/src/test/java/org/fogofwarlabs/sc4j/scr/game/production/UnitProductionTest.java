package org.fogofwarlabs.sc4j.scr.game.production;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrTechIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUpgradeIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.replay.SelectionRequest;
import org.fogofwarlabs.sc4j.scr.game.frame.GameFrame;
import org.fogofwarlabs.sc4j.scr.game.frame.GameFrameScenario;
import org.fogofwarlabs.sc4j.scr.game.order.OrderPool;
import org.fogofwarlabs.sc4j.scr.game.order.UnitSelection;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.IscriptExecutor;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameCheats;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitHandle;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks the build prerequisites of producers, and the build, train and morph requests of a selected producer and their resource, queue and order
 * effects.
 */
@RequiresInstallation
final class UnitProductionTest {
    /**
     * The tileset of the test maps.
     */
    private static final int TILESET = 0;
    /**
     * The command player of the test, the owner of the test units.
     */
    private static final int PLAYER = 0;
    /**
     * The player identifier of the test player.
     */
    private static final int PLAYER_ID = 0;
    /**
     * The team of the test player.
     */
    private static final int TEAM = 0;
    /**
     * The team size of the selection outside a team game.
     */
    private static final int NO_TEAM_SIZE = 0;

    private static final int MAP_DIMENSION = 8;
    private static final int MAP_TILE_COUNT = MAP_DIMENSION * MAP_DIMENSION;
    private static final int UNIT_CAPACITY = 2;
    private static final int ORDER_CAPACITY = 8;
    private static final int INSTRUCTION_LIMIT = 512;
    private static final int PRODUCER_POSITION = 128;
    private static final int MOVE_TARGET_POSITION = 160;

    /**
     * The unit pool indexes of the producer and of the second Larva in the pool, and of the units created outside the pool: a prerequisite building
     * or a current build unit, and the Overlord of the morph test.
     */
    private static final int PRODUCER_POOL_INDEX = 1;
    private static final int SECOND_LARVA_POOL_INDEX = 2;
    private static final int DETACHED_UNIT_POOL_INDEX = 2;
    private static final int OVERLORD_POOL_INDEX = 3;

    private static final int ZERG_RACE = 0;
    private static final int TERRAN_RACE = 1;
    private static final int PROTOSS_RACE = 2;
    private static final int LOCAL_PLAYER_INDEX = 8;
    private static final int HANDLE_WIDTH_BYTES = 2;

    private static final int INITIAL_MINERALS = 500;
    private static final int WORKER_MINERAL_COST = 50;
    private static final int MINERALS_AFTER_TRAINING = 450;
    private static final int MINERALS_WITH_FULL_QUEUE = 250;
    /**
     * The gas of the player in the Lurker morph test, enough for one Lurker.
     */
    private static final int LURKER_TEST_GAS = 200;

    private static final int EMPTY_QUEUE_TYPE = 228;
    private static final int LAST_QUEUE_INDEX = 4;
    private static final int FREE_QUEUE_SLOTS = 3;
    private static final int STALE_SECONDARY_STATE = 9;
    private static final int RETAINED_SECONDARY_STATE = 7;
    private static final int TRAIN_ORDER = 38;
    private static final int ZERG_UNIT_MORPH_ORDER = 42;
    private static final int LARVA_TYPE = 35;
    private static final int DRONE_TYPE = 41;
    private static final int LURKER_TYPE = 103;

    private static final int FACTORY_POSITION = 200;
    private static final int MACHINE_SHOP_X = 260;
    private static final int MACHINE_SHOP_Y = 216;
    private static final int COMMAND_CENTER_POSITION = 500;
    /**
     * The pool index of the completed prerequisite that the accounting of the construction game counts.
     */
    private static final int PREREQUISITE_POOL_INDEX = 7;

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

    private static @NotNull Game createState(int workerType) {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(0));

        game.getUnitSystem().initializeUnitPool(UNIT_CAPACITY);

        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        Unit producer = pool.getUnit(PRODUCER_POOL_INDEX);

        producer.setUnitId(workerType == ScrUnitIds.TERRAN_SCV
                           ? ScrUnitIds.TERRAN_COMMAND_CENTER
                           : workerType == ScrUnitIds.ZERG_DRONE ? ScrUnitIds.ZERG_LARVA : ScrUnitIds.PROTOSS_NEXUS);
        producer.setOwnerIndex(PLAYER);
        producer.setStatus(UnitStatusFlags.COMPLETED | UnitStatusFlags.GROUNDED_BUILDING);
        producer.setPrimaryOrder(ScrOrderIds.NOTHING);
        producer.setSprite(SpriteScenario.createSprite(0, PRODUCER_POSITION, PRODUCER_POSITION, 0));
        producer.setFirstBuildQueueType(ScrUnitIds.NONE);
        producer.setSecondBuildQueueType(ScrUnitIds.NONE);
        producer.setThirdBuildQueueType(ScrUnitIds.NONE);
        producer.setFourthBuildQueueType(ScrUnitIds.NONE);
        producer.setFifthBuildQueueType(ScrUnitIds.NONE);

        UnitAccounting accounting = PlayerProgressTestUtils.createUnitAccounting();

        accounting.initializeForGame(null);

        Assertions.assertNotNull(gameData);

        accounting.updateCompleted(producer, gameData.getUnitTypes(), 1, false, null);
        game.getPlayerTable().setUnitAccounting(accounting);

        int race = workerType == ScrUnitIds.TERRAN_SCV ? TERRAN_RACE : workerType == ScrUnitIds.ZERG_DRONE ? ZERG_RACE : PROTOSS_RACE;
        Player player = new Player(PLAYER, PLAYER_ID, PlayerTypes.HUMAN, race, TEAM);

        player.setMinerals(INITIAL_MINERALS);
        game.getPlayerTable().setPlayer(PLAYER, player);

        UnitSelection selection = new UnitSelection(pool, false, NO_TEAM_SIZE, LOCAL_PLAYER_INDEX);

        game.setSelection(selection);

        int handle = UnitHandle.encode(PRODUCER_POOL_INDEX, pool.getGeneration(PRODUCER_POOL_INDEX), UNIT_CAPACITY);

        selection.execute(game, new SelectionRequest(SelectionRequest.Operation.REPLACE, HANDLE_WIDTH_BYTES, new long[]{handle}), PLAYER);

        return game;
    }

    private static @NotNull Unit createProducer(int unitType, int status) {
        Unit producer = UnitTestUtils.createUnit(PRODUCER_POOL_INDEX);

        producer.setUnitId(unitType);
        producer.setOwnerIndex(PLAYER);
        producer.setStatus(status);
        producer.setResearchType(ScrTechIds.NONE);
        producer.setUpgradeType(ScrUpgradeIds.NONE);

        producer.setFirstBuildQueueType(ScrUnitIds.NONE);
        producer.setSecondBuildQueueType(ScrUnitIds.NONE);
        producer.setThirdBuildQueueType(ScrUnitIds.NONE);
        producer.setFourthBuildQueueType(ScrUnitIds.NONE);
        producer.setFifthBuildQueueType(ScrUnitIds.NONE);

        return producer;
    }

    private static @NotNull Game createHydraliskState(int cheatFlags) throws IOException {
        Assertions.assertNotNull(gameData);

        Game game = createState(ScrUnitIds.ZERG_DRONE);
        GameFrame frame = GameFrameScenario.createFrame(gameData);

        frame.getVisibility().setCheatFlags(cheatFlags);
        game.setFrame(frame);
        game.initializeOrderPool(ORDER_CAPACITY);
        game.getPlayerTable().setPlayerTechnology(PlayerProgressTestUtils.createPlayerTechnology(true));
        game.getPlayerTable().setPlayerUpgrades(PlayerProgressTestUtils.createPlayerUpgrades(true));

        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        Unit hydralisk = pool.getUnit(PRODUCER_POOL_INDEX);
        Sprite sprite = hydralisk.getSpriteOrNull();
        SpriteImage image = SpriteScenario.createImage(0, 0, 0);

        Assertions.assertNotNull(sprite);

        hydralisk.setUnitId(ScrUnitIds.ZERG_HYDRALISK);
        hydralisk.setStatus(UnitStatusFlags.COMPLETED);
        image.setAnimationType(IscriptExecutor.ANIMATION_BUILT);
        sprite.setMainImage(image);

        Unit overlord = UnitTestUtils.createUnit(OVERLORD_POOL_INDEX);

        overlord.setUnitId(ScrUnitIds.ZERG_OVERLORD);
        overlord.setOwnerIndex(PLAYER);
        overlord.setStatus(UnitStatusFlags.COMPLETED);

        UnitAccounting accounting = game.getPlayerTable().getUnitAccounting();
        Player player = game.getPlayerTable().getPlayer(PLAYER);

        Assertions.assertNotNull(accounting);
        Assertions.assertNotNull(player);

        accounting.updateCompleted(overlord, gameData.getUnitTypes(), 1, false, null);
        player.setGas(LURKER_TEST_GAS);

        return game;
    }


    @Test
    void can_build_should_check_protoss_building_prerequisites_without_mutating_resources() {
        Game game = createState(ScrUnitIds.PROTOSS_PROBE);
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        Unit probe = pool.getUnit(PRODUCER_POOL_INDEX);

        probe.setUnitId(ScrUnitIds.PROTOSS_PROBE);
        probe.setStatus(UnitStatusFlags.COMPLETED);

        for (int unitType : new int[]{ScrUnitIds.PROTOSS_NEXUS,
                                      ScrUnitIds.PROTOSS_PYLON,
                                      ScrUnitIds.PROTOSS_ASSIMILATOR,
                                      ScrUnitIds.PROTOSS_GATEWAY,
                                      ScrUnitIds.PROTOSS_FORGE}) {
            Assertions.assertTrue(game.getUnitProduction().canBuild(probe, unitType));
        }

        for (int unitType : new int[]{ScrUnitIds.PROTOSS_ROBOTICS_FACILITY,
                                      ScrUnitIds.PROTOSS_OBSERVATORY,
                                      ScrUnitIds.PROTOSS_PHOTON_CANNON,
                                      ScrUnitIds.PROTOSS_CITADEL_OF_ADUN,
                                      ScrUnitIds.PROTOSS_CYBERNETICS_CORE,
                                      ScrUnitIds.PROTOSS_TEMPLAR_ARCHIVES,
                                      ScrUnitIds.PROTOSS_STARGATE,
                                      ScrUnitIds.PROTOSS_FLEET_BEACON,
                                      ScrUnitIds.PROTOSS_ARBITER_TRIBUNAL,
                                      ScrUnitIds.PROTOSS_ROBOTICS_SUPPORT_BAY,
                                      ScrUnitIds.PROTOSS_SHIELD_BATTERY}) {
            Assertions.assertFalse(game.getUnitProduction().canBuild(probe, unitType));
        }

        Unit prerequisite = UnitTestUtils.createUnit(DETACHED_UNIT_POOL_INDEX);

        prerequisite.setOwnerIndex(PLAYER);
        prerequisite.setUnitId(ScrUnitIds.PROTOSS_STARGATE);

        Assertions.assertNotNull(gameData);

        UnitAccounting accounting = game.getPlayerTable().getUnitAccounting();

        Assertions.assertNotNull(accounting);

        accounting.updateCompleted(prerequisite, gameData.getUnitTypes(), 1, false, null);

        Assertions.assertTrue(game.getUnitProduction().canBuild(probe, ScrUnitIds.PROTOSS_FLEET_BEACON));
        Assertions.assertFalse(game.getUnitProduction().canBuild(probe, ScrUnitIds.PROTOSS_ARBITER_TRIBUNAL));

        prerequisite.setUnitId(ScrUnitIds.PROTOSS_TEMPLAR_ARCHIVES);

        accounting.updateCompleted(prerequisite, gameData.getUnitTypes(), 1, false, null);

        Assertions.assertTrue(game.getUnitProduction().canBuild(probe, ScrUnitIds.PROTOSS_ARBITER_TRIBUNAL));

        probe.setStasisTimer(1);

        Assertions.assertFalse(game.getUnitProduction().canBuild(probe, ScrUnitIds.PROTOSS_PYLON));

        probe.setStasisTimer(0);
        probe.setUnitId(ScrUnitIds.TERRAN_SCV);

        Assertions.assertFalse(game.getUnitProduction().canBuild(probe, ScrUnitIds.PROTOSS_PYLON));

        Player player = game.getPlayerTable().getPlayer(PLAYER);

        Assertions.assertNotNull(player);

        Assertions.assertEquals(INITIAL_MINERALS, player.getMinerals());
        Assertions.assertEquals(EMPTY_QUEUE_TYPE, probe.getFirstBuildQueueType());
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void train_should_enqueue_workers_with_circular_queue_resource_and_secondary_order_effects() {
        for (int workerType : new int[]{ScrUnitIds.TERRAN_SCV, ScrUnitIds.PROTOSS_PROBE}) {
            Game game = createState(workerType);
            UnitPool pool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(pool);

            Unit producer = pool.getUnit(PRODUCER_POOL_INDEX);

            producer.setBuildQueueIndex(LAST_QUEUE_INDEX);
            producer.setSecondaryOrderState(STALE_SECONDARY_STATE);
            producer.setCurrentBuildUnit(UnitTestUtils.createUnit(DETACHED_UNIT_POOL_INDEX));

            Assertions.assertTrue(game.getUnitProduction().train(PLAYER, workerType));

            Assertions.assertEquals(workerType, producer.getFifthBuildQueueType());

            Player player = game.getPlayerTable().getPlayer(PLAYER);

            Assertions.assertNotNull(player);

            Assertions.assertEquals(MINERALS_AFTER_TRAINING, player.getMinerals());
            Assertions.assertEquals(TRAIN_ORDER, producer.getSecondaryOrder());
            Assertions.assertEquals(0, producer.getSecondaryOrderState());
            Assertions.assertFalse(producer.hasCurrentBuildUnit());

            producer.setSecondaryOrderState(RETAINED_SECONDARY_STATE);

            Unit target = UnitTestUtils.createUnit(DETACHED_UNIT_POOL_INDEX);

            producer.setCurrentBuildUnit(target);

            Assertions.assertTrue(game.getUnitProduction().train(PLAYER, workerType));

            Assertions.assertEquals(workerType, producer.getFirstBuildQueueType());
            Assertions.assertEquals(RETAINED_SECONDARY_STATE, producer.getSecondaryOrderState());
            Assertions.assertSame(target, producer.getCurrentBuildUnit());

            for (int remaining = 0; remaining < FREE_QUEUE_SLOTS; remaining++) {
                Assertions.assertTrue(game.getUnitProduction().train(PLAYER, workerType));
            }

            Assertions.assertFalse(game.getUnitProduction().train(PLAYER, workerType));

            Assertions.assertEquals(MINERALS_WITH_FULL_QUEUE, player.getMinerals());
            Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
        }
    }

    @Test
    void morph_should_morph_larvae_in_selection_order_until_resources_run_out() {
        Game game = createState(ScrUnitIds.ZERG_DRONE);

        game.initializeOrderPool(ORDER_CAPACITY);

        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        Unit first = pool.getUnit(PRODUCER_POOL_INDEX);
        Unit second = pool.getUnit(SECOND_LARVA_POOL_INDEX);

        second.setUnitId(ScrUnitIds.ZERG_LARVA);
        second.setOwnerIndex(PLAYER);
        second.setStatus(UnitStatusFlags.COMPLETED);
        second.setPrimaryOrder(ScrOrderIds.LARVA);
        second.setSprite(SpriteScenario.createSprite(0, PRODUCER_POSITION, PRODUCER_POSITION, 0));
        second.setFirstBuildQueueType(ScrUnitIds.NONE);
        second.setSecondBuildQueueType(ScrUnitIds.NONE);
        second.setThirdBuildQueueType(ScrUnitIds.NONE);
        second.setFourthBuildQueueType(ScrUnitIds.NONE);
        second.setFifthBuildQueueType(ScrUnitIds.NONE);

        for (Unit larva : new Unit[]{first, second}) {
            SpriteImage image = SpriteScenario.createImage(0, 0, 0);

            image.setAnimationType(IscriptExecutor.ANIMATION_BUILT);

            Sprite mainImageSprite = larva.getSpriteOrNull();

            Assertions.assertNotNull(mainImageSprite);

            mainImageSprite.setMainImage(image);
        }

        Unit overlord = UnitTestUtils.createUnit(OVERLORD_POOL_INDEX);

        overlord.setUnitId(ScrUnitIds.ZERG_OVERLORD);
        overlord.setOwnerIndex(PLAYER);
        overlord.setStatus(UnitStatusFlags.COMPLETED);

        Assertions.assertNotNull(gameData);

        UnitAccounting accounting = game.getPlayerTable().getUnitAccounting();

        Assertions.assertNotNull(accounting);

        accounting.updateCompleted(overlord, gameData.getUnitTypes(), 1, false, null);

        int firstHandle = UnitHandle.encode(PRODUCER_POOL_INDEX, pool.getGeneration(PRODUCER_POOL_INDEX), UNIT_CAPACITY);
        int secondHandle = UnitHandle.encode(SECOND_LARVA_POOL_INDEX, pool.getGeneration(SECOND_LARVA_POOL_INDEX), UNIT_CAPACITY);

        UnitSelection morphSelection = game.getSelection();

        Assertions.assertNotNull(morphSelection);

        morphSelection.execute(game,
                               new SelectionRequest(SelectionRequest.Operation.REPLACE, HANDLE_WIDTH_BYTES, new long[]{secondHandle, firstHandle}),
                               PLAYER);

        Player player = game.getPlayerTable().getPlayer(PLAYER);

        Assertions.assertNotNull(player);

        player.setMinerals(WORKER_MINERAL_COST);
        game.getOrders().queueFront(second, ScrOrderIds.MOVE, null, MOVE_TARGET_POSITION, MOVE_TARGET_POSITION, ScrUnitIds.NONE);

        Assertions.assertTrue(game.getUnitProduction().morph(PLAYER, ScrUnitIds.ZERG_DRONE, INSTRUCTION_LIMIT));
        Assertions.assertEquals(ZERG_UNIT_MORPH_ORDER, second.getPrimaryOrder());
        Assertions.assertEquals(DRONE_TYPE, second.getFirstBuildQueueType());
        Assertions.assertEquals(LARVA_TYPE, second.getUnitId());
        Assertions.assertNull(second.getFirstOrder());

        OrderPool ordersAfterMorph = game.getOrderPool();

        Assertions.assertNotNull(ordersAfterMorph);

        Assertions.assertEquals(0, ordersAfterMorph.getAllocatedCount());
        Assertions.assertEquals(EMPTY_QUEUE_TYPE, first.getFirstBuildQueueType());

        Assertions.assertEquals(0, player.getMinerals());

        Assertions.assertFalse(game.getUnitProduction().morph(PLAYER, ScrUnitIds.ZERG_DRONE, INSTRUCTION_LIMIT));

        player.setMinerals(WORKER_MINERAL_COST);

        Assertions.assertTrue(game.getUnitProduction().morph(PLAYER, ScrUnitIds.ZERG_DRONE, INSTRUCTION_LIMIT));

        Assertions.assertEquals(ZERG_UNIT_MORPH_ORDER, first.getPrimaryOrder());
        Assertions.assertEquals(DRONE_TYPE, first.getFirstBuildQueueType());

        Assertions.assertEquals(0, player.getMinerals());
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void morph_should_accept_lurker_aspect_without_research_only_under_medieval_man() throws IOException {
        Game refused = createHydraliskState(0);
        UnitPool refusedPool = refused.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(refusedPool);

        Assertions.assertFalse(refused.getUnitProduction().morph(PLAYER, ScrUnitIds.ZERG_LURKER, INSTRUCTION_LIMIT),
                               "Lurker Aspect is not researched.");
        Assertions.assertEquals(EMPTY_QUEUE_TYPE, refusedPool.getUnit(PRODUCER_POOL_INDEX).getFirstBuildQueueType());

        Game accepted = createHydraliskState(GameCheats.MEDIEVAL_MAN);
        UnitPool acceptedPool = accepted.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(acceptedPool);

        Unit hydralisk = acceptedPool.getUnit(PRODUCER_POOL_INDEX);

        Assertions.assertTrue(accepted.getUnitProduction().morph(PLAYER, ScrUnitIds.ZERG_LURKER, INSTRUCTION_LIMIT));
        Assertions.assertEquals(ZERG_UNIT_MORPH_ORDER, hydralisk.getPrimaryOrder());
        Assertions.assertEquals(LURKER_TYPE, hydralisk.getFirstBuildQueueType());
    }

    @Test
    void production_rejects_retained_dying_producer_without_spending_resources() {
        for (int workerType : new int[]{ScrUnitIds.TERRAN_SCV, ScrUnitIds.ZERG_DRONE, ScrUnitIds.PROTOSS_PROBE}) {
            Game game = createState(workerType);
            UnitPool pool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(pool);

            Unit producer = pool.getUnit(PRODUCER_POOL_INDEX);

            producer.setPrimaryOrder(ScrOrderIds.DIE);
            producer.setPrimaryOrderState(Unit.DIE_STATE_DESTROYED);

            Assertions.assertFalse(workerType == ScrUnitIds.ZERG_DRONE
                                   ? game.getUnitProduction().morph(PLAYER, workerType, INSTRUCTION_LIMIT)
                                   : game.getUnitProduction().train(PLAYER, workerType));

            Player player = game.getPlayerTable().getPlayer(PLAYER);

            Assertions.assertNotNull(player);

            Assertions.assertEquals(INITIAL_MINERALS, player.getMinerals());
            Assertions.assertEquals(EMPTY_QUEUE_TYPE, producer.getFirstBuildQueueType());
        }
    }

    @Test
    void train_should_reject_missing_resources_supply_and_disabled_producer_without_mutation() {
        Game game = createState(ScrUnitIds.PROTOSS_PROBE);
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        Unit producer = pool.getUnit(PRODUCER_POOL_INDEX);

        Player player = game.getPlayerTable().getPlayer(PLAYER);

        Assertions.assertNotNull(player);

        player.setMinerals(WORKER_MINERAL_COST - 1);

        Assertions.assertFalse(game.getUnitProduction().train(PLAYER, ScrUnitIds.PROTOSS_PROBE));
        Assertions.assertEquals(EMPTY_QUEUE_TYPE, producer.getFirstBuildQueueType());

        player.setMinerals(WORKER_MINERAL_COST);
        producer.setLockdownTimer(1);

        Assertions.assertFalse(game.getUnitProduction().train(PLAYER, ScrUnitIds.PROTOSS_PROBE));

        producer.setLockdownTimer(0);

        Assertions.assertNotNull(gameData);

        UnitAccounting accounting = game.getPlayerTable().getUnitAccounting();

        Assertions.assertNotNull(accounting);

        accounting.updateCompleted(producer, gameData.getUnitTypes(), -1, false, null);

        Assertions.assertFalse(game.getUnitProduction().train(PLAYER, ScrUnitIds.PROTOSS_PROBE));

        Assertions.assertEquals(WORKER_MINERAL_COST, player.getMinerals());
        Assertions.assertEquals(EMPTY_QUEUE_TYPE, producer.getFirstBuildQueueType());

        accounting.updateCompleted(producer, gameData.getUnitTypes(), 1, false, null);

        Assertions.assertTrue(game.getUnitProduction().train(PLAYER, ScrUnitIds.PROTOSS_PROBE));
    }

    @Test
    void can_build_should_follow_drone_prerequisites_with_burrowed_extractor_exception() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(0));
        UnitAccounting accounting = PlayerProgressTestUtils.createUnitAccounting();

        accounting.initializeForGame(null);
        game.getPlayerTable().setUnitAccounting(accounting);

        Unit drone = createProducer(ScrUnitIds.ZERG_DRONE, UnitStatusFlags.COMPLETED);

        Assertions.assertTrue(game.getUnitProduction().canBuild(drone, ScrUnitIds.ZERG_HATCHERY));
        Assertions.assertTrue(game.getUnitProduction().canBuild(drone, ScrUnitIds.ZERG_CREEP_COLONY));
        Assertions.assertTrue(game.getUnitProduction().canBuild(drone, ScrUnitIds.ZERG_EXTRACTOR));
        Assertions.assertFalse(game.getUnitProduction().canBuild(drone, ScrUnitIds.ZERG_SPAWNING_POOL));
        Assertions.assertFalse(game.getUnitProduction().canBuild(drone, ScrUnitIds.ZERG_EVOLUTION_CHAMBER));

        ProductionScenario.registerCompletedPrerequisite(gameData, accounting, ScrUnitIds.ZERG_HATCHERY);

        Assertions.assertTrue(game.getUnitProduction().canBuild(drone, ScrUnitIds.ZERG_SPAWNING_POOL));
        Assertions.assertTrue(game.getUnitProduction().canBuild(drone, ScrUnitIds.ZERG_EVOLUTION_CHAMBER));
        Assertions.assertFalse(game.getUnitProduction().canBuild(drone, ScrUnitIds.ZERG_HYDRALISK_DEN));
        Assertions.assertFalse(game.getUnitProduction().canBuild(drone, ScrUnitIds.ZERG_SPIRE));

        drone.setStatus(drone.getStatus() | UnitStatusFlags.BURROWED);

        Assertions.assertFalse(game.getUnitProduction().canBuild(drone, ScrUnitIds.ZERG_SPAWNING_POOL));
        Assertions.assertFalse(game.getUnitProduction().canBuild(drone, ScrUnitIds.ZERG_HATCHERY));
        Assertions.assertTrue(game.getUnitProduction().canBuild(drone, ScrUnitIds.ZERG_EXTRACTOR));
        Assertions.assertFalse(game.getUnitProduction().canBuild(drone, ScrUnitIds.PROTOSS_NEXUS));
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void can_build_should_reject_busy_hatchery_morphs_and_tank_production_without_machine_shop() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, TILESET, new int[MAP_TILE_COUNT]), new LcgRandom(0));
        UnitAccounting accounting = PlayerProgressTestUtils.createUnitAccounting();

        accounting.initializeForGame(null);
        game.getPlayerTable().setUnitAccounting(accounting);

        Unit hatchery = createProducer(ScrUnitIds.ZERG_HATCHERY, UnitStatusFlags.COMPLETED | UnitStatusFlags.GROUNDED_BUILDING);

        hatchery.setResearchType(ScrTechIds.NONE);
        hatchery.setUpgradeType(ScrUpgradeIds.NONE);

        Assertions.assertFalse(game.getUnitProduction().canBuild(hatchery, ScrUnitIds.ZERG_LAIR));

        ProductionScenario.registerCompletedPrerequisite(gameData, accounting, ScrUnitIds.ZERG_SPAWNING_POOL);

        Assertions.assertTrue(game.getUnitProduction().canBuild(hatchery, ScrUnitIds.ZERG_LAIR));

        hatchery.setResearchType(ScrTechIds.BURROWING);

        Assertions.assertFalse(game.getUnitProduction().canBuild(hatchery, ScrUnitIds.ZERG_LAIR));

        hatchery.setResearchType(ScrTechIds.NONE);
        hatchery.setFirstBuildQueueType(ScrUnitIds.ZERG_LAIR);

        Assertions.assertFalse(game.getUnitProduction().canBuild(hatchery, ScrUnitIds.ZERG_LAIR));

        Unit barracks = createProducer(ScrUnitIds.TERRAN_BARRACKS, UnitStatusFlags.COMPLETED | UnitStatusFlags.GROUNDED_BUILDING);

        Assertions.assertTrue(game.getUnitProduction().canBuild(barracks, ScrUnitIds.TERRAN_MARINE));
        Assertions.assertFalse(game.getUnitProduction().canBuild(barracks, ScrUnitIds.TERRAN_FIREBAT));

        barracks.setStatus(UnitStatusFlags.COMPLETED);

        Assertions.assertFalse(game.getUnitProduction().canBuild(barracks, ScrUnitIds.TERRAN_MARINE));

        Unit factory = createProducer(ScrUnitIds.TERRAN_FACTORY, UnitStatusFlags.COMPLETED | UnitStatusFlags.GROUNDED_BUILDING);

        Assertions.assertTrue(game.getUnitProduction().canBuild(factory, ScrUnitIds.TERRAN_VULTURE));
        Assertions.assertFalse(game.getUnitProduction().canBuild(factory, ScrUnitIds.TERRAN_SIEGE_TANK_TANK_MODE));
        Assertions.assertFalse(game.getUnitProduction().canBuild(barracks, ScrUnitIds.TERRAN_SIEGE_TANK_TANK_MODE));
    }

    @Test
    void can_build_should_apply_terran_addon_relationship_to_production_requirements() {
        Game game = ProductionScenario.createConstructionGame(gameData);
        UnitAccounting accounting = game.getPlayerTable().getUnitAccounting();
        Unit factory = ProductionScenario.createCompletedUnit(game,
                                                              ScrUnitIds.TERRAN_FACTORY,
                                                              FACTORY_POSITION, FACTORY_POSITION,
                                                              ProductionScenario.PLAYER);
        Unit machineShop = ProductionScenario.createCompletedUnit(game,
                                                                  ScrUnitIds.TERRAN_MACHINE_SHOP,
                                                                  MACHINE_SHOP_X, MACHINE_SHOP_Y,
                                                                  ProductionScenario.PLAYER);

        Assertions.assertTrue(game.getUnitProduction().canBuild(factory, ScrUnitIds.TERRAN_VULTURE));
        Assertions.assertFalse(game.getUnitProduction().canBuild(factory, ScrUnitIds.TERRAN_SIEGE_TANK_TANK_MODE));
        Assertions.assertTrue(game.getUnitProduction().canBuild(factory, ScrUnitIds.TERRAN_MACHINE_SHOP));

        factory.setAddon(machineShop);

        Assertions.assertTrue(game.getUnitProduction().canBuild(factory, ScrUnitIds.TERRAN_SIEGE_TANK_TANK_MODE));
        Assertions.assertFalse(game.getUnitProduction().canBuild(factory, ScrUnitIds.TERRAN_MACHINE_SHOP));

        factory.setAddon(null);
        factory.setFirstBuildQueueType(ScrUnitIds.TERRAN_VULTURE);

        Assertions.assertFalse(game.getUnitProduction().canBuild(factory, ScrUnitIds.TERRAN_MACHINE_SHOP));

        factory.setFirstBuildQueueType(ScrUnitIds.NONE);

        Unit commandCenter = ProductionScenario.createCompletedUnit(game,
                                                                    ScrUnitIds.TERRAN_COMMAND_CENTER,
                                                                    COMMAND_CENTER_POSITION, COMMAND_CENTER_POSITION,
                                                                    ProductionScenario.PLAYER);
        long randomCount = game.getRandom().getTotalRandomCounts();

        Assertions.assertFalse(game.getUnitProduction().canBuild(commandCenter, ScrUnitIds.TERRAN_COMSAT_STATION));

        int prerequisiteType = ScrUnitIds.TERRAN_ACADEMY;
        Unit prerequisite = UnitTestUtils.createUnit(PREREQUISITE_POOL_INDEX);

        prerequisite.setOwnerIndex(ProductionScenario.PLAYER);
        prerequisite.setUnitId(prerequisiteType);

        Assertions.assertNotNull(accounting);
        Assertions.assertNotNull(gameData);

        accounting.update(prerequisite, gameData.getUnitTypes(), 1, false);
        accounting.updateCompleted(prerequisite, gameData.getUnitTypes(), 1, false, null);

        Assertions.assertTrue(game.getUnitProduction().canBuild(commandCenter, ScrUnitIds.TERRAN_COMSAT_STATION));
        Assertions.assertFalse(game.getUnitProduction().canBuild(commandCenter, ScrUnitIds.TERRAN_NUCLEAR_SILO));

        commandCenter.setResearchType(ScrTechIds.LOCKDOWN);

        Assertions.assertFalse(game.getUnitProduction().canBuild(commandCenter, ScrUnitIds.TERRAN_COMSAT_STATION));
        Assertions.assertEquals(randomCount, game.getRandom().getTotalRandomCounts());
    }
}
