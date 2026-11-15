package org.fogofwarlabs.sc4j.scr.game.production;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.game.NetworkReplayScenario;
import org.fogofwarlabs.sc4j.scr.game.movement.UnitTargetMovement;
import org.fogofwarlabs.sc4j.scr.game.order.OrderPool;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Checks Terran construction, cancellation, the end of placement for a far builder, the facing test of a builder without a ground weapon against the
 * synchronization words of a recorded LAN game, the placement of the empty type {@code 228} and the placement of the type read past the five queue
 * slots.
 * <p>
 * The memory layouts are the contract of the type {@code 228} test, because replays reproduce the values they give: the build time of type
 * {@code 228} is the units.dat Requirement Index of type {@code 0}, and its EUD-visible type count row is the first row of the completed type counts
 * in the game context. The unit record layout is the contract of the test past the five queue slots: queue index {@code 5} is the EUD-visible energy
 * WORD.
 */
@RequiresInstallation
final class UnitTerranConstructionTest {
    private static final int INSTRUCTION_LIMIT = 10000;
    /**
     * The test player and the second human player of the construction game, and the starting resources of the test player.
     */
    private static final int PLAYER = ProductionScenario.PLAYER;
    private static final int ENEMY_PLAYER = ProductionScenario.ENEMY_PLAYER;
    private static final int STARTING_MINERALS = ProductionScenario.STARTING_MINERALS;
    private static final int STARTING_GAS = ProductionScenario.STARTING_GAS;
    /**
     * The human player slot of the running game.
     */
    private static final int HUMAN_PLAYER = 1;
    private static final int SITE_TILE = 10;
    private static final int DEPOT_HALF_WIDTH = 48;
    private static final int DEPOT_HALF_HEIGHT = 32;
    private static final int DEPOT_X = SITE_TILE * TerrainTileset.TILE_SIZE + DEPOT_HALF_WIDTH;
    private static final int DEPOT_Y = SITE_TILE * TerrainTileset.TILE_SIZE + DEPOT_HALF_HEIGHT;

    private static final int SCV_X = 200;
    private static final int SCV_Y = 200;
    private static final int SECOND_SCV_X = 240;
    private static final int ENEMY_SCV_X = 260;
    /**
     * The divisor of the Supply Depot hit points that leaves the depot damaged.
     */
    private static final int DAMAGED_HIT_POINT_DIVISOR = 2;
    private static final int FAR_WORKER_X = 3200;
    private static final int FAR_WORKER_Y = 2800;
    /**
     * The distance from the far worker to its building site, beyond the reach of the placement order.
     */
    private static final int FAR_SITE_DISTANCE = 400;
    /**
     * The build queue index whose WORD is the energy, the first WORD after the five queue slots.
     */
    private static final int ENERGY_QUEUE_INDEX = 5;

    private static final int PLACE_STATE_MOVING = 1;
    private static final int CONSTRUCT_STATE_ENTERING = 3;
    private static final int CONSTRUCT_STATE_PICKING = 4;
    /**
     * The frames the construction may take beyond the build time before the test stops it.
     */
    private static final int CONSTRUCTION_FRAME_MARGIN = 10;
    /**
     * The quarters of the mineral cost that a canceled building refunds.
     */
    private static final int REFUND_QUARTERS = 3;
    private static final int QUARTERS = 4;

    /**
     * The recording client's player of the builder test.
     */
    private static final int BUILDER_LOCAL_PLAYER = 0;
    /**
     * The last frame the recording of the builder test runs to.
     */
    private static final int BUILDER_LAST_FRAME = 8000;
    /**
     * The frames and the native synchronization words of records 1 and 3 of the recording client of the builder test.
     */
    private static final long RECORD_ONE_FRAME = 7998L;
    private static final int RECORD_ONE_WORD = 0xDBA1;
    private static final long RECORD_THREE_FRAME = 8000L;
    private static final int RECORD_THREE_WORD = 0xFA75;

    /**
     * The orders of the construction tests.
     */
    private static final int DIE_ORDER = 0;
    private static final int PLACE_BUILDING_ORDER = 30;
    private static final int CONSTRUCTING_BUILDING_ORDER = 33;
    private static final int REPAIR_ORDER = 34;
    private static final int INCOMPLETE_BUILDING_ORDER = 44;
    private static final int RESET_COLLISION_ORDER = 150;
    /**
     * The unit types of the Supply Depot and of the empty type.
     */
    private static final int SUPPLY_DEPOT_TYPE = 109;
    private static final int EMPTY_UNIT_TYPE = 228;
    /**
     * The completed status flag.
     */
    private static final int COMPLETED_FLAG = 0x1;
    /**
     * The divisor of the hit points that a building starts its construction with.
     */
    private static final int CONSTRUCTION_START_DIVISOR = 10;

    private static @Nullable GameData gameData;
    private static @Nullable ReplayRuntime runtime;

    @BeforeAll
    static void loadGameData() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
        runtime  = RunningGameScenario.start(gameData);
    }

    @AfterAll
    static void closeGameData() {
        runtime = null;

        if (gameData != null) {
            gameData.close();
        }
    }

    @Test
    void scv_places_charges_and_completes_building_through_construction_animation() {
        Game game = ProductionScenario.createConstructionGame(gameData);

        Assertions.assertNotNull(gameData);

        DatTable units = gameData.getUnits();
        Player player = game.getPlayerTable().getPlayer(PLAYER);
        Unit scv = ProductionScenario.createCompletedUnit(game, ScrUnitIds.TERRAN_SCV, DEPOT_X, DEPOT_Y, PLAYER);

        scv.setBuildQueueIndex(0);
        scv.setFirstBuildQueueType(ScrUnitIds.TERRAN_SUPPLY_DEPOT);

        game.getOrders().issue(scv, ScrOrderIds.PLACE_BUILDING, null, DEPOT_X, DEPOT_Y, ScrUnitIds.NONE, false, INSTRUCTION_LIMIT);
        game.getUnitTerranConstruction().placeBuilding(scv, INSTRUCTION_LIMIT);

        Assertions.assertEquals(PLACE_STATE_MOVING, scv.getPrimaryOrderState());
        Assertions.assertTrue(UnitTargetMovement.isAtMovementTarget(scv));
        Assertions.assertTrue(game.getCostTests().testUnit(game, PLAYER, ScrUnitIds.TERRAN_SUPPLY_DEPOT, true, false));
        Assertions.assertTrue(game.getUnitPlacement().canPlaceBuilding(scv, PLAYER, ScrUnitIds.TERRAN_SUPPLY_DEPOT, DEPOT_X, DEPOT_Y, true, false));

        game.getUnitTerranConstruction().placeBuilding(scv, INSTRUCTION_LIMIT);

        Assertions.assertEquals(CONSTRUCTING_BUILDING_ORDER, scv.getPrimaryOrder());
        Assertions.assertEquals(CONSTRUCT_STATE_ENTERING, scv.getPrimaryOrderState());

        Unit depot = scv.getOrderTargetUnit();

        Assertions.assertNotNull(depot);
        Assertions.assertEquals(SUPPLY_DEPOT_TYPE, depot.getUnitId());
        Assertions.assertEquals(0, depot.getStatus() & UnitStatusFlags.COMPLETED);
        Assertions.assertSame(scv, depot.getParentUnit());
        Assertions.assertEquals(INCOMPLETE_BUILDING_ORDER, depot.getPrimaryOrder());
        Assertions.assertNotNull(player);

        Assertions.assertEquals(STARTING_MINERALS - units.get("Mineral Cost", ScrUnitIds.TERRAN_SUPPLY_DEPOT), player.getMinerals());
        Assertions.assertEquals(EMPTY_UNIT_TYPE, scv.getFirstBuildQueueType());
        Assertions.assertEquals(0, scv.getStatus() & UnitStatusFlags.GROUND_UNIT);

        int expectedElevation = units.get("Elevation Level", ScrUnitIds.TERRAN_SCV) + 1;
        Sprite constructingSprite = scv.getSpriteOrNull();

        Assertions.assertNotNull(constructingSprite);

        Assertions.assertEquals(expectedElevation, constructingSprite.getElevationLevel());

        OrderPool.Order constructionOrder = scv.getFirstOrder();

        Assertions.assertNotNull(constructionOrder);

        Assertions.assertEquals(RESET_COLLISION_ORDER, constructionOrder.getOrderId());

        int buildTime = units.get("Build Time", ScrUnitIds.TERRAN_SUPPLY_DEPOT);

        scv.setPrimaryOrderState(CONSTRUCT_STATE_PICKING);

        long randomBefore = game.getRandom().getTotalRandomCounts();
        int frames = 0;

        while ((depot.getStatus() & UnitStatusFlags.COMPLETED) == 0 && frames < buildTime + CONSTRUCTION_FRAME_MARGIN) {
            game.getUnitTerranConstruction().updateConstructing(scv, INSTRUCTION_LIMIT);
            frames++;
        }

        Assertions.assertEquals(buildTime + 1, frames);
        Assertions.assertTrue(game.getRandom().getTotalRandomCounts() > randomBefore);
        Assertions.assertEquals(COMPLETED_FLAG, depot.getStatus() & UnitStatusFlags.COMPLETED);
        Assertions.assertEquals(units.get("Hit Points", ScrUnitIds.TERRAN_SUPPLY_DEPOT), depot.getHitPoints());
        Assertions.assertNull(depot.getParentUnit());
        Assertions.assertNotEquals(CONSTRUCTING_BUILDING_ORDER, scv.getPrimaryOrder());

        UnitAccounting completedBuildingAccounting = game.getPlayerTable().getUnitAccounting();

        Assertions.assertNotNull(completedBuildingAccounting);

        Assertions.assertEquals(1, completedBuildingAccounting.getCompletedTypeCount(ScrUnitIds.TERRAN_SUPPLY_DEPOT, PLAYER));
    }

    @Test
    void scv_with_an_emptied_queue_slot_places_a_unit_of_type_228_without_cost() {
        Game game = ProductionScenario.createConstructionGame(gameData);

        Assertions.assertNotNull(gameData);

        ScrUnitTypeView units = gameData.getUnitTypes();
        Player player = game.getPlayerTable().getPlayer(PLAYER);
        Unit scv = ProductionScenario.createCompletedUnit(game, ScrUnitIds.TERRAN_SCV, DEPOT_X, DEPOT_Y, PLAYER);

        scv.setBuildQueueIndex(0);
        scv.setFirstBuildQueueType(ScrUnitIds.NONE);

        game.getOrders().issue(scv, ScrOrderIds.PLACE_BUILDING, null, DEPOT_X, DEPOT_Y, ScrUnitIds.NONE, false, INSTRUCTION_LIMIT);
        game.getUnitTerranConstruction().placeBuilding(scv, INSTRUCTION_LIMIT);
        game.getUnitTerranConstruction().placeBuilding(scv, INSTRUCTION_LIMIT);

        Unit building = scv.getOrderTargetUnit();

        Assertions.assertNotNull(building);
        Assertions.assertNotNull(player);

        Assertions.assertEquals(EMPTY_UNIT_TYPE, building.getUnitId());
        Assertions.assertEquals(Integer.divideUnsigned(units.getHitPoints(ScrUnitIds.NONE), CONSTRUCTION_START_DIVISOR), building.getHitPoints());
        Assertions.assertEquals(units.getRequirementIndex(ScrUnitIds.TERRAN_MARINE), building.getRemainingBuildTime());
        Assertions.assertEquals(STARTING_MINERALS, player.getMinerals());
        Assertions.assertEquals(STARTING_GAS, player.getGas());

        UnitAccounting accounting = game.getPlayerTable().getUnitAccounting();

        Assertions.assertNotNull(accounting);

        // The type count row 228 is the completed count row 0.
        Assertions.assertEquals(1, accounting.getCompletedTypeCount(ScrUnitIds.TERRAN_MARINE, PLAYER));
    }

    @Test
    void place_building_should_read_the_energy_as_the_build_type_at_queue_index_five() {
        Game game = ProductionScenario.createConstructionGame(gameData);

        Assertions.assertNotNull(gameData);

        ScrUnitTypeView units = gameData.getUnitTypes();
        Player player = game.getPlayerTable().getPlayer(PLAYER);
        Unit scv = ProductionScenario.createCompletedUnit(game, ScrUnitIds.TERRAN_SCV, DEPOT_X, DEPOT_Y, PLAYER);

        scv.setBuildQueueIndex(ENERGY_QUEUE_INDEX);
        scv.setEnergy(ScrUnitIds.TERRAN_SUPPLY_DEPOT);

        game.getOrders().issue(scv, ScrOrderIds.PLACE_BUILDING, null, DEPOT_X, DEPOT_Y, ScrUnitIds.NONE, false, INSTRUCTION_LIMIT);
        game.getUnitTerranConstruction().placeBuilding(scv, INSTRUCTION_LIMIT);
        game.getUnitTerranConstruction().placeBuilding(scv, INSTRUCTION_LIMIT);

        Unit depot = scv.getOrderTargetUnit();

        Assertions.assertNotNull(depot);
        Assertions.assertNotNull(player);

        Assertions.assertEquals(SUPPLY_DEPOT_TYPE, depot.getUnitId());
        Assertions.assertEquals(STARTING_MINERALS - units.getMineralCost(ScrUnitIds.TERRAN_SUPPLY_DEPOT), player.getMinerals());
        // Emptying the current queue slot writes the empty type into the energy.
        Assertions.assertEquals(EMPTY_UNIT_TYPE, scv.getEnergy());
    }

    @Test
    void scv_resumes_abandoned_building_and_canceled_construction_refunds_minerals() {
        Game game = ProductionScenario.createConstructionGame(gameData);

        Assertions.assertNotNull(gameData);

        DatTable units = gameData.getUnits();
        Unit scv = ProductionScenario.createCompletedUnit(game, ScrUnitIds.TERRAN_SCV, SCV_X, SCV_Y, PLAYER);
        Unit depot = ProductionScenario.createCompletedUnit(game, ScrUnitIds.TERRAN_SUPPLY_DEPOT, DEPOT_X, DEPOT_Y, PLAYER);

        depot.setStatus(depot.getStatus() & ~UnitStatusFlags.COMPLETED | UnitStatusFlags.GROUNDED_BUILDING);
        depot.setParentUnit(scv);
        scv.setOrderTargetUnit(depot);

        Unit secondScv = ProductionScenario.createCompletedUnit(game, ScrUnitIds.TERRAN_SCV, SECOND_SCV_X, SCV_Y, PLAYER);

        Assertions.assertEquals(-1, game.getUnitTerranConstruction().getDefaultConstructionOrder(secondScv, depot));

        game.getUnitLifecycle().removeTargetReferences(depot, scv);

        Assertions.assertNull(depot.getParentUnit());
        Assertions.assertEquals(CONSTRUCTING_BUILDING_ORDER, game.getUnitTerranConstruction().getDefaultConstructionOrder(scv, depot));

        Unit enemyScv = ProductionScenario.createCompletedUnit(game, ScrUnitIds.TERRAN_SCV, ENEMY_SCV_X, SCV_Y, ENEMY_PLAYER);

        Assertions.assertEquals(-1, game.getUnitTerranConstruction().getDefaultConstructionOrder(enemyScv, depot));

        depot.setStatus(depot.getStatus() | UnitStatusFlags.COMPLETED);
        depot.setHitPoints(units.get("Hit Points", ScrUnitIds.TERRAN_SUPPLY_DEPOT) / DAMAGED_HIT_POINT_DIVISOR);

        Assertions.assertEquals(REPAIR_ORDER, game.getUnitTerranConstruction().getDefaultConstructionOrder(scv, depot));

        depot.setHitPoints(units.get("Hit Points", ScrUnitIds.TERRAN_SUPPLY_DEPOT));

        Assertions.assertEquals(-1, game.getUnitTerranConstruction().getDefaultConstructionOrder(scv, depot));

        depot.setStatus(depot.getStatus() & ~UnitStatusFlags.COMPLETED);
        depot.setPrimaryOrder(ScrOrderIds.INCOMPLETE_BUILDING);

        Player player = game.getPlayerTable().getPlayer(PLAYER);

        Assertions.assertNotNull(player);

        int minerals = player.getMinerals();

        Assertions.assertTrue(game.getUnitConstruction().cancelBuildingUnit(depot, INSTRUCTION_LIMIT));

        int refund = units.get("Mineral Cost", ScrUnitIds.TERRAN_SUPPLY_DEPOT) * REFUND_QUARTERS / QUARTERS;

        Assertions.assertEquals(minerals + refund, player.getMinerals());
        Assertions.assertEquals(DIE_ORDER, depot.getPrimaryOrder());
    }

    @Test
    void place_building_should_end_order_of_far_builder_with_emptied_queue() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit worker = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_SCV, FAR_WORKER_X, FAR_WORKER_Y, HUMAN_PLAYER);
            Sprite sprite = worker.getSpriteOrNull();

            Assertions.assertNotNull(sprite);

            worker.setPrimaryOrder(ScrOrderIds.PLACE_BUILDING);
            worker.setPrimaryOrderState(PLACE_STATE_MOVING);
            worker.setOrderTargetX(sprite.getX() + FAR_SITE_DISTANCE);
            worker.setOrderTargetY(sprite.getY());
            game.getUnitPositioning().setMovementDestination(worker, sprite.getX(), sprite.getY());

            Assertions.assertEquals(EMPTY_UNIT_TYPE, worker.getBuildQueueUnitType(worker.getBuildQueueIndex()));

            Assertions.assertDoesNotThrow(() -> game.getUnitTerranConstruction().placeBuilding(worker, RunningGameScenario.INSTRUCTION_LIMIT));

            Assertions.assertNotEquals(PLACE_BUILDING_ORDER, worker.getPrimaryOrder(), "Beyond 128 pixels the order ends.");
        });
    }

    @Test
    void builder_without_ground_weapon_keeps_native_sync_words_through_facing_check() throws IOException {
        Path map = ExternalInput.INSTALLATION.resolveFile(ExternalInput.MAPS_FOLDER, ExternalInput.TEST_MAPS_FOLDER, "civ.scx");

        Map<Long, Integer> words = NetworkReplayScenario.collectResourceWords(ExternalInput.INSTALLATION.getPath(), map,
                                                                              GameTestResources.REPLAY_CIVILIZATION_DEFENSE_BUILDER_DESYNC,
                                                                              BUILDER_LOCAL_PLAYER,
                                                                              BUILDER_LAST_FRAME);

        Assertions.assertEquals(RECORD_ONE_WORD, words.get(RECORD_ONE_FRAME), "The native client's record 1 (frame 7998).");
        Assertions.assertEquals(RECORD_THREE_WORD, words.get(RECORD_THREE_FRAME), "The native client's record 3 (frame 8000).");
    }
}
