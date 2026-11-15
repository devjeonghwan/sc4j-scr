package org.fogofwarlabs.sc4j.scr.game.resource;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.frame.GameFrameScenario;
import org.fogofwarlabs.sc4j.scr.game.order.OrderPool;
import org.fogofwarlabs.sc4j.scr.game.order.UnitSelection;
import org.fogofwarlabs.sc4j.scr.game.path.PathPool;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.IscriptExecutor;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.terrain.MapTileFlags;
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

import java.io.IOException;

/**
 * Checks the worker harvesting orders: mineral searches and waits, gathering, gas claims, cargo return and the harvest collision reset.
 */
@RequiresInstallation
final class UnitHarvestingTest {
    private static final int INSTRUCTION_LIMIT = 512;
    private static final int GATHER_INSTRUCTION_LIMIT = 10000;
    private static final int SMALL_MAP_DIMENSION = 8;
    private static final int SMALL_MAP_TILE_COUNT = SMALL_MAP_DIMENSION * SMALL_MAP_DIMENSION;
    private static final int MAP_DIMENSION = 32;
    private static final int MAP_TILE_COUNT = MAP_DIMENSION * MAP_DIMENSION;
    private static final int SMALL_ORDER_CAPACITY = 8;
    private static final int ORDER_CAPACITY = 16;
    private static final int SMALL_IMAGE_CAPACITY = 8;
    private static final int IMAGE_CAPACITY = 16;
    private static final int LARGE_IMAGE_CAPACITY = 32;
    private static final int GAS_ENTRY_UNIT_CAPACITY = 2;
    private static final int RESOURCE_ELEVATION = 11;

    private static final int WORKER_INDEX = 1;
    private static final int GATHERED_FIELD_INDEX = 2;
    private static final int FREE_FIELD_INDEX = 3;
    private static final int WORKER_POSITION = 128;
    private static final int GATHERED_FIELD_HORIZONTAL_POSITION = 64;
    /**
     * Free field positions within and past {@code 256} pixels of the gathered field.
     */
    private static final int NEAR_FREE_FIELD_HORIZONTAL_POSITION = 328;
    private static final int FAR_FREE_FIELD_HORIZONTAL_POSITION = 448;
    private static final int LEFT_EDGE_WORKER_HORIZONTAL_POSITION = 160;
    private static final int LEFT_EDGE_FIELD_HORIZONTAL_POSITION = 32;
    /**
     * The occupied tiles around the left edge field, each a column and a row.
     */
    private static final int @NotNull [] @NotNull [] LEFT_EDGE_OCCUPIED_TILES = {{1, 2}, {2, 2}, {0, 3}, {2, 3}, {0, 5}, {1, 5}, {2, 5}};
    private static final int UNCOMPLETED_FIELD_HORIZONTAL_POSITION = 192;

    private static final int GAS_HARVEST_STATE = 5;
    private static final int GAS_HARVEST_COOLDOWN = 37;
    private static final int BYTE_MASK = 0xFF;
    private static final int INTERRUPTION_COUNTDOWN = 4;
    private static final int RESUMED_ORDER_COUNTDOWN = 8;
    private static final int GATHER_SPRITE_POSITION = 100;
    private static final int @NotNull [] MINERAL_AMOUNTS = {0, 3, 8};
    private static final int @NotNull [] GAS_AMOUNTS = {0, 7, 8, 9};
    private static final int RESOURCES_PER_TRIP = 8;
    private static final int DEPLETED_GAS_PER_TRIP = 2;
    private static final int SAVED_TARGET_HORIZONTAL_POSITION = 123;
    private static final int SAVED_TARGET_VERTICAL_POSITION = 124;

    private static final int FIELD_POSITION = 320;
    private static final int WAITING_WORKER_HORIZONTAL_POSITION = 288;
    private static final int RETURN_VERTICAL_POSITION = 240;
    private static final int SAVED_FIELD_HORIZONTAL_POSITION = 400;
    private static final int GATHERED_MINERALS = 50;
    private static final int LARVA_SIDE_COUNT = 10;
    private static final int DECREMENTED_LARVA_SIDE_COUNT = 9;
    private static final int UPPER_LARVA_SIDE_COUNT = 34;

    private static final int TARGET_GAP = 11;
    private static final int GAP_RANGE = 10;
    private static final int SHORT_RANGE = 9;
    private static final int DESTINATION_COUNTDOWN = 3;
    private static final int MOVING_WORKER_POSITION = 64;
    private static final int MOVE_TO_MINERALS_COUNTDOWN = 15;
    private static final int QUEUED_ORDER_COUNT = 2;

    private static final int NOTHING_ORDER = 23;
    private static final int WAIT_FOR_GAS_ORDER = 82;
    private static final int HARVEST_GAS_ORDER = 83;
    private static final int MOVE_TO_MINERALS_ORDER = 85;
    private static final int WAIT_FOR_MINERALS_ORDER = 86;
    private static final int MINING_MINERALS_ORDER = 87;
    private static final int GATHERING_INTERRUPTED_ORDER = 88;
    private static final int GATHER_WAIT_INTERRUPTED_ORDER = 89;
    private static final int RESET_HARVEST_COLLISION_ORDER = 151;
    private static final int GATHERING_STATUS = 0x800000;
    private static final int GAS_CARRYING_FLAG = 1;
    private static final int MINERALS_CARRYING_FLAG = 2;
    /**
     * The full-load image types of the carried gas: the Terran gas tank, the Zerg gas sac and the Protoss gas orb; the depleted-load image
     * follows each of them.
     */
    private static final int TERRAN_GAS_TANK_IMAGE = 403;
    private static final int ZERG_GAS_SAC_IMAGE = 401;
    private static final int PROTOSS_GAS_ORB_IMAGE = 399;

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

    private static @NotNull Game createMineralSearchGame(int @NotNull [] flags) throws IOException {
        Assertions.assertNotNull(gameData);

        return MineralSearchScenario.createGame(gameData, flags);
    }

    private static @NotNull Unit placeMineralSearchUnit(@NotNull Game game, int poolIndex, int type, int owner, int positionX, int positionY) {
        Unit unit = MineralSearchScenario.placeUnit(game, poolIndex, type, owner, positionX, positionY);

        if (type == ScrUnitIds.PROTOSS_PROBE) {
            PathPool pathPool = game.getPathSystem().getPathPool();
            Sprite sprite = unit.getSpriteOrNull();

            Assertions.assertNotNull(pathPool);
            Assertions.assertNotNull(sprite);

            SpriteImage image = SpriteScenario.createImage(0, 0, 0);
            int pathIndex = pathPool.allocate();

            image.setAnimationType(IscriptExecutor.ANIMATION_BUILT);
            sprite.setMainImage(image);
            pathPool.setFlags(pathIndex, 0);
            unit.setPathIndex(pathIndex);
            unit.setPrimaryOrder(ScrOrderIds.MOVE_TO_MINERALS);
            unit.setStatus(UnitStatusFlags.COMPLETED);
        }

        return unit;
    }

    private static @NotNull Game createGatheredFieldGame(int freePositionX) throws IOException {
        int[] flags = MineralSearchScenario.createWalkableFlags();

        Game game = createMineralSearchGame(flags);
        Unit worker = placeMineralSearchUnit(game, WORKER_INDEX, ScrUnitIds.PROTOSS_PROBE, 0, WORKER_POSITION, WORKER_POSITION);
        Unit gathered = placeMineralSearchUnit(game, GATHERED_FIELD_INDEX, ScrUnitIds.RESOURCE_MINERAL_FIELD, PlayerTable.NEUTRAL_PLAYER_ID,
                                               GATHERED_FIELD_HORIZONTAL_POSITION, WORKER_POSITION);
        Unit free = placeMineralSearchUnit(game, FREE_FIELD_INDEX, ScrUnitIds.RESOURCE_MINERAL_FIELD, PlayerTable.NEUTRAL_PLAYER_ID,
                                           freePositionX, WORKER_POSITION);
        UnitAccounting accounting = game.getPlayerTable().getUnitAccounting();

        Assertions.assertNotNull(gameData);
        Assertions.assertNotNull(accounting);

        accounting.updateCompleted(gathered, gameData.getUnitTypes(), 1, false, null);
        accounting.updateCompleted(free, gameData.getUnitTypes(), 1, false, null);
        gathered.setResourceBeingGathered(true);
        worker.setStatus(UnitStatusFlags.COMPLETED | UnitStatusFlags.GATHERING);
        worker.setPrimaryOrderState(1);
        worker.setOrderTargetUnit(gathered);
        worker.setMovementDestinationX(WORKER_POSITION);
        worker.setMovementDestinationY(WORKER_POSITION);

        return game;
    }


    @Test
    void gas_entry_keeps_claim_and_hidden_death_releases_it_before_final_recycling() throws IOException {
        Game game = Game.create(gameData, new GameMap(SMALL_MAP_DIMENSION, SMALL_MAP_DIMENSION, 0, new int[SMALL_MAP_TILE_COUNT]), new LcgRandom(0));

        game.getUnitSystem().initializeUnitPool(GAS_ENTRY_UNIT_CAPACITY);
        game.initializeOrderPool(SMALL_ORDER_CAPACITY);
        game.initializeUnitSpatialIndex();
        game.getPathSystem().setPathPool(new PathPool());

        SightCoverage sight = new SightCoverage(SMALL_MAP_DIMENSION);
        int[] sharedVisionMasks = new int[PlayerTable.PLAYER_SLOTS];

        for (int index = 0; index < sharedVisionMasks.length; index++) {
            sharedVisionMasks[index] = 1 << index;
        }

        sight.setSharedVisionMasks(sharedVisionMasks);
        game.setSightCoverage(sight);
        game.getLocalVision().setComparisonMask(1);

        Assertions.assertNotNull(gameData);

        game.setFrame(GameFrameScenario.createFrame(gameData));

        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        Unit worker = pool.getUnit(UnitPoolScenario.allocate(pool));

        worker.setUnitId(ScrUnitIds.TERRAN_SCV);
        worker.setStatus(UnitStatusFlags.COMPLETED | UnitStatusFlags.GATHERING);
        worker.setPrimaryOrder(ScrOrderIds.HARVEST_GAS);
        worker.setPositionX(WORKER_POSITION);
        worker.setPositionY(WORKER_POSITION);

        Sprite sprite = SpriteScenario.createSprite(game, 0, WORKER_POSITION, WORKER_POSITION, 0);

        sprite.setVisibilityFlags(Sprite.VISIBLE_TO_ALL_PLAYERS);
        worker.setSprite(sprite);
        pool.prependToOwnerList(worker, 0);
        pool.insertCreatedUnit(worker);
        pool.transferCreatedUnitToActive(worker);

        UnitSpatialIndex placementIndex = game.getUnitSystem().getUnitSpatialIndex();

        Assertions.assertNotNull(placementIndex);

        placementIndex.insert(worker, gameData.getUnitTypes());
        worker.setPathIndex(game.getPathSystem().getPathPool().allocate());

        Unit refinery = UnitTestUtils.createUnit(3);

        refinery.setUnitId(ScrUnitIds.TERRAN_REFINERY);
        refinery.setStatus(UnitStatusFlags.COMPLETED | UnitStatusFlags.GROUNDED_BUILDING);
        refinery.setResourceBeingGathered(true);
        worker.setGatherTarget(refinery);
        worker.setGatheringResource(true);
        worker.setOrderTargetUnit(refinery);

        game.getUnitHarvesting().harvestGas(worker, INSTRUCTION_LIMIT);

        Assertions.assertEquals(GAS_HARVEST_STATE, worker.getPrimaryOrderState());
        Assertions.assertEquals(GAS_HARVEST_COOLDOWN, worker.getCooldownTimers() & BYTE_MASK);
        Assertions.assertEquals(worker.getIndex(), pool.getFirstCreatedIndex(false));
        Assertions.assertEquals(0, pool.getFirstCreatedIndex(true));
        Assertions.assertEquals(1, pool.getAllocatedCount());
        Assertions.assertEquals(worker.getIndex(), pool.getFirstOwnerIndex(0));
        Assertions.assertEquals(-1, worker.getMinimumHorizontalEdgeIndex());
        Assertions.assertEquals(-1, worker.getPathIndex());
        Assertions.assertEquals(0, sprite.getVisibilityFlags());
        Assertions.assertTrue((sprite.getFlags() & Sprite.FLAG_HIDDEN) != 0);
        Assertions.assertTrue(worker.isGatheringResource());
        Assertions.assertSame(refinery, worker.getGatherTarget());
        Assertions.assertTrue(refinery.isResourceBeingGathered());

        game.setSelection(new UnitSelection(pool, false, 0, 0));
        game.getUnitSystem().setUnitCreationContext(new UnitCreationContext(false, false, false, false, true, 0));

        UnitAccounting accounting = PlayerProgressTestUtils.createUnitAccounting();

        game.getPlayerTable().setUnitAccounting(accounting);
        accounting.update(worker, gameData.getUnitTypes(), 1, false);
        accounting.updateCompleted(worker, gameData.getUnitTypes(), 1, false, null);

        SpriteImage main = SpriteScenario.createImage(game, 0, 0, 0);

        main.setAnimationType(IscriptExecutor.ANIMATION_BUILT);
        sprite.insertHeadImage(main);
        sprite.setMainImage(main);

        game.getUnitLifecycle().kill(worker, INSTRUCTION_LIMIT);

        Assertions.assertEquals(0, worker.getPrimaryOrder());
        Assertions.assertEquals(1, pool.getAllocatedCount());

        game.getUnitOrders().updatePrimary(worker, PlayerTypes.HUMAN, INSTRUCTION_LIMIT);

        Assertions.assertFalse(refinery.isResourceBeingGathered());
        Assertions.assertNull(worker.getGatherTarget());
        Assertions.assertNull(worker.getSpriteOrNull());
        Assertions.assertEquals(0, pool.getFirstCreatedIndex(false));
        Assertions.assertEquals(worker.getIndex(), pool.getFirstDeadIndex());
        Assertions.assertEquals(1, pool.getAllocatedCount());
        Assertions.assertEquals(0, accounting.getCompletedTypeCount(ScrUnitIds.TERRAN_SCV, 0));

        game.getUnitLifecycle().updateDead(worker, false, INSTRUCTION_LIMIT);

        Assertions.assertEquals(0, pool.getAllocatedCount());
        Assertions.assertEquals(0, pool.getFirstDeadIndex());
        Assertions.assertEquals(0, pool.getFirstOwnerIndex(0));
    }

    @Test
    void update_primary_should_run_interruption_before_countdown_and_share_hidden_gas_countdown() {
        Game game = Game.create(gameData, new GameMap(SMALL_MAP_DIMENSION, SMALL_MAP_DIMENSION, 0, new int[SMALL_MAP_TILE_COUNT]), new LcgRandom(0));

        game.initializeOrderPool(SMALL_ORDER_CAPACITY);

        Unit worker = UnitTestUtils.createUnit(1);

        worker.setUnitId(ScrUnitIds.PROTOSS_PROBE);
        worker.setStatus(UnitStatusFlags.COMPLETED | UnitStatusFlags.GATHERING | UnitStatusFlags.ORDER_NOT_INTERRUPTIBLE);
        worker.setPrimaryOrder(ScrOrderIds.GATHERING_INTERRUPTED);
        worker.setPrimaryOrderCountdown(INTERRUPTION_COUNTDOWN);

        Sprite sprite = SpriteScenario.createSprite(0, WORKER_POSITION, WORKER_POSITION, 0);
        SpriteImage image = SpriteScenario.createImage(0, 0, 0);

        image.setAnimationType(IscriptExecutor.ANIMATION_BUILT);
        sprite.setMainImage(image);
        worker.setSprite(sprite);

        Unit resource = UnitTestUtils.createUnit(2);

        resource.setResourceBeingGathered(true);
        worker.setGatherTarget(resource);
        worker.setGatheringResource(true);
        game.getOrders().queueFront(worker, ScrOrderIds.NOTHING, null, 0, 0, ScrUnitIds.NONE);

        game.getUnitOrders().updatePrimary(worker, PlayerTypes.HUMAN, INSTRUCTION_LIMIT);

        Assertions.assertEquals(NOTHING_ORDER, worker.getPrimaryOrder());
        Assertions.assertEquals(RESUMED_ORDER_COUNTDOWN, worker.getPrimaryOrderCountdown());
        Assertions.assertFalse(resource.isResourceBeingGathered());
        Assertions.assertFalse(worker.isGatheringResource());

        sprite.setFlags(sprite.getFlags() | Sprite.FLAG_HIDDEN);
        worker.setPrimaryOrder(ScrOrderIds.HARVEST_GAS);
        worker.setPrimaryOrderState(GAS_HARVEST_STATE);
        worker.setPrimaryOrderCountdown(INTERRUPTION_COUNTDOWN);
        worker.setCooldownTimers(1);

        game.getUnitOrders().updatePrimary(worker, PlayerTypes.HUMAN, INSTRUCTION_LIMIT);

        Assertions.assertEquals(INTERRUPTION_COUNTDOWN - 1, worker.getPrimaryOrderCountdown());
        Assertions.assertEquals(GAS_HARVEST_STATE, worker.getPrimaryOrderState());
    }

    @Test
    void gather_resources_should_carry_remaining_minerals_and_request_death_of_exhausted_field() {
        for (int available : MINERAL_AMOUNTS) {
            Game game = Game.create(gameData, new GameMap(SMALL_MAP_DIMENSION, SMALL_MAP_DIMENSION, 0, new int[SMALL_MAP_TILE_COUNT]),
                                    new LcgRandom(1));

            game.getSpriteSystem().initializeImagePool(LARGE_IMAGE_CAPACITY);
            game.initializeOrderPool(ORDER_CAPACITY);

            Assertions.assertNotNull(gameData);

            int workerSpriteType = gameData.getFlingy().get("Sprite", gameData.getUnits().get("Graphics", ScrUnitIds.PROTOSS_PROBE));
            Sprite workerSprite = SpriteScenario.createSprite(game, workerSpriteType, GATHER_SPRITE_POSITION, GATHER_SPRITE_POSITION, 0);
            SpriteImage workerMain = SpriteScenario.createImage(game, gameData.getSprites().get("Image", workerSpriteType), 0, 0);

            workerSprite.insertHeadImage(workerMain);
            workerSprite.setMainImage(workerMain);

            Unit worker = UnitTestUtils.createUnit(1);

            worker.setUnitId(ScrUnitIds.PROTOSS_PROBE);
            worker.setSprite(workerSprite);

            int resourceSpriteType = gameData.getFlingy().get("Sprite", gameData.getUnits().get("Graphics", ScrUnitIds.RESOURCE_MINERAL_FIELD));
            Sprite resourceSprite = SpriteScenario.createSprite(game, resourceSpriteType, WORKER_POSITION, WORKER_POSITION, RESOURCE_ELEVATION);
            SpriteImage resourceMain = SpriteScenario.createImage(game, gameData.getSprites().get("Image", resourceSpriteType), 0, 0);

            resourceMain.setAnimationType(IscriptExecutor.ANIMATION_BUILT);
            resourceSprite.insertHeadImage(resourceMain);
            resourceSprite.setMainImage(resourceMain);

            Unit mineral = UnitTestUtils.createUnit(2);

            mineral.setUnitId(ScrUnitIds.RESOURCE_MINERAL_FIELD);
            mineral.setSprite(resourceSprite);
            mineral.setPrimaryOrder(ScrOrderIds.NOTHING);
            mineral.setResources(available);
            game.getOrders().queueFront(mineral, ScrOrderIds.NOTHING, null, 0, 0, ScrUnitIds.NONE);

            game.getUnitHarvesting().gatherResources(worker, mineral, GATHER_INSTRUCTION_LIMIT);

            Assertions.assertEquals(0, mineral.getPrimaryOrder());
            Assertions.assertNotNull(mineral.getSpriteOrNull());
            Assertions.assertNull(mineral.getFirstOrder());
            Assertions.assertEquals(available, worker.getCarriedResourceAmount());
            Assertions.assertEquals(available == 0 ? 0 : MINERALS_CARRYING_FLAG, worker.getCarryingFlags());
            Assertions.assertEquals(available < RESOURCES_PER_TRIP ? available : 0, mineral.getResources());
        }
    }

    @Test
    void remove_target_references_should_clear_targets_and_queued_orders_but_keep_saved_position() {
        Game game = Game.create(gameData, new GameMap(SMALL_MAP_DIMENSION, SMALL_MAP_DIMENSION, 0, new int[SMALL_MAP_TILE_COUNT]), new LcgRandom(0));

        game.initializeOrderPool(ORDER_CAPACITY);

        Unit worker = UnitTestUtils.createUnit(1);

        worker.setUnitId(ScrUnitIds.PROTOSS_PROBE);

        Unit mineral = UnitTestUtils.createUnit(2);

        mineral.setUnitId(ScrUnitIds.RESOURCE_MINERAL_FIELD);
        mineral.setSprite(SpriteScenario.createSprite(0, WORKER_POSITION, WORKER_POSITION, RESOURCE_ELEVATION));
        worker.setTargetResource(mineral, SAVED_TARGET_HORIZONTAL_POSITION, SAVED_TARGET_VERTICAL_POSITION);
        worker.setGatherTarget(mineral);
        worker.setGatheringResource(true);
        worker.setMovementTargetUnit(mineral);
        worker.setOrderTargetUnit(mineral);
        worker.setAutomaticTargetUnit(mineral);

        game.getOrders().queueFront(worker, ScrOrderIds.RESET_HARVEST_COLLISION, null, 0, 0, ScrUnitIds.NONE);
        game.getOrders().queueFront(worker, ScrOrderIds.MOVE_TO_MINERALS, mineral, WORKER_POSITION, WORKER_POSITION,
                                    ScrUnitIds.NONE);

        game.getUnitLifecycle().removeTargetReferences(worker, mineral);

        Assertions.assertNull(worker.getTargetResourceUnit());
        Assertions.assertEquals(SAVED_TARGET_HORIZONTAL_POSITION, worker.getTargetResourceX());
        Assertions.assertEquals(SAVED_TARGET_VERTICAL_POSITION, worker.getTargetResourceY());
        Assertions.assertNull(worker.getGatherTarget());
        Assertions.assertFalse(worker.isGatheringResource());
        Assertions.assertNull(worker.getMovementTargetUnit());
        Assertions.assertNull(worker.getOrderTargetUnit());
        Assertions.assertFalse(worker.hasAutomaticTarget());

        OrderPool.Order workerFirstOrder = worker.getFirstOrder();

        Assertions.assertNotNull(workerFirstOrder);
        Assertions.assertEquals(RESET_HARVEST_COLLISION_ORDER, workerFirstOrder.getOrderId());
        Assertions.assertSame(worker.getFirstOrder(), worker.getLastOrder());

        OrderPool orderPool = game.getOrderPool();

        Assertions.assertNotNull(orderPool);
        Assertions.assertEquals(1, orderPool.getAllocatedCount());
    }

    @Test
    void gather_resources_should_use_stored_gas_amount_and_depleted_rate() {
        for (int refineryType : new int[]{ScrUnitIds.TERRAN_REFINERY, ScrUnitIds.ZERG_EXTRACTOR, ScrUnitIds.PROTOSS_ASSIMILATOR}) {
            for (int available : GAS_AMOUNTS) {
                Game game = Game.create(gameData, new GameMap(SMALL_MAP_DIMENSION, SMALL_MAP_DIMENSION, 0, new int[SMALL_MAP_TILE_COUNT]),
                                        new LcgRandom(1));

                game.getSpriteSystem().initializeImagePool(IMAGE_CAPACITY);

                int workerType = refineryType == ScrUnitIds.TERRAN_REFINERY
                                 ? ScrUnitIds.TERRAN_SCV
                                 : refineryType == ScrUnitIds.ZERG_EXTRACTOR ? ScrUnitIds.ZERG_DRONE : ScrUnitIds.PROTOSS_PROBE;

                Assertions.assertNotNull(gameData);

                int spriteType = gameData.getFlingy().get("Sprite", gameData.getUnits().get("Graphics", workerType));
                int imageType = gameData.getSprites().get("Image", spriteType);
                Sprite sprite = SpriteScenario.createSprite(game, spriteType, GATHER_SPRITE_POSITION, GATHER_SPRITE_POSITION, 0);
                SpriteImage main = SpriteScenario.createImage(game, imageType, 0, 0);

                sprite.insertHeadImage(main);
                sprite.setMainImage(main);

                Unit worker = UnitTestUtils.createUnit(1);

                worker.setUnitId(workerType);
                worker.setSprite(sprite);

                Unit refinery = UnitTestUtils.createUnit(2);

                refinery.setUnitId(refineryType);
                refinery.setResources(available);

                game.getUnitHarvesting().gatherResources(worker, refinery, GATHER_INSTRUCTION_LIMIT);

                Assertions.assertEquals(available < RESOURCES_PER_TRIP ? DEPLETED_GAS_PER_TRIP : RESOURCES_PER_TRIP,
                                        worker.getCarriedResourceAmount());
                Assertions.assertEquals(Math.max(0, available - RESOURCES_PER_TRIP), refinery.getResources());
                Assertions.assertEquals(GAS_CARRYING_FLAG, worker.getCarryingFlags());

                int carryType = (refineryType == ScrUnitIds.TERRAN_REFINERY
                                 ? TERRAN_GAS_TANK_IMAGE
                                 : refineryType == ScrUnitIds.ZERG_EXTRACTOR ? ZERG_GAS_SAC_IMAGE : PROTOSS_GAS_ORB_IMAGE) +
                                (available < RESOURCES_PER_TRIP ? 1 : 0);

                Assertions.assertTrue(sprite.getImages().stream().anyMatch(image -> image.getImageTypeId() == carryType));
            }
        }
    }

    @Test
    void gas_claim_uses_harvest_order_and_wait_cancellation_unlinks_both_ends() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]), new LcgRandom(0));

        game.initializeOrderPool(ORDER_CAPACITY);

        Unit refinery = UnitTestUtils.createUnit(1);

        refinery.setUnitId(ScrUnitIds.TERRAN_REFINERY);
        refinery.setStatus(UnitStatusFlags.COMPLETED | UnitStatusFlags.GROUNDED_BUILDING);
        refinery.setSprite(SpriteScenario.createSprite(0, FIELD_POSITION, FIELD_POSITION, RESOURCE_ELEVATION));

        Unit active = UnitTestUtils.createUnit(2);
        Unit waiting = UnitTestUtils.createUnit(3);

        for (Unit worker : new Unit[]{active, waiting}) {
            worker.setUnitId(ScrUnitIds.TERRAN_SCV);
            worker.setStatus(UnitStatusFlags.COMPLETED | UnitStatusFlags.GATHERING);
            worker.setPrimaryOrder(ScrOrderIds.WAIT_FOR_GAS);
            worker.setOrderTargetUnit(refinery);

            Sprite sprite = SpriteScenario.createSprite(0, WAITING_WORKER_HORIZONTAL_POSITION, FIELD_POSITION, 0);
            SpriteImage image = SpriteScenario.createImage(0, 0, 0);

            image.setAnimationType(IscriptExecutor.ANIMATION_BUILT);
            sprite.setMainImage(image);
            worker.setSprite(sprite);

            game.getOrders().queueFront(worker, ScrOrderIds.RESET_HARVEST_COLLISION, null, 0, 0, ScrUnitIds.NONE);

            game.getUnitHarvesting().waitForGas(worker, INSTRUCTION_LIMIT);
        }

        Assertions.assertEquals(HARVEST_GAS_ORDER, active.getPrimaryOrder());
        Assertions.assertSame(refinery, active.getGatherTarget());
        Assertions.assertTrue(active.isGatheringResource());

        OrderPool.Order activeOrder = active.getFirstOrder();

        Assertions.assertNotNull(activeOrder);
        Assertions.assertEquals(RESET_HARVEST_COLLISION_ORDER, activeOrder.getOrderId());
        Assertions.assertSame(active.getFirstOrder(), active.getLastOrder());
        Assertions.assertEquals(WAIT_FOR_GAS_ORDER, waiting.getPrimaryOrder());
        Assertions.assertSame(waiting, refinery.getFirstGatheringWorker());
        Assertions.assertSame(waiting, refinery.getLastGatheringWorker());

        OrderPool.Order waitingOrder = waiting.getFirstOrder();

        Assertions.assertNotNull(waitingOrder);
        Assertions.assertEquals(GATHER_WAIT_INTERRUPTED_ORDER, waitingOrder.getOrderId());

        game.getOrders().activateNext(waiting, INSTRUCTION_LIMIT);

        Assertions.assertEquals(GATHER_WAIT_INTERRUPTED_ORDER, waiting.getPrimaryOrder());

        game.getUnitHarvesting().interruptGathering(waiting, INSTRUCTION_LIMIT);

        Assertions.assertNull(refinery.getFirstGatheringWorker());
        Assertions.assertNull(refinery.getLastGatheringWorker());
        Assertions.assertNull(waiting.getGatherTarget());
        Assertions.assertNull(waiting.getNextGatheringWorker());
        Assertions.assertNull(waiting.getPreviousGatheringWorker());
        Assertions.assertTrue(refinery.isResourceBeingGathered());
        Assertions.assertSame(refinery, active.getGatherTarget());
    }

    @Test
    void is_active_depot_should_accept_incomplete_hatchery_only_during_building_morph() {
        Game game = Game.create(gameData, new GameMap(SMALL_MAP_DIMENSION, SMALL_MAP_DIMENSION, 0, new int[SMALL_MAP_TILE_COUNT]), new LcgRandom(0));
        Unit hatchery = UnitTestUtils.createUnit(1);

        hatchery.setUnitId(ScrUnitIds.ZERG_HATCHERY);
        hatchery.setStatus(UnitStatusFlags.GROUNDED_BUILDING);
        hatchery.setBuildQueueIndex(1);
        hatchery.setSecondBuildQueueType(ScrUnitIds.NONE);

        Assertions.assertFalse(game.getUnitResourceSearch().isActiveDepot(hatchery));

        hatchery.setSecondBuildQueueType(ScrUnitIds.ZERG_LAIR);

        Assertions.assertTrue(game.getUnitResourceSearch().isActiveDepot(hatchery));

        hatchery.setSecondBuildQueueType(ScrUnitIds.ZERG_DRONE);

        Assertions.assertFalse(game.getUnitResourceSearch().isActiveDepot(hatchery));

        hatchery.setStatus(UnitStatusFlags.COMPLETED | UnitStatusFlags.GROUNDED_BUILDING);

        Assertions.assertTrue(game.getUnitResourceSearch().isActiveDepot(hatchery));
    }

    @Test
    void move_to_minerals_should_test_the_ring_of_a_left_edge_field_from_its_first_column() throws IOException {
        int[] flags = MineralSearchScenario.createWalkableFlags();

        for (int[] tile : LEFT_EDGE_OCCUPIED_TILES) {
            flags[tile[1] * MineralSearchScenario.MAP_SIZE + tile[0]] |= MapTileFlags.OCCUPIED;
        }

        Game game = createMineralSearchGame(flags);
        Unit worker = placeMineralSearchUnit(game, WORKER_INDEX, ScrUnitIds.PROTOSS_PROBE, 0, LEFT_EDGE_WORKER_HORIZONTAL_POSITION,
                                             WORKER_POSITION);
        Unit mineral = placeMineralSearchUnit(game, GATHERED_FIELD_INDEX, ScrUnitIds.RESOURCE_MINERAL_FIELD, PlayerTable.NEUTRAL_PLAYER_ID,
                                              LEFT_EDGE_FIELD_HORIZONTAL_POSITION, WORKER_POSITION);
        UnitAccounting accounting = game.getPlayerTable().getUnitAccounting();

        Assertions.assertNotNull(gameData);
        Assertions.assertNotNull(accounting);

        accounting.updateCompleted(mineral, gameData.getUnitTypes(), 1, false, null);
        game.getUnitHarvesting().moveToMinerals(worker, INSTRUCTION_LIMIT);

        Assertions.assertSame(mineral, worker.getOrderTargetUnit());
        Assertions.assertSame(mineral, worker.getTargetResourceUnit());
        Assertions.assertEquals(1, worker.getPrimaryOrderState());
    }

    @Test
    void move_to_minerals_should_find_no_field_without_a_completed_neutral_mineral_field() throws IOException {
        int[] flags = MineralSearchScenario.createWalkableFlags();
        Game game = createMineralSearchGame(flags);
        Unit worker = placeMineralSearchUnit(game, WORKER_INDEX, ScrUnitIds.PROTOSS_PROBE, 0, WORKER_POSITION, WORKER_POSITION);

        placeMineralSearchUnit(game, GATHERED_FIELD_INDEX, ScrUnitIds.RESOURCE_MINERAL_FIELD, PlayerTable.NEUTRAL_PLAYER_ID,
                               UNCOMPLETED_FIELD_HORIZONTAL_POSITION, WORKER_POSITION);
        game.getUnitHarvesting().moveToMinerals(worker, INSTRUCTION_LIMIT);

        Assertions.assertNull(worker.getOrderTargetUnit());
        Assertions.assertNull(worker.getTargetResourceUnit());
        Assertions.assertEquals(RESET_HARVEST_COLLISION_ORDER, worker.getPrimaryOrder());
    }

    @Test
    void move_to_minerals_should_replace_a_gathered_field_with_a_free_field_within_256_pixels() throws IOException {
        Game game = createGatheredFieldGame(NEAR_FREE_FIELD_HORIZONTAL_POSITION);
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        Unit worker = pool.getUnit(WORKER_INDEX);

        game.getUnitHarvesting().moveToMinerals(worker, INSTRUCTION_LIMIT);

        Assertions.assertSame(pool.getUnit(FREE_FIELD_INDEX), worker.getOrderTargetUnit());
        Assertions.assertSame(pool.getUnit(FREE_FIELD_INDEX), worker.getTargetResourceUnit());
        Assertions.assertEquals(1, worker.getPrimaryOrderState());
    }

    @Test
    void move_to_minerals_should_wait_at_a_gathered_field_without_a_free_field_within_256_pixels() throws IOException {
        Game game = createGatheredFieldGame(FAR_FREE_FIELD_HORIZONTAL_POSITION);
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        Unit worker = pool.getUnit(WORKER_INDEX);

        game.getUnitHarvesting().moveToMinerals(worker, INSTRUCTION_LIMIT);

        Assertions.assertEquals(WAIT_FOR_MINERALS_ORDER, worker.getPrimaryOrder());
        Assertions.assertSame(pool.getUnit(GATHERED_FIELD_INDEX), worker.getOrderTargetUnit());
    }

    @Test
    void return_minerals_should_deposit_and_resume_saved_target_with_hatchery_side_updates() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]), new LcgRandom(0));

        game.initializeOrderPool(ORDER_CAPACITY);
        game.getSpriteSystem().initializeImagePool(SMALL_IMAGE_CAPACITY);

        Player player = new Player(0, 0, PlayerTypes.HUMAN, 0, 0);

        player.setGatheredMinerals(GATHERED_MINERALS);
        game.getPlayerTable().setPlayer(0, player);

        Unit worker = UnitTestUtils.createUnit(1);

        worker.setUnitId(ScrUnitIds.ZERG_DRONE);
        worker.setPrimaryOrder(ScrOrderIds.RETURN_MINERALS);
        worker.setPrimaryOrderState(1);
        worker.setStatus(UnitStatusFlags.COMPLETED | UnitStatusFlags.GATHERING);
        worker.setCarryingFlags(CarryingFlags.MINERALS);
        worker.setCarriedResourceAmount(RESOURCES_PER_TRIP);
        worker.setMovementDestinationX(FIELD_POSITION);
        worker.setMovementDestinationY(RETURN_VERTICAL_POSITION);

        Sprite sprite = SpriteScenario.createSprite(0, FIELD_POSITION, RETURN_VERTICAL_POSITION, 0);
        SpriteImage image = SpriteScenario.createImage(0, 0, 0);

        image.setAnimationType(IscriptExecutor.ANIMATION_BUILT);
        sprite.setMainImage(image);
        worker.setSprite(sprite);

        Unit depot = UnitTestUtils.createUnit(2);

        depot.setUnitId(ScrUnitIds.ZERG_HATCHERY);
        depot.setStatus(UnitStatusFlags.COMPLETED | UnitStatusFlags.GROUNDED_BUILDING);
        depot.setSprite(SpriteScenario.createSprite(0, FIELD_POSITION, FIELD_POSITION, 0));
        depot.setResources(LARVA_SIDE_COUNT);
        depot.setSecondLarvaSideCount(LARVA_SIDE_COUNT);
        depot.setThirdLarvaSideCount(LARVA_SIDE_COUNT);
        depot.setFourthLarvaSideCount(LARVA_SIDE_COUNT);
        worker.setOrderTargetUnit(depot);

        Unit mineral = UnitTestUtils.createUnit(3);

        mineral.setUnitId(ScrUnitIds.RESOURCE_MINERAL_FIELD);
        mineral.setPrimaryOrder(ScrOrderIds.NOTHING);
        mineral.setSprite(SpriteScenario.createSprite(0, SAVED_FIELD_HORIZONTAL_POSITION, RETURN_VERTICAL_POSITION, RESOURCE_ELEVATION));
        worker.setTargetResource(mineral, SAVED_FIELD_HORIZONTAL_POSITION, RETURN_VERTICAL_POSITION);

        game.getOrders().queueFront(worker, ScrOrderIds.RESET_HARVEST_COLLISION, null, 0, 0, ScrUnitIds.NONE);

        game.getUnitHarvesting().returnMinerals(worker, INSTRUCTION_LIMIT);

        Assertions.assertEquals(RESOURCES_PER_TRIP, player.getMinerals());
        Assertions.assertEquals(GATHERED_MINERALS + RESOURCES_PER_TRIP, player.getGatheredMinerals());
        Assertions.assertEquals(0, worker.getCarryingFlags());
        Assertions.assertEquals(0, worker.getCarriedResourceAmount());
        Assertions.assertEquals(MOVE_TO_MINERALS_ORDER, worker.getPrimaryOrder());
        Assertions.assertSame(mineral, worker.getOrderTargetUnit());
        Assertions.assertEquals(DECREMENTED_LARVA_SIDE_COUNT, depot.getLarvaSideCount(Unit.LARVA_SIDE_LEFT));
        Assertions.assertEquals(UPPER_LARVA_SIDE_COUNT, depot.getLarvaSideCount(Unit.LARVA_SIDE_UPPER));
        Assertions.assertEquals(DECREMENTED_LARVA_SIDE_COUNT, depot.getLarvaSideCount(Unit.LARVA_SIDE_RIGHT));
        Assertions.assertEquals(DECREMENTED_LARVA_SIDE_COUNT, depot.getLarvaSideCount(Unit.LARVA_SIDE_LOWER));
    }

    @Test
    void reset_harvest_collision_should_move_behind_new_harvest_order_without_clearing_gathering() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]), new LcgRandom(0));

        game.initializeOrderPool(ORDER_CAPACITY);

        Unit worker = UnitTestUtils.createUnit(1);

        worker.setUnitId(ScrUnitIds.ZERG_DRONE);
        worker.setPrimaryOrder(ScrOrderIds.RESET_HARVEST_COLLISION);
        worker.setStatus(UnitStatusFlags.COMPLETED | UnitStatusFlags.ORDER_NOT_INTERRUPTIBLE | UnitStatusFlags.GATHERING);

        Sprite sprite = SpriteScenario.createSprite(0, WAITING_WORKER_HORIZONTAL_POSITION, FIELD_POSITION, 0);
        SpriteImage image = SpriteScenario.createImage(0, 0, 0);

        image.setAnimationType(IscriptExecutor.ANIMATION_BUILT);
        sprite.setMainImage(image);
        worker.setSprite(sprite);

        game.getOrders().queueFront(worker, ScrOrderIds.RESET_HARVEST_COLLISION, null, 0, 0, ScrUnitIds.NONE);
        game.getOrders().queueFront(worker, ScrOrderIds.RESET_HARVEST_COLLISION, null, 0, 0, ScrUnitIds.NONE);
        game.getOrders().queueFront(worker, ScrOrderIds.MOVE_TO_MINERALS, null, WORKER_POSITION, WORKER_POSITION,
                                    ScrUnitIds.NONE);

        game.getUnitHarvesting().resetHarvestCollision(worker, INSTRUCTION_LIMIT);

        Assertions.assertEquals(MOVE_TO_MINERALS_ORDER, worker.getPrimaryOrder());
        Assertions.assertEquals(GATHERING_STATUS, worker.getStatus() & UnitStatusFlags.GATHERING);

        OrderPool.Order workerFirstOrder = worker.getFirstOrder();

        Assertions.assertNotNull(workerFirstOrder);
        Assertions.assertEquals(RESET_HARVEST_COLLISION_ORDER, workerFirstOrder.getOrderId());
        Assertions.assertSame(worker.getFirstOrder(), worker.getLastOrder());

        OrderPool orderPool = game.getOrderPool();

        Assertions.assertNotNull(orderPool);
        Assertions.assertEquals(1, orderPool.getAllocatedCount());
    }

    @Test
    void target_movement_uses_bounding_box_range_and_keeps_existing_border_destination() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]), new LcgRandom(0));
        Unit worker = UnitTestUtils.createUnit(1);

        worker.setUnitId(ScrUnitIds.PROTOSS_PROBE);
        worker.setSprite(SpriteScenario.createSprite(0, WORKER_POSITION, WORKER_POSITION, 0));

        Unit mineral = UnitTestUtils.createUnit(2);

        mineral.setUnitId(ScrUnitIds.RESOURCE_MINERAL_FIELD);

        Assertions.assertNotNull(gameData);

        int targetX = WORKER_POSITION + gameData.getUnits().get("Unit Size Right", ScrUnitIds.PROTOSS_PROBE) +
                      gameData.getUnits().get("Unit Size Left", ScrUnitIds.RESOURCE_MINERAL_FIELD) + TARGET_GAP;

        mineral.setSprite(SpriteScenario.createSprite(0, targetX, WORKER_POSITION, RESOURCE_ELEVATION));

        Assertions.assertTrue(game.getUnitTargetMovement().isTargetInRange(worker, mineral, GAP_RANGE));
        Assertions.assertFalse(game.getUnitTargetMovement().isTargetInRange(worker, mineral, SHORT_RANGE));

        int borderX = targetX - gameData.getUnits().get("Unit Size Left", ScrUnitIds.RESOURCE_MINERAL_FIELD) -
                      gameData.getUnits().get("Unit Size Right", ScrUnitIds.PROTOSS_PROBE) - 1;

        worker.setMovementDestinationX(borderX);
        worker.setMovementDestinationY(WORKER_POSITION);
        worker.setMovementTargetUnit(mineral);
        worker.setMovementDestinationCountdown(DESTINATION_COUNTDOWN);
        worker.setStatus(UnitStatusFlags.IMMOVABLE);

        game.getUnitTargetMovement().moveToTargetReset(worker, mineral);

        Assertions.assertEquals(borderX, worker.getMovementDestinationX());
        Assertions.assertEquals(DESTINATION_COUNTDOWN, worker.getMovementDestinationCountdown());
        Assertions.assertEquals(0, worker.getStatus() & UnitStatusFlags.IMMOVABLE);
        Assertions.assertEquals(targetX, worker.getDirectionTargetX());
    }

    @Test
    void mineral_wait_claims_unoccupied_field_and_activates_mining_before_interruption_orders() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]), new LcgRandom(0));

        game.initializeOrderPool(ORDER_CAPACITY);

        Unit worker = UnitTestUtils.createUnit(1);

        worker.setUnitId(ScrUnitIds.PROTOSS_PROBE);
        worker.setPrimaryOrder(ScrOrderIds.WAIT_FOR_MINERALS);
        worker.setStatus(UnitStatusFlags.COMPLETED | UnitStatusFlags.GATHERING);

        Sprite sprite = SpriteScenario.createSprite(0, WAITING_WORKER_HORIZONTAL_POSITION, FIELD_POSITION, 0);
        SpriteImage image = SpriteScenario.createImage(0, 0, 0);

        image.setAnimationType(IscriptExecutor.ANIMATION_BUILT);
        sprite.setMainImage(image);
        worker.setSprite(sprite);

        Unit mineral = UnitTestUtils.createUnit(2);

        mineral.setUnitId(ScrUnitIds.RESOURCE_MINERAL_FIELD);
        mineral.setPrimaryOrder(ScrOrderIds.NOTHING);
        mineral.setSprite(SpriteScenario.createSprite(0, FIELD_POSITION, FIELD_POSITION, RESOURCE_ELEVATION));
        worker.setOrderTargetUnit(mineral);

        game.getOrders().queueFront(worker, ScrOrderIds.RESET_HARVEST_COLLISION, null, 0, 0, ScrUnitIds.NONE);

        game.getUnitHarvesting().waitForMinerals(worker, INSTRUCTION_LIMIT);

        Assertions.assertTrue(mineral.isResourceBeingGathered());
        Assertions.assertTrue(worker.isGatheringResource());
        Assertions.assertSame(mineral, worker.getGatherTarget());
        Assertions.assertEquals(MINING_MINERALS_ORDER, worker.getPrimaryOrder());
        Assertions.assertSame(mineral, worker.getOrderTargetUnit());

        OrderPool.Order workerFirstOrder = worker.getFirstOrder();

        Assertions.assertNotNull(workerFirstOrder);
        Assertions.assertEquals(GATHERING_INTERRUPTED_ORDER, workerFirstOrder.getOrderId());

        OrderPool.Order workerLastOrder = worker.getLastOrder();

        Assertions.assertNotNull(workerLastOrder);
        Assertions.assertEquals(RESET_HARVEST_COLLISION_ORDER, workerLastOrder.getOrderId());

        OrderPool orderPool = game.getOrderPool();

        Assertions.assertNotNull(orderPool);
        Assertions.assertEquals(QUEUED_ORDER_COUNT, orderPool.getAllocatedCount());

        Unit second = UnitTestUtils.createUnit(3);
        Unit third = UnitTestUtils.createUnit(4);

        for (Unit waiting : new Unit[]{second, third}) {
            waiting.setUnitId(ScrUnitIds.PROTOSS_PROBE);
            waiting.setPrimaryOrder(ScrOrderIds.WAIT_FOR_MINERALS);
            waiting.setStatus(UnitStatusFlags.COMPLETED | UnitStatusFlags.GATHERING);

            Sprite waitingSprite = SpriteScenario.createSprite(0, WAITING_WORKER_HORIZONTAL_POSITION, FIELD_POSITION, 0);
            SpriteImage waitingImage = SpriteScenario.createImage(0, 0, 0);

            waitingImage.setAnimationType(IscriptExecutor.ANIMATION_BUILT);
            waitingSprite.setMainImage(waitingImage);
            waiting.setSprite(waitingSprite);
            waiting.setOrderTargetUnit(mineral);

            game.getUnitHarvesting().waitForMinerals(waiting, INSTRUCTION_LIMIT);
        }

        Assertions.assertSame(third, mineral.getFirstGatheringWorker());
        Assertions.assertSame(second, mineral.getLastGatheringWorker());

        worker.setPrimaryOrder(ScrOrderIds.GATHERING_INTERRUPTED);
        worker.setStatus(worker.getStatus() | UnitStatusFlags.ORDER_NOT_INTERRUPTIBLE);

        game.getUnitHarvesting().interruptGathering(worker, INSTRUCTION_LIMIT);

        Assertions.assertFalse(worker.isGatheringResource());
        Assertions.assertNull(worker.getGatherTarget());
        Assertions.assertEquals(MINING_MINERALS_ORDER, second.getPrimaryOrder());
        Assertions.assertTrue(second.isGatheringResource());
        Assertions.assertSame(mineral, second.getGatherTarget());
        Assertions.assertEquals(WAIT_FOR_MINERALS_ORDER, third.getPrimaryOrder());
        Assertions.assertSame(third, mineral.getFirstGatheringWorker());
        Assertions.assertSame(third, mineral.getLastGatheringWorker());
        Assertions.assertNull(third.getNextGatheringWorker());
        Assertions.assertNull(third.getPreviousGatheringWorker());
    }

    @Test
    void move_to_minerals_should_retain_mineral_target_and_queue_collision_reset_once() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]), new LcgRandom(0));

        game.initializeOrderPool(ORDER_CAPACITY);
        game.getPathSystem().setPathPool(new PathPool());

        Unit worker = UnitTestUtils.createUnit(1);

        worker.setUnitId(ScrUnitIds.PROTOSS_PROBE);
        worker.setPrimaryOrder(ScrOrderIds.MOVE_TO_MINERALS);
        worker.setStatus(UnitStatusFlags.COMPLETED | UnitStatusFlags.IMMOVABLE);
        worker.setSprite(SpriteScenario.createSprite(0, MOVING_WORKER_POSITION, MOVING_WORKER_POSITION, 0));

        PathPool pathPool = game.getPathSystem().getPathPool();

        Assertions.assertNotNull(pathPool);

        int pathIndex = pathPool.allocate();

        pathPool.setFlags(pathIndex, 0);
        worker.setPathIndex(pathIndex);

        Unit mineral = UnitTestUtils.createUnit(2);

        mineral.setUnitId(ScrUnitIds.RESOURCE_MINERAL_FIELD);
        mineral.setSprite(SpriteScenario.createSprite(0, FIELD_POSITION, FIELD_POSITION, RESOURCE_ELEVATION));
        worker.setOrderTargetUnit(mineral);
        worker.setOrderTargetX(FIELD_POSITION);
        worker.setOrderTargetY(FIELD_POSITION);

        game.getUnitHarvesting().moveToMinerals(worker, INSTRUCTION_LIMIT);

        Assertions.assertEquals(1, worker.getPrimaryOrderState());
        Assertions.assertSame(mineral, worker.getMovementTargetUnit());
        Assertions.assertSame(mineral, worker.getTargetResourceUnit());
        Assertions.assertEquals(FIELD_POSITION, worker.getMovementDestinationX());
        Assertions.assertEquals(FIELD_POSITION, worker.getDirectionTargetY());
        Assertions.assertEquals(MOVE_TO_MINERALS_COUNTDOWN, worker.getMovementDestinationCountdown());
        Assertions.assertEquals(1, game.getPathSystem().getPathPool().getFlags(pathIndex));
        Assertions.assertEquals(0, worker.getStatus() & UnitStatusFlags.IMMOVABLE);
        Assertions.assertEquals(GATHERING_STATUS, worker.getStatus() & UnitStatusFlags.GATHERING);

        OrderPool.Order workerFirstOrder = worker.getFirstOrder();

        Assertions.assertNotNull(workerFirstOrder);
        Assertions.assertEquals(RESET_HARVEST_COLLISION_ORDER, workerFirstOrder.getOrderId());

        game.getUnitHarvesting().moveToMinerals(worker, INSTRUCTION_LIMIT);

        Assertions.assertSame(worker.getFirstOrder(), worker.getLastOrder());

        OrderPool orderPool = game.getOrderPool();

        Assertions.assertNotNull(orderPool);
        Assertions.assertEquals(1, orderPool.getAllocatedCount());
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }
}
