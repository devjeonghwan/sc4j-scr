package org.fogofwarlabs.sc4j.scr.game.production;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.ButtonSetIds;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.ai.ComputerUnitRecord;
import org.fogofwarlabs.sc4j.scr.game.ai.UnitStrengthTable;
import org.fogofwarlabs.sc4j.scr.game.order.OrderPool;
import org.fogofwarlabs.sc4j.scr.game.path.PathPool;
import org.fogofwarlabs.sc4j.scr.game.player.*;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpritePool;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.*;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitSpatialIndex;
import org.fogofwarlabs.sc4j.scr.game.visibility.SightCoverage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.stream.IntStream;

/**
 * Checks morph completion and accounting for Zerg units, and the button set writes of a morph.
 * <p>
 * The unit record layout is the contract of the test past the five queue slots: queue index {@code 5} is the EUD-visible energy WORD, and queue index
 * {@code 6} is the queue index BYTE with the generation BYTE above it.
 */
@RequiresInstallation
final class UnitMorphingTest {
    private static final int SMALL_MAP_DIMENSION = 8;
    private static final int SMALL_MAP_TILE_COUNT = SMALL_MAP_DIMENSION * SMALL_MAP_DIMENSION;
    private static final int MAP_DIMENSION = 32;
    private static final int MAP_TILE_COUNT = MAP_DIMENSION * MAP_DIMENSION;
    private static final int ORDER_POOL_CAPACITY = 16;
    private static final int IMAGE_POOL_CAPACITY = 16;
    private static final int REFUND_UNIT_CAPACITY = 1;
    private static final int REFUND_SPRITE_CAPACITY = 2;
    private static final int MORPH_UNIT_CAPACITY = 2;
    private static final int RESOURCE_MORPH_UNIT_CAPACITY = 3;
    private static final int MORPH_SPRITE_CAPACITY = 3;
    private static final int INSTRUCTION_LIMIT = 10000;

    private static final int PLAYER = 0;
    /**
     * The player identifier and the team of the test player.
     */
    private static final int PLAYER_ID = 0;
    private static final int TEAM = 0;
    /**
     * The local player comparison value of the unit creation context, a slot that owns no test unit.
     */
    private static final int LOCAL_PLAYER = 8;
    private static final int LOCAL_COMPARISON_MASK = 1 << PLAYER;
    /**
     * The race counter base of the Zerg supply counters.
     */
    private static final int ZERG_SUPPLY_COUNTERS = 0;
    /**
     * The half-supply amount of the supply tests.
     */
    private static final int SUPPLY_REQUEST = 16;
    private static final int STARTING_MINERALS = 7;
    private static final int STARTING_GAS = 11;

    private static final int ACTIVE_WORKER_FIXTURE_INDEX = 0;
    private static final int RESOURCE_FIXTURE_INDEX = 1;
    private static final int WAITING_WORKER_FIXTURE_INDEX = 2;
    private static final int WORKER_POSITION = 320;
    private static final int RESOURCE_POSITION_X = 448;
    private static final int RESOURCE_POSITION_Y = 352;

    private static final int LARVA_POSITION = 320;
    private static final int RALLY_X = 400;
    private static final int RALLY_Y = 350;
    /**
     * The Drones of the wrapped queue of the refund test.
     */
    private static final int QUEUED_DRONES = 3;
    /**
     * The computer record request kinds of the first, second, third and fifth queue slots.
     */
    private static final int FIRST_REQUEST_KIND = 11;
    private static final int SECOND_REQUEST_KIND = 22;
    private static final int THIRD_REQUEST_KIND = 33;
    private static final int FIFTH_REQUEST_KIND = 55;
    /**
     * The pool index of the active child of the refund test; the morphing unit has pool index {@code 1}.
     */
    private static final int CHILD_POOL_INDEX = 2;
    /**
     * The pool indices of the Overlord and the parent of the Larva morph test; the Larva has pool index {@code 1}.
     */
    private static final int OVERLORD_POOL_INDEX = 2;
    private static final int PARENT_POOL_INDEX = 3;
    /**
     * The pool index of the second unit of a paired egg.
     */
    private static final int SECOND_OFFSPRING_POOL_INDEX = 2;
    /**
     * The divisor of the Larva hit points that the Larva morph test stores as the hit points before the morph.
     */
    private static final int PREVIOUS_HIT_POINT_DIVISOR = 2;

    private static final int START_STATE = 0;
    private static final int COUNTDOWN_STATE = 1;
    private static final int HATCHING_STATE = 2;
    private static final int UNKNOWN_STATE = 3;
    /**
     * The largest BYTE, a state and a queue index that the morph does not know.
     */
    private static final int MAXIMUM_BYTE = 0xFF;
    /**
     * The build queue index whose WORD is the energy, the first WORD after the five queue slots.
     */
    private static final int ENERGY_QUEUE_INDEX = 5;
    /**
     * The build queue index whose WORD is the queue index BYTE with the generation BYTE above it, the second WORD after the five queue slots.
     */
    private static final int INDEX_WORD_QUEUE_INDEX = 6;
    /**
     * The generation of the Larva's pool slot, so that the queue index WORD reads as the queue index alone.
     */
    private static final int CLEARED_GENERATION = 0;
    /**
     * A unit type WORD past every unit type.
     */
    private static final int UNKNOWN_UNIT_TYPE = 0xFFFF;

    private static final int VISIBILITY_FLAGS = 7;
    private static final int REMAINING_BUILD_TIME = 7;
    private static final int SHORT_REMAINING_BUILD_TIME = 2;
    private static final int ORDER_COUNTDOWN = 7;
    private static final int FIXED_POINT_SHIFT = 8;
    /**
     * The shift from a units.dat Unit Direction to a heading.
     */
    private static final int DIRECTION_TO_HEADING_SHIFT = 3;

    /**
     * The default button sets of the Zerg Egg and of the Zerg Larva.
     */
    private static final int EGG_BUTTON_SET = 36;
    private static final int LARVA_BUTTON_SET = 35;
    /**
     * The empty button set and the cancel-morph button set.
     */
    private static final int EMPTY_BUTTON_SET = 228;
    private static final int CANCEL_MORPH_BUTTON_SET = 236;
    /**
     * The unit types that the morphs leave.
     */
    private static final int LARVA_TYPE = 35;
    private static final int EGG_TYPE = 36;
    private static final int HYDRALISK_TYPE = 38;
    private static final int DRONE_TYPE = 41;
    private static final int DEVOURING_ONE_TYPE = 54;
    private static final int HATCHERY_TYPE = 131;
    private static final int EMPTY_UNIT_TYPE = 228;
    /**
     * The orders of a hatched unit that moves to the rally point, of a hatched unit with a birth animation and of a morphing Larva or Egg.
     */
    private static final int MOVE_ORDER = 6;
    private static final int ZERG_BIRTH_ORDER = 41;
    private static final int ZERG_UNIT_MORPH_ORDER = 42;
    /**
     * The completed status flag.
     */
    private static final int COMPLETED_FLAG = 0x1;
    /**
     * The units that hatch from an Egg of a paired type.
     */
    private static final int OFFSPRING_COUNT = 2;
    /**
     * The unit pool indexes of the Marine and of the Hatchery of the button set test.
     */
    private static final int MARINE_POOL_INDEX = 1;
    private static final int HATCHERY_POOL_INDEX = 2;

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

    static @NotNull IntStream getLarvaMorphTypes() {
        return IntStream.of(ScrUnitIds.ZERG_ZERGLING, ScrUnitIds.ZERG_HYDRALISK, ScrUnitIds.ZERG_ULTRALISK,
                            ScrUnitIds.ZERG_DRONE, ScrUnitIds.ZERG_OVERLORD, ScrUnitIds.ZERG_MUTALISK,
                            ScrUnitIds.ZERG_QUEEN, ScrUnitIds.ZERG_DEFILER, ScrUnitIds.ZERG_SCOURGE,
                            ScrUnitIds.HERO_TORRASQUE, ScrUnitIds.HERO_DEVOURING_ONE, ScrUnitIds.CRITTER_RHYNADON);
    }

    private static @NotNull Game createMorphGame(int unitCapacity) {
        GameMap map = new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT], new int[MAP_TILE_COUNT]);
        Game game = Game.create(gameData, map, new LcgRandom(0));

        game.getMap().initializeOverlayTiles();

        SightCoverage sight = new SightCoverage(MAP_DIMENSION);
        int[] sharedVision = new int[PlayerTable.PLAYER_SLOTS];

        for (int playerIndex = 0; playerIndex < sharedVision.length; playerIndex++) {
            sharedVision[playerIndex] = 1 << playerIndex;
        }

        sight.setSharedVisionMasks(sharedVision);
        game.setSightCoverage(sight);
        game.getLocalVision().setComparisonMask(LOCAL_COMPARISON_MASK);
        game.getUnitSystem().initializeUnitPool(unitCapacity);
        game.getSpriteSystem().initializeSpritePool(MORPH_SPRITE_CAPACITY);
        game.getSpriteSystem().initializeImagePool(IMAGE_POOL_CAPACITY);
        game.initializeOrderPool(ORDER_POOL_CAPACITY);
        game.initializeExecutionScope();
        game.getPlayerTable().setPlayer(PLAYER, new Player(PLAYER, PLAYER_ID, PlayerTypes.HUMAN, ScrRaceIds.ZERG, TEAM));
        game.getPlayerTable().setPlayerUpgrades(PlayerProgressTestUtils.createPlayerUpgrades(true));
        game.initializeUnitSpatialIndex();

        UnitSpatialIndex spatialIndex = game.getUnitSystem().getUnitSpatialIndex();

        Assertions.assertNotNull(spatialIndex);

        spatialIndex.initializeFlyingUnits();
        game.getPathSystem().setPathPool(new PathPool());

        Assertions.assertNotNull(gameData);

        game.setUnitStrengthTable(new UnitStrengthTable(gameData.getUnitTypes(), gameData.getWeaponTypes()));
        game.getUnitSystem().setUnitCreationContext(new UnitCreationContext(false, false, false, false, false, LOCAL_PLAYER));

        UnitAccounting accounting = PlayerProgressTestUtils.createUnitAccounting();

        accounting.initializeForGame(null);
        game.getPlayerTable().setUnitAccounting(accounting);
        game.getPlayerTable().setProductionStatistics(new ProductionStatistics(true, new int[ProductionStatistics.VALUE_COUNT],
                                                                               new float[ProductionStatistics.VALUE_COUNT]));

        return game;
    }


    @Test
    void update_primary_should_not_dispatch_building_morph_as_larva_morph() {
        Game game = Game.create(gameData, new GameMap(SMALL_MAP_DIMENSION, SMALL_MAP_DIMENSION, 0, new int[SMALL_MAP_TILE_COUNT]), new LcgRandom(0));
        Unit building = UnitTestUtils.createUnit(1);

        game.initializeOrderPool(ORDER_POOL_CAPACITY);
        game.getPlayerTable().setPlayer(PLAYER, new Player(PLAYER, PLAYER_ID, PlayerTypes.HUMAN, ScrRaceIds.ZERG, TEAM));

        building.setUnitId(ScrUnitIds.ZERG_HATCHERY);
        building.setOwnerIndex(PLAYER);
        building.setStatus(UnitStatusFlags.COMPLETED);
        building.setPrimaryOrder(ScrOrderIds.ZERG_BUILDING_MORPH);
        building.setFirstBuildQueueType(ScrUnitIds.ZERG_LAIR);

        game.getUnitOrders().updatePrimary(building, PlayerTypes.HUMAN, INSTRUCTION_LIMIT);

        // The building morph fails its cost test, keeps the Hatchery completed and queues its return to idle; a Larva morph would replace the type.
        Assertions.assertNotNull(gameData);
        Assertions.assertEquals(HATCHERY_TYPE, building.getUnitId());
        Assertions.assertEquals(COMPLETED_FLAG, building.getStatus() & UnitStatusFlags.COMPLETED);

        OrderPool.Order lastOrder = building.getLastOrder();

        Assertions.assertNotNull(lastOrder);
        Assertions.assertEquals(gameData.getUnitTypes().getReturnToIdleOrder(ScrUnitIds.ZERG_HATCHERY), lastOrder.getOrderId());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void update_should_refund_wrapped_queue_and_keep_larva_when_supply_is_lost(boolean activeChild) {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]), new LcgRandom(0));

        game.getUnitSystem().initializeUnitPool(REFUND_UNIT_CAPACITY);
        game.getSpriteSystem().initializeSpritePool(REFUND_SPRITE_CAPACITY);
        game.getSpriteSystem().initializeImagePool(IMAGE_POOL_CAPACITY);
        game.initializeOrderPool(ORDER_POOL_CAPACITY);
        game.initializeExecutionScope();
        game.initializeUnitSpatialIndex();

        Assertions.assertNotNull(gameData);

        game.setUnitStrengthTable(new UnitStrengthTable(gameData.getUnitTypes(), gameData.getWeaponTypes()));
        game.getUnitSystem().setUnitCreationContext(new UnitCreationContext(false, false, false, false, false, LOCAL_PLAYER));

        UnitAccounting accounting = PlayerProgressTestUtils.createUnitAccounting();

        accounting.initializeForGame(null);
        game.getPlayerTable().setUnitAccounting(accounting);

        Player player = new Player(PLAYER, PLAYER_ID, PlayerTypes.HUMAN, ScrRaceIds.ZERG, TEAM);

        player.setMinerals(STARTING_MINERALS);
        player.setGas(STARTING_GAS);
        game.getPlayerTable().setPlayer(PLAYER, player);

        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit unit = unitPool.getUnit(1);

        UnitStorageConstructor.clearBeforeInnerInitialization(unit);

        Assertions.assertTrue(game.getUnitInitializer().initialize(unit, ScrUnitIds.ZERG_LARVA, LARVA_POSITION, LARVA_POSITION, PLAYER,
                                                                   INSTRUCTION_LIMIT));
        unit.setStatus(unit.getStatus() | UnitStatusFlags.COMPLETED);
        unit.setPrimaryOrder(ScrOrderIds.ZERG_UNIT_MORPH);
        unit.setBuildQueueIndex(Unit.FIFTH_QUEUE_SLOT);
        unit.setFifthBuildQueueType(ScrUnitIds.ZERG_DRONE);
        unit.setFirstBuildQueueType(ScrUnitIds.NONE);
        unit.setSecondBuildQueueType(ScrUnitIds.ZERG_DRONE);
        unit.setThirdBuildQueueType(ScrUnitIds.NONE);
        unit.setFourthBuildQueueType(ScrUnitIds.ZERG_DRONE);

        ComputerUnitRecord record = new ComputerUnitRecord(ComputerUnitRecord.KIND_BUILDING, 0);
        Object firstRequest = new Object();
        Object thirdRequest = new Object();

        record.queueKinds[Unit.FIRST_QUEUE_SLOT]   = FIRST_REQUEST_KIND;
        record.queueKinds[Unit.SECOND_QUEUE_SLOT]  = SECOND_REQUEST_KIND;
        record.queueKinds[Unit.THIRD_QUEUE_SLOT]   = THIRD_REQUEST_KIND;
        record.queueKinds[Unit.FIFTH_QUEUE_SLOT]   = FIFTH_REQUEST_KIND;
        record.queueTargets[Unit.FIRST_QUEUE_SLOT] = firstRequest;
        record.queueTargets[Unit.THIRD_QUEUE_SLOT] = thirdRequest;
        unit.setComputerRecord(record);

        Unit child = UnitTestUtils.createUnit(CHILD_POOL_INDEX);

        child.setPrimaryOrder(ScrOrderIds.DIE);

        if (activeChild) {
            unit.setCurrentBuildUnit(child);
        }

        Sprite sprite = unit.getSpriteOrNull();
        int generation = unitPool.getGeneration(1);
        int randomCount = game.getRandom().getTotalRandomCounts();

        game.getUnitMorphing().update(unit, INSTRUCTION_LIMIT);

        int refundedDrones = activeChild ? QUEUED_DRONES - 1 : QUEUED_DRONES;

        Assertions.assertEquals(LARVA_TYPE, unit.getUnitId());
        Assertions.assertEquals(gameData.getUnits().get("Return to Idle", ScrUnitIds.ZERG_LARVA), unit.getPrimaryOrder());
        Assertions.assertEquals(STARTING_MINERALS + refundedDrones * gameData.getUnits().get("Mineral Cost", ScrUnitIds.ZERG_DRONE),
                                player.getMinerals());
        Assertions.assertEquals(STARTING_GAS, player.getGas());
        Assertions.assertEquals(0, unit.getBuildQueueIndex());

        for (int position = 0; position < Unit.BUILD_QUEUE_SLOTS; position++) {
            Assertions.assertEquals(EMPTY_UNIT_TYPE, unit.getBuildQueueUnitType(position));
        }

        Assertions.assertSame(sprite, unit.getSpriteOrNull());
        Assertions.assertEquals(generation, unitPool.getGeneration(1));
        Assertions.assertEquals(randomCount, game.getRandom().getTotalRandomCounts());
        Assertions.assertEquals(COMPLETED_FLAG, unit.getStatus() & UnitStatusFlags.COMPLETED);
        Assertions.assertEquals(THIRD_REQUEST_KIND, record.queueKinds[Unit.SECOND_QUEUE_SLOT]);
        Assertions.assertEquals(FIRST_REQUEST_KIND, record.queueKinds[Unit.FIFTH_QUEUE_SLOT]);
        Assertions.assertSame(thirdRequest, record.queueTargets[Unit.SECOND_QUEUE_SLOT]);
        Assertions.assertSame(firstRequest, record.queueTargets[Unit.FIFTH_QUEUE_SLOT]);
        Assertions.assertSame(activeChild ? child : null, unit.getCurrentBuildUnit());
    }

    @ParameterizedTest
    @MethodSource("getLarvaMorphTypes")
    void larva_morph_replaces_graphics_and_path_with_incomplete_egg_then_hatches(int requestedType) {
        Game game = createMorphGame(MORPH_UNIT_CAPACITY);
        UnitSpatialIndex spatialIndex = game.getUnitSystem().getUnitSpatialIndex();
        UnitAccounting accounting = game.getPlayerTable().getUnitAccounting();
        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(spatialIndex);
        Assertions.assertNotNull(accounting);
        Assertions.assertNotNull(unitPool);
        Assertions.assertNotNull(gameData);

        Unit unit = unitPool.getUnit(1);

        UnitStorageConstructor.clearBeforeInnerInitialization(unit);

        Assertions.assertTrue(game.getUnitInitializer().initialize(unit, ScrUnitIds.ZERG_LARVA, LARVA_POSITION, LARVA_POSITION, PLAYER,
                                                                   INSTRUCTION_LIMIT));

        unitPool.commitInitializedUnit(unit);
        unit.setStatus(unit.getStatus() | UnitStatusFlags.COMPLETED);

        int previousHitPoints = gameData.getUnits().get("Hit Points", ScrUnitIds.ZERG_LARVA) / PREVIOUS_HIT_POINT_DIVISOR;

        unit.setHitPoints(previousHitPoints);
        unit.setPrimaryOrder(ScrOrderIds.ZERG_UNIT_MORPH);
        unit.setPrimaryOrderState(START_STATE);
        unit.setFirstBuildQueueType(requestedType);
        unit.setSecondBuildQueueType(ScrUnitIds.NONE);
        unit.setThirdBuildQueueType(ScrUnitIds.NONE);
        unit.setFourthBuildQueueType(ScrUnitIds.NONE);
        unit.setFifthBuildQueueType(ScrUnitIds.NONE);
        unit.setBuildQueueIndex(0);

        Sprite visibilityFlagsSprite = unit.getSpriteOrNull();

        Assertions.assertNotNull(visibilityFlagsSprite);

        visibilityFlagsSprite.setVisibilityFlags(VISIBILITY_FLAGS);

        PathPool pathPool = game.getPathSystem().getPathPool();

        Assertions.assertNotNull(pathPool);

        int path = pathPool.allocate();

        unit.setPathIndex(path);

        spatialIndex.insert(unit, gameData.getUnitTypes());
        accounting.update(unit, gameData.getUnitTypes(), 1, false);
        accounting.updateCompleted(unit, gameData.getUnitTypes(), 1, false, null);

        Unit overlord = UnitTestUtils.createUnit(OVERLORD_POOL_INDEX);

        overlord.setUnitId(ScrUnitIds.ZERG_OVERLORD);
        overlord.setOwnerIndex(PLAYER);
        overlord.setStatus(UnitStatusFlags.COMPLETED);
        accounting.updateCompleted(overlord, gameData.getUnitTypes(), 1, false, null);

        Unit parent = UnitTestUtils.createUnit(PARENT_POOL_INDEX);
        boolean constructionImage = gameData.getUnitTypes().getConstructionImage(requestedType) != 0;

        parent.setUnitId(ScrUnitIds.ZERG_HATCHERY);

        if (!constructionImage) {
            parent.setRallyPositionX(RALLY_X);
            parent.setRallyPositionY(RALLY_Y);
        }

        unit.setParentUnit(parent);

        Assertions.assertEquals(1, accounting.getCompletedTypeCount(ScrUnitIds.ZERG_LARVA, PLAYER));
        Assertions.assertTrue(accounting.hasAvailableSupply(PLAYER, ZERG_SUPPLY_COUNTERS, SUPPLY_REQUEST));

        Sprite previousSprite = unit.getSpriteOrNull();

        game.getUnitMorphing().update(unit, INSTRUCTION_LIMIT);

        Assertions.assertEquals(EGG_TYPE, unit.getUnitId());
        Assertions.assertEquals(0, unit.getStatus() & UnitStatusFlags.COMPLETED);
        Assertions.assertEquals(ZERG_UNIT_MORPH_ORDER, unit.getPrimaryOrder());
        Assertions.assertEquals(COUNTDOWN_STATE, unit.getPrimaryOrderState());
        Assertions.assertEquals(requestedType, unit.getFirstBuildQueueType());
        Assertions.assertEquals(gameData.getUnits().get("Build Time", requestedType), unit.getRemainingBuildTime());

        int eggHitPoints = gameData.getUnits().get("Hit Points", ScrUnitIds.ZERG_EGG) >> FIXED_POINT_SHIFT;
        int larvaHitPoints = gameData.getUnits().get("Hit Points", ScrUnitIds.ZERG_LARVA) >> FIXED_POINT_SHIFT;
        int expectedHitPoints = ((previousHitPoints >> FIXED_POINT_SHIFT) * eggHitPoints / larvaHitPoints) << FIXED_POINT_SHIFT;

        Assertions.assertEquals(expectedHitPoints, unit.getHitPoints());
        Assertions.assertEquals(LARVA_TYPE, unit.getPreviousUnitType());
        Assertions.assertEquals(previousHitPoints >> FIXED_POINT_SHIFT, unit.getPreviousVisibleHitPoints());
        Assertions.assertSame(parent, unit.getParentUnit());
        Assertions.assertEquals(0, accounting.getCompletedTypeCount(ScrUnitIds.ZERG_LARVA, PLAYER));

        int requiredSupply = gameData.getUnits().get("Supply Required", requestedType);
        boolean paired = (gameData.getUnitTypes().getFlags(requestedType) & UnitTypeFlags.TWO_UNITS_IN_ONE_EGG) != 0;

        if (paired) {
            requiredSupply *= OFFSPRING_COUNT;
        }

        Assertions.assertEquals(requiredSupply == 0, accounting.hasAvailableSupply(PLAYER, ZERG_SUPPLY_COUNTERS, SUPPLY_REQUEST));
        Assertions.assertTrue(accounting.hasAvailableSupply(PLAYER, ZERG_SUPPLY_COUNTERS, SUPPLY_REQUEST - requiredSupply));
        Assertions.assertEquals(-1, unit.getPathIndex());
        Assertions.assertEquals(path, pathPool.getFirstFreeIndex());

        Sprite eggSprite = unit.getSpriteOrNull();

        Assertions.assertNotNull(eggSprite);
        Assertions.assertNotSame(previousSprite, eggSprite);
        Assertions.assertEquals(VISIBILITY_FLAGS, eggSprite.getVisibilityFlags());

        SpritePool spritePool = game.getSpriteSystem().getSpritePool();

        Assertions.assertNotNull(spritePool);
        Assertions.assertEquals(1, spritePool.getAllocatedCount());
        Assertions.assertTrue(unit.getMinimumHorizontalEdgeIndex() >= 0);

        unit.setRemainingBuildTime(REMAINING_BUILD_TIME);

        int remaining = unit.getRemainingBuildTime();

        unit.setPrimaryOrderCountdown(ORDER_COUNTDOWN);

        game.getUnitOrders().updatePrimary(unit, PlayerTypes.HUMAN, INSTRUCTION_LIMIT);

        Assertions.assertEquals(remaining - 1, unit.getRemainingBuildTime());
        Assertions.assertEquals(ORDER_COUNTDOWN - 1, unit.getPrimaryOrderCountdown());

        unit.setPrimaryOrderState(HATCHING_STATE);
        unit.setOrderSignal(Unit.ORDER_SIGNAL_ANIMATION_DONE);
        unit.setRemainingBuildTime(0);
        unit.setSecondBuildQueueType(ScrUnitIds.ZERG_DRONE);

        game.getUnitMorphing().update(unit, INSTRUCTION_LIMIT);

        Assertions.assertEquals(requestedType, unit.getUnitId());
        Assertions.assertEquals(EGG_TYPE, unit.getPreviousUnitType());
        Assertions.assertEquals(EMPTY_UNIT_TYPE, unit.getFirstBuildQueueType());
        Assertions.assertEquals(0, unit.getBuildQueueIndex());
        Assertions.assertEquals(DRONE_TYPE, unit.getSecondBuildQueueType());
        Assertions.assertEquals(constructionImage ? ZERG_BIRTH_ORDER : MOVE_ORDER, unit.getPrimaryOrder());
        Assertions.assertEquals(constructionImage ? 0 : COMPLETED_FLAG, unit.getStatus() & UnitStatusFlags.COMPLETED);

        if (!constructionImage) {
            Assertions.assertEquals(RALLY_X, unit.getOrderTargetX());
            Assertions.assertEquals(RALLY_Y, unit.getOrderTargetY());
        }

        int existingCompleted = requestedType == ScrUnitIds.ZERG_OVERLORD ? 1 : 0;

        Assertions.assertEquals(existingCompleted + (constructionImage ? 0 : 1), accounting.getCompletedTypeCount(requestedType, PLAYER));

        if (constructionImage) {
            unit.setOrderSignal(Unit.ORDER_SIGNAL_ANIMATION_DONE);

            game.getUnitMorphing().birth(unit, INSTRUCTION_LIMIT);
        }

        int hatchedUnits = paired ? OFFSPRING_COUNT : 1;

        Assertions.assertEquals(COMPLETED_FLAG, unit.getStatus() & UnitStatusFlags.COMPLETED);
        Assertions.assertEquals(existingCompleted + hatchedUnits, accounting.getCompletedTypeCount(requestedType, PLAYER));
        Assertions.assertEquals(0, accounting.getCompletedTypeCount(ScrUnitIds.ZERG_EGG, PLAYER));
        Assertions.assertEquals(gameData.getUnits().get("Hit Points", requestedType), unit.getHitPoints());
        Assertions.assertEquals(0, unit.getRemainingBuildTime());
        Assertions.assertEquals(0, unit.getStatus() & UnitStatusFlags.ORDER_NOT_INTERRUPTIBLE);
        Assertions.assertEquals(hatchedUnits, spritePool.getAllocatedCount());

        if (paired) {
            Unit secondUnit = unitPool.getUnit(SECOND_OFFSPRING_POOL_INDEX);

            Assertions.assertEquals(requestedType, secondUnit.getUnitId());
            Assertions.assertEquals(gameData.getUnits().get("Unit Direction", requestedType) << DIRECTION_TO_HEADING_SHIFT, secondUnit.getHeading());
            Assertions.assertEquals(COMPLETED_FLAG, secondUnit.getStatus() & UnitStatusFlags.COMPLETED);
            Assertions.assertEquals(OFFSPRING_COUNT, unitPool.getAllocatedCount());
            Assertions.assertTrue(secondUnit.getMinimumHorizontalEdgeIndex() >= 0);
        }

        Assertions.assertSame(parent, unit.getParentUnit());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void morph_should_retain_resource_links_for_active_and_waiting_workers(boolean isWaitingWorker) {
        Game game = createMorphGame(RESOURCE_MORPH_UNIT_CAPACITY);
        GameData data = gameData;
        UnitPool unitPool = game.getUnitSystem().getUnitPool();
        UnitSpatialIndex spatialIndex = game.getUnitSystem().getUnitSpatialIndex();
        UnitAccounting accounting = game.getPlayerTable().getUnitAccounting();

        Assertions.assertNotNull(data);
        Assertions.assertNotNull(unitPool);
        Assertions.assertNotNull(spatialIndex);
        Assertions.assertNotNull(accounting);

        Unit[] fixtureUnits = new Unit[RESOURCE_MORPH_UNIT_CAPACITY];

        for (int fixtureIndex = 0; fixtureIndex < fixtureUnits.length; fixtureIndex++) {
            int availableUnitIndex = unitPool.getFirstFreeIndex();

            Assertions.assertNotEquals(0, availableUnitIndex);

            Unit fixtureUnit = unitPool.getUnit(availableUnitIndex);
            boolean isResource = fixtureIndex == RESOURCE_FIXTURE_INDEX;
            int unitType = isResource ? ScrUnitIds.RESOURCE_MINERAL_FIELD : ScrUnitIds.ZERG_DRONE;
            int owner = isResource ? PlayerTable.NEUTRAL_PLAYER_ID : PLAYER;
            int positionX = isResource ? RESOURCE_POSITION_X : WORKER_POSITION;
            int positionY = isResource ? RESOURCE_POSITION_Y : WORKER_POSITION;

            UnitStorageConstructor.clearBeforeInnerInitialization(fixtureUnit);

            Assertions.assertTrue(game.getUnitInitializer().initialize(fixtureUnit, unitType, positionX, positionY, owner, INSTRUCTION_LIMIT));

            unitPool.commitInitializedUnit(fixtureUnit);
            fixtureUnit.setStatus(fixtureUnit.getStatus() | UnitStatusFlags.COMPLETED);
            spatialIndex.insert(fixtureUnit, data.getUnitTypes());
            accounting.update(fixtureUnit, data.getUnitTypes(), 1, false);
            accounting.updateCompleted(fixtureUnit, data.getUnitTypes(), 1, false, null);
            fixtureUnits[fixtureIndex] = fixtureUnit;
        }

        Unit activeWorker = fixtureUnits[ACTIVE_WORKER_FIXTURE_INDEX];
        Unit resource = fixtureUnits[RESOURCE_FIXTURE_INDEX];
        Unit waitingWorker = fixtureUnits[WAITING_WORKER_FIXTURE_INDEX];

        Assertions.assertNotNull(activeWorker);
        Assertions.assertNotNull(resource);
        Assertions.assertNotNull(waitingWorker);

        activeWorker.setPrimaryOrder(ScrOrderIds.WAIT_FOR_MINERALS);
        activeWorker.setPrimaryOrderState(0);
        activeWorker.setOrderTargetUnit(resource);
        game.getUnitHarvesting().waitForMinerals(activeWorker, INSTRUCTION_LIMIT);

        Assertions.assertTrue(resource.isResourceBeingGathered());
        Assertions.assertTrue(activeWorker.isGatheringResource());
        Assertions.assertSame(resource, activeWorker.getGatherTarget());

        waitingWorker.setPrimaryOrder(ScrOrderIds.WAIT_FOR_MINERALS);
        waitingWorker.setPrimaryOrderState(0);
        waitingWorker.setOrderTargetUnit(resource);
        game.getUnitHarvesting().waitForMinerals(waitingWorker, INSTRUCTION_LIMIT);

        Assertions.assertFalse(waitingWorker.isGatheringResource());
        Assertions.assertSame(resource, waitingWorker.getGatherTarget());
        Assertions.assertSame(waitingWorker, resource.getFirstGatheringWorker());
        Assertions.assertSame(waitingWorker, resource.getLastGatheringWorker());

        Unit worker = isWaitingWorker ? waitingWorker : activeWorker;
        int previousOrder = worker.getPrimaryOrder();
        int previousOrderState = worker.getPrimaryOrderState();
        int generation = unitPool.getGeneration(worker.getIndex());

        game.getUnitMorphing().morph(worker, ScrUnitIds.ZERG_DRONE, INSTRUCTION_LIMIT);

        Assertions.assertSame(resource, worker.getGatherTarget());
        Assertions.assertTrue(resource.isResourceBeingGathered());
        Assertions.assertSame(waitingWorker, resource.getFirstGatheringWorker());
        Assertions.assertSame(waitingWorker, resource.getLastGatheringWorker());
        Assertions.assertNull(waitingWorker.getPreviousGatheringWorker());
        Assertions.assertNull(waitingWorker.getNextGatheringWorker());
        Assertions.assertFalse(worker.isGatheringResource());
        Assertions.assertEquals(DRONE_TYPE, worker.getUnitId());
        Assertions.assertEquals(previousOrder, worker.getPrimaryOrder());
        Assertions.assertEquals(previousOrderState, worker.getPrimaryOrderState());
        Assertions.assertEquals(generation, unitPool.getGeneration(worker.getIndex()));

        if (isWaitingWorker) {
            Assertions.assertTrue(activeWorker.isGatheringResource());
            Assertions.assertSame(resource, activeWorker.getGatherTarget());
        }
    }

    @Test
    void update_should_read_the_energy_as_the_requested_type_at_queue_index_five() {
        Game game = createMorphGame(MORPH_UNIT_CAPACITY);
        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);
        Assertions.assertNotNull(gameData);

        Unit unit = unitPool.getUnit(1);

        UnitStorageConstructor.clearBeforeInnerInitialization(unit);

        Assertions.assertTrue(game.getUnitInitializer().initialize(unit, ScrUnitIds.ZERG_LARVA, LARVA_POSITION, LARVA_POSITION, PLAYER,
                                                                   INSTRUCTION_LIMIT));

        unitPool.commitInitializedUnit(unit);
        unit.setStatus(unit.getStatus() | UnitStatusFlags.COMPLETED);
        unit.setPrimaryOrder(ScrOrderIds.ZERG_UNIT_MORPH);
        unit.setPrimaryOrderState(START_STATE);
        unit.setBuildQueueIndex(ENERGY_QUEUE_INDEX);
        unit.setEnergy(ScrUnitIds.ZERG_HYDRALISK);

        // Without an Overlord the Hydralisk fails the supply test: the queue is reset and the Larva returns to idle.
        game.getUnitMorphing().update(unit, INSTRUCTION_LIMIT);

        Assertions.assertEquals(LARVA_TYPE, unit.getUnitId());
        Assertions.assertEquals(gameData.getUnitTypes().getReturnToIdleOrder(ScrUnitIds.ZERG_LARVA), unit.getPrimaryOrder());
        Assertions.assertEquals(0, unit.getBuildQueueIndex());
        Assertions.assertEquals(HYDRALISK_TYPE, unit.getEnergy());
    }

    @Test
    void update_should_take_the_egg_build_time_of_the_queue_index_word_at_queue_index_six() {
        Game game = createMorphGame(MORPH_UNIT_CAPACITY);
        UnitPool unitPool = game.getUnitSystem().getUnitPool();
        UnitSpatialIndex spatialIndex = game.getUnitSystem().getUnitSpatialIndex();
        UnitAccounting accounting = game.getPlayerTable().getUnitAccounting();
        PathPool pathPool = game.getPathSystem().getPathPool();

        Assertions.assertNotNull(unitPool);
        Assertions.assertNotNull(spatialIndex);
        Assertions.assertNotNull(accounting);
        Assertions.assertNotNull(pathPool);
        Assertions.assertNotNull(gameData);

        Unit unit = unitPool.getUnit(1);

        UnitStorageConstructor.clearBeforeInnerInitialization(unit);

        Assertions.assertTrue(game.getUnitInitializer().initialize(unit, ScrUnitIds.ZERG_LARVA, LARVA_POSITION, LARVA_POSITION, PLAYER,
                                                                   INSTRUCTION_LIMIT));

        unitPool.commitInitializedUnit(unit);
        unitPool.setGeneration(1, CLEARED_GENERATION);
        unit.setStatus(unit.getStatus() | UnitStatusFlags.COMPLETED);
        unit.setPrimaryOrder(ScrOrderIds.ZERG_UNIT_MORPH);
        unit.setPrimaryOrderState(START_STATE);
        unit.setBuildQueueIndex(INDEX_WORD_QUEUE_INDEX);
        unit.setPathIndex(pathPool.allocate());

        spatialIndex.insert(unit, gameData.getUnitTypes());
        accounting.update(unit, gameData.getUnitTypes(), 1, false);
        accounting.updateCompleted(unit, gameData.getUnitTypes(), 1, false, null);

        int queuedType = INDEX_WORD_QUEUE_INDEX | CLEARED_GENERATION << Byte.SIZE;

        // The queued type 6, the Tank Mode turret, needs no supply: the Larva becomes an Egg without an Overlord.
        game.getUnitMorphing().update(unit, INSTRUCTION_LIMIT);

        Assertions.assertEquals(EGG_TYPE, unit.getUnitId());
        Assertions.assertEquals(COUNTDOWN_STATE, unit.getPrimaryOrderState());
        Assertions.assertEquals(INDEX_WORD_QUEUE_INDEX, unit.getBuildQueueIndex());
        Assertions.assertEquals(gameData.getUnitTypes().getBuildTime(queuedType), unit.getRemainingBuildTime());
    }

    @Test
    void update_should_advance_countdown_before_reading_queue_or_checking_unit_type() {
        Game game = Game.create(gameData, new GameMap(SMALL_MAP_DIMENSION, SMALL_MAP_DIMENSION, 0, new int[SMALL_MAP_TILE_COUNT]), new LcgRandom(0));
        Unit unit = UnitTestUtils.createUnit(1);

        unit.setUnitId(ScrUnitIds.HERO_DEVOURING_ONE);
        unit.setPrimaryOrderState(COUNTDOWN_STATE);
        unit.setBuildQueueIndex(MAXIMUM_BYTE);
        unit.setRemainingBuildTime(SHORT_REMAINING_BUILD_TIME);

        game.getUnitMorphing().update(unit, INSTRUCTION_LIMIT);

        Assertions.assertEquals(SHORT_REMAINING_BUILD_TIME - 1, unit.getRemainingBuildTime());
        Assertions.assertEquals(COUNTDOWN_STATE, unit.getPrimaryOrderState());
        Assertions.assertEquals(DEVOURING_ONE_TYPE, unit.getUnitId());
    }

    @ParameterizedTest
    @ValueSource(ints = {START_STATE, HATCHING_STATE, UNKNOWN_STATE, MAXIMUM_BYTE})
    void update_should_ignore_unsupported_source_in_unstarted_unsignaled_and_other_phases(int phase) {
        Game game = Game.create(gameData, new GameMap(SMALL_MAP_DIMENSION, SMALL_MAP_DIMENSION, 0, new int[SMALL_MAP_TILE_COUNT]), new LcgRandom(0));
        Unit unit = UnitTestUtils.createUnit(1);

        unit.setUnitId(ScrUnitIds.HERO_DEVOURING_ONE);
        unit.setPrimaryOrderState(phase);
        unit.setBuildQueueIndex(MAXIMUM_BYTE);

        game.getUnitMorphing().update(unit, INSTRUCTION_LIMIT);

        Assertions.assertEquals(phase, unit.getPrimaryOrderState());
        Assertions.assertEquals(MAXIMUM_BYTE, unit.getBuildQueueIndex());
        Assertions.assertEquals(DEVOURING_ONE_TYPE, unit.getUnitId());
    }

    @Test
    void update_should_ignore_unknown_source_type_before_reading_queue() {
        Game game = Game.create(gameData, new GameMap(SMALL_MAP_DIMENSION, SMALL_MAP_DIMENSION, 0, new int[SMALL_MAP_TILE_COUNT]), new LcgRandom(0));
        Unit unit = UnitTestUtils.createUnit(1);

        unit.setUnitId(UNKNOWN_UNIT_TYPE);
        unit.setBuildQueueIndex(MAXIMUM_BYTE);

        game.getUnitMorphing().update(unit, INSTRUCTION_LIMIT);

        Assertions.assertEquals(UNKNOWN_UNIT_TYPE, unit.getUnitId());
        Assertions.assertEquals(MAXIMUM_BYTE, unit.getBuildQueueIndex());
    }

    @Test
    void write_button_set_should_skip_only_disabled_non_buildings_with_non_empty_set() throws IOException {
        try (GameData data = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            DatTable units = data.getUnits();
            ScrUnitTypeView types = UnitsDat.createTypeTable(units);
            Unit marine = UnitTestUtils.createUnit(MARINE_POOL_INDEX);

            marine.setUnitId(ScrUnitIds.TERRAN_MARINE);

            UnitMorphing.writeButtonSet(types, marine, EGG_BUTTON_SET);

            Assertions.assertEquals(EGG_BUTTON_SET, marine.getCurrentButtonSet());

            marine.setStatus(marine.getStatus() | UnitStatusFlags.DISABLED);
            UnitMorphing.writeButtonSet(types, marine, LARVA_BUTTON_SET);

            Assertions.assertEquals(EGG_BUTTON_SET, marine.getCurrentButtonSet(), "A disabled unit keeps its set.");

            UnitMorphing.writeButtonSet(types, marine, ButtonSetIds.EMPTY);

            Assertions.assertEquals(EMPTY_BUTTON_SET, marine.getCurrentButtonSet(), "The empty set is always written.");

            Unit hatchery = UnitTestUtils.createUnit(HATCHERY_POOL_INDEX);

            hatchery.setUnitId(ScrUnitIds.ZERG_HATCHERY);
            hatchery.setStatus(UnitStatusFlags.DISABLED);

            UnitMorphing.writeButtonSet(types, hatchery, ButtonSetIds.CANCEL_MORPH);

            Assertions.assertEquals(CANCEL_MORPH_BUTTON_SET, hatchery.getCurrentButtonSet(), "A disabled building still takes the set.");
        }
    }
}
