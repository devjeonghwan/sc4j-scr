package org.fogofwarlabs.sc4j.scr.game.order;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.replay.SelectionRequest;
import org.fogofwarlabs.sc4j.scr.game.combat.CombatGameScenario;
import org.fogofwarlabs.sc4j.scr.game.order.OrderPool.Order;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.resource.CarryingFlags;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
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
 * Checks how the order, right-click, rally, stop, return cargo and hold position commands change the orders and rally targets of the selected
 * units, including a selected enemy turret of a Siege Tank.
 */
@RequiresInstallation
final class UnitCommandsTest {
    private static final int LOCAL_PLAYER_INDEX = 8;
    private static final int INSTRUCTION_LIMIT = 10000;

    /**
     * The horizontal and vertical position of the worker of {@link WorkerCommandScenario}.
     */
    private static final int WORKER_POSITION = WorkerCommandScenario.WORKER_POSITION;
    /**
     * The pool indices of the worker and of the second unit of {@link WorkerCommandScenario}, a Mineral Field unless a test changes its type.
     */
    private static final int WORKER_INDEX = WorkerCommandScenario.WORKER_POOL_INDEX;
    private static final int RESOURCE_INDEX = WorkerCommandScenario.RESOURCE_POOL_INDEX;
    /**
     * The horizontal and vertical sprite position of the second unit of {@link WorkerCommandScenario}.
     */
    private static final int RESOURCE_POSITION = 320;
    /**
     * A unit type past {@code units.dat}, which an EUD map can write to a unit.
     */
    private static final int PAST_UNITS_DAT_TYPE = 230;

    /**
     * The horizontal position past the map, whose width is {@code 1024} pixels.
     */
    private static final int OUT_OF_MAP_X = 1024;
    /**
     * The position of a right click on the second unit.
     */
    private static final int CLICK_POSITION = 250;
    /**
     * The position of a unit rally command, which the target unit position replaces.
     */
    private static final int UNIT_RALLY_POSITION = 100;
    /**
     * The tile rally position of a rally command.
     */
    private static final int RALLY_X = 400;
    private static final int RALLY_Y = 500;
    /**
     * The point of a right click without a target.
     */
    private static final int MOVE_TARGET_X = 500;
    private static final int MOVE_TARGET_Y = 600;
    /**
     * The movement destination of a moving unit that is stopped.
     */
    private static final int MOVEMENT_DESTINATION_X = 400;
    private static final int MOVEMENT_DESTINATION_Y = 500;

    /**
     * An order position far from both units of the duel world and an order position near them, each used for both coordinates.
     */
    private static final int FAR_POSITION = 1000;
    private static final int NEAR_POSITION = 300;
    /**
     * The position of the attack move order without a target, which becomes the order target position.
     */
    private static final int ATTACK_MOVE_X = 500;
    private static final int ATTACK_MOVE_Y = 600;
    /**
     * The player of the second unit of the duel world, who selects no unit.
     */
    private static final int SECOND_PLAYER = 1;

    /**
     * The unit, sprite and image capacities of the Siege Tank world.
     */
    private static final int TURRET_UNIT_CAPACITY = 8;
    private static final int TURRET_SPRITE_CAPACITY = 8;
    private static final int TURRET_IMAGE_CAPACITY = 64;
    /**
     * The vertical position of every unit of the Siege Tank world.
     */
    private static final int UNIT_POSITION_Y = 200;
    /**
     * The horizontal positions of the siege tank with its turret and of the enemy marine the right click targets.
     */
    private static final int TANK_POSITION_X = 200;
    private static final int MARINE_POSITION_X = 300;
    /**
     * The pool indices of the siege tank, of its turret and of the enemy marine.
     */
    private static final int TANK_INDEX = 1;
    private static final int TURRET_INDEX = 2;
    private static final int MARINE_INDEX = 3;
    /**
     * The team size of the selection outside a team game.
     */
    private static final int TEAM_SIZE = 0;
    /**
     * The unit handle width in bytes of the selection request.
     */
    private static final int HANDLE_WIDTH_BYTES = 2;

    /**
     * The Die, Stop, Player Guard, Move, Follow, Return Gas, Move To Minerals, Return Minerals, Hold Position and Heal Move orders.
     */
    private static final int DIE_ORDER = 0;
    private static final int STOP_ORDER = 1;
    private static final int PLAYER_GUARD_ORDER = 3;
    private static final int MOVE_ORDER = 6;
    private static final int FOLLOW_ORDER = 49;
    private static final int RETURN_GAS_ORDER = 84;
    private static final int MOVE_TO_MINERALS_ORDER = 85;
    private static final int RETURN_MINERALS_ORDER = 90;
    private static final int HOLD_POSITION_ORDER = 107;
    private static final int HEAL_MOVE_ORDER = 177;
    /**
     * The order state of a unit whose death destroyed it.
     */
    private static final int DESTROYED_DIE_STATE = 1;
    /**
     * The unit type id that names no unit type.
     */
    private static final int NO_UNIT_TYPE = 228;
    /**
     * The user action flag of a commanded unit.
     */
    private static final int COMMANDED_ACTION_FLAG = 0x2;
    /**
     * The carrying flags of a worker that carries a powerup.
     */
    private static final int CARRIED_POWERUP_FLAGS = 0x4;

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
        Assertions.assertNotNull(gameData);

        return WorkerCommandScenario.createGame(gameData, workerType);
    }

    private static @NotNull Game createDuelGame(int firstUnitType, int secondUnitType) {
        Assertions.assertNotNull(gameData);

        return CombatGameScenario.createDuelGame(gameData, firstUnitType, secondUnitType);
    }


    @Test
    void right_click_should_use_actual_resource_position_and_keep_selection_and_pool_identity() {
        for (int workerType : new int[]{ScrUnitIds.TERRAN_SCV, ScrUnitIds.ZERG_DRONE, ScrUnitIds.PROTOSS_PROBE}) {
            Game game = createState(workerType);
            UnitCommands commands = game.getUnitCommands();
            UnitPool workerPool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(workerPool);

            Unit worker = workerPool.getUnit(WORKER_INDEX);
            UnitPool resourceUnitPool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(resourceUnitPool);

            Unit resource = resourceUnitPool.getUnit(RESOURCE_INDEX);
            UnitPool generationUnitPool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(generationUnitPool);

            int generation = generationUnitPool.getGeneration(WORKER_INDEX);
            int randomCount = game.getRandom().getTotalRandomCounts();

            Assertions.assertTrue(commands.rightClick(0, WORKER_POSITION, WORKER_POSITION, resource, ScrUnitIds.NONE, false, INSTRUCTION_LIMIT));

            Assertions.assertEquals(MOVE_TO_MINERALS_ORDER, worker.getPrimaryOrder());
            Assertions.assertSame(resource, worker.getOrderTargetUnit());
            Assertions.assertEquals(RESOURCE_POSITION, worker.getOrderTargetX());
            Assertions.assertEquals(RESOURCE_POSITION, worker.getOrderTargetY());
            Assertions.assertEquals(NO_UNIT_TYPE, worker.getOrderTargetUnitType());
            Assertions.assertEquals(COMMANDED_ACTION_FLAG, worker.getUserActionFlags() & (Unit.USER_ACTION_ORDER_DONE | Unit.USER_ACTION_COMMANDED));

            OrderPool orders = game.getOrderPool();

            Assertions.assertNotNull(orders);

            Assertions.assertEquals(0, orders.getAllocatedCount());

            UnitSelection selection = game.getSelection();

            Assertions.assertNotNull(selection);

            Assertions.assertSame(worker, selection.getSelectedUnit(0, 0));
            Assertions.assertEquals(generation, generationUnitPool.getGeneration(WORKER_INDEX));
            Assertions.assertEquals(randomCount, game.getRandom().getTotalRandomCounts());
        }
    }

    @Test
    void right_click_should_reject_out_of_bounds_and_disabled_worker_without_order_change() {
        Game game = createState(ScrUnitIds.ZERG_DRONE);
        UnitCommands commands = game.getUnitCommands();
        UnitPool workerPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(workerPool);

        Unit worker = workerPool.getUnit(WORKER_INDEX);
        UnitPool resourceUnitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(resourceUnitPool);

        Unit resource = resourceUnitPool.getUnit(RESOURCE_INDEX);

        Assertions.assertFalse(commands.rightClick(0, OUT_OF_MAP_X, WORKER_POSITION, resource, ScrUnitIds.NONE, false, INSTRUCTION_LIMIT));

        worker.setLockdownTimer(1);

        Assertions.assertFalse(commands.rightClick(0, WORKER_POSITION, WORKER_POSITION, resource, ScrUnitIds.NONE, false, INSTRUCTION_LIMIT));

        Assertions.assertEquals(PLAYER_GUARD_ORDER, worker.getPrimaryOrder());
        Assertions.assertNull(worker.getOrderTargetUnit());

        OrderPool orders = game.getOrderPool();

        Assertions.assertNotNull(orders);

        Assertions.assertEquals(0, orders.getAllocatedCount());
    }

    @Test
    void rally_should_use_live_target_position_then_replace_it_with_tile_position() {
        Game game = createState(ScrUnitIds.ZERG_DRONE);
        UnitCommands commands = game.getUnitCommands();
        UnitPool factoryPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(factoryPool);

        Unit factory = factoryPool.getUnit(WORKER_INDEX);
        UnitPool resourceUnitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(resourceUnitPool);

        Unit resource = resourceUnitPool.getUnit(RESOURCE_INDEX);

        factory.setUnitId(ScrUnitIds.ZERG_HATCHERY);
        factory.setStatus(UnitStatusFlags.GROUNDED_BUILDING);

        int randomCount = game.getRandom().getTotalRandomCounts();

        Assertions.assertTrue(commands.rally(0, ScrOrderIds.RALLY_POINT_UNIT, UNIT_RALLY_POSITION, UNIT_RALLY_POSITION, resource));

        Assertions.assertSame(resource, factory.getRallyTargetUnit());
        Assertions.assertEquals(RESOURCE_POSITION, factory.getRallyPositionX());
        Assertions.assertEquals(RESOURCE_POSITION, factory.getRallyPositionY());

        Assertions.assertTrue(commands.rally(0, ScrOrderIds.RALLY_POINT_TILE, RALLY_X, RALLY_Y, resource));

        Assertions.assertNull(factory.getRallyTargetUnit());
        Assertions.assertEquals(RALLY_X, factory.getRallyPositionX());
        Assertions.assertEquals(RALLY_Y, factory.getRallyPositionY());
        Assertions.assertEquals(PLAYER_GUARD_ORDER, factory.getPrimaryOrder());

        OrderPool orders = game.getOrderPool();

        Assertions.assertNotNull(orders);

        Assertions.assertEquals(0, orders.getAllocatedCount());
        Assertions.assertEquals(randomCount, game.getRandom().getTotalRandomCounts());
        Assertions.assertEquals(COMMANDED_ACTION_FLAG, factory.getUserActionFlags() & Unit.USER_ACTION_COMMANDED);
    }

    @Test
    void rally_should_default_to_factory_and_keep_previous_target_when_disabled() {
        Game game = createState(ScrUnitIds.ZERG_DRONE);
        UnitPool factoryPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(factoryPool);

        Unit factory = factoryPool.getUnit(WORKER_INDEX);

        factory.setUnitId(ScrUnitIds.ZERG_HATCHERY);
        factory.setStatus(UnitStatusFlags.GROUNDED_BUILDING);

        Assertions.assertTrue(game.getUnitCommands().rally(0, ScrOrderIds.RALLY_POINT_UNIT, UNIT_RALLY_POSITION, UNIT_RALLY_POSITION, null));

        Assertions.assertSame(factory, factory.getRallyTargetUnit());

        Sprite horizontalSprite = factory.getSpriteOrNull();

        Assertions.assertNotNull(horizontalSprite);

        Assertions.assertEquals(horizontalSprite.getX(), factory.getRallyPositionX());

        Sprite verticalSprite = factory.getSpriteOrNull();

        Assertions.assertNotNull(verticalSprite);

        Assertions.assertEquals(verticalSprite.getY(), factory.getRallyPositionY());

        factory.setStasisTimer(1);

        Assertions.assertFalse(game.getUnitCommands().rally(0, ScrOrderIds.RALLY_POINT_TILE, RALLY_X, RALLY_Y, null));

        Assertions.assertSame(factory, factory.getRallyTargetUnit());
    }

    @Test
    void rally_should_ignore_a_grounded_building_whose_type_is_past_units_dat() {
        Game game = createState(ScrUnitIds.ZERG_DRONE);
        UnitPool buildingPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(buildingPool);

        Unit building = buildingPool.getUnit(WORKER_INDEX);

        building.setUnitId(PAST_UNITS_DAT_TYPE);
        building.setStatus(UnitStatusFlags.GROUNDED_BUILDING);

        int rallyX = building.getRallyPositionX();
        int rallyY = building.getRallyPositionY();

        Assertions.assertFalse(game.getUnitCommands().rally(0, ScrOrderIds.RALLY_POINT_TILE, RALLY_X, RALLY_Y, null));

        Assertions.assertEquals(rallyX, building.getRallyPositionX());
        Assertions.assertEquals(rallyY, building.getRallyPositionY());
    }

    @Test
    void right_click_should_move_selected_flyer_to_point_without_drawing_random() {
        Game game = createState(ScrUnitIds.ZERG_OVERLORD);
        UnitCommands commands = game.getUnitCommands();
        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit unit = unitPool.getUnit(WORKER_INDEX);
        int randomCount = game.getRandom().getTotalRandomCounts();

        Assertions.assertTrue(commands.rightClick(0, MOVE_TARGET_X, MOVE_TARGET_Y, null, ScrUnitIds.NONE, false, INSTRUCTION_LIMIT));

        Assertions.assertEquals(MOVE_ORDER, unit.getPrimaryOrder());
        Assertions.assertNull(unit.getOrderTargetUnit());
        Assertions.assertEquals(MOVE_TARGET_X, unit.getOrderTargetX());
        Assertions.assertEquals(MOVE_TARGET_Y, unit.getOrderTargetY());
        Assertions.assertEquals(COMMANDED_ACTION_FLAG, unit.getUserActionFlags() & Unit.USER_ACTION_COMMANDED);
        Assertions.assertEquals(randomCount, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void right_click_should_update_incomplete_factory_rally_without_issuing_movement() {
        Game game = createState(ScrUnitIds.ZERG_DRONE);
        UnitCommands commands = game.getUnitCommands();
        UnitPool factoryPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(factoryPool);

        Unit factory = factoryPool.getUnit(WORKER_INDEX);
        UnitPool targetUnitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(targetUnitPool);

        Unit target = targetUnitPool.getUnit(RESOURCE_INDEX);

        factory.setUnitId(ScrUnitIds.ZERG_HATCHERY);
        factory.setStatus(UnitStatusFlags.GROUNDED_BUILDING);

        int actionFlags = factory.getUserActionFlags();

        Assertions.assertTrue(commands.rightClick(0, MOVE_TARGET_X, MOVE_TARGET_Y, null, ScrUnitIds.NONE, true, INSTRUCTION_LIMIT));

        Assertions.assertEquals(MOVE_TARGET_X, factory.getRallyPositionX());
        Assertions.assertEquals(MOVE_TARGET_Y, factory.getRallyPositionY());

        Assertions.assertTrue(commands.rightClick(0, WORKER_POSITION, WORKER_POSITION, target, ScrUnitIds.NONE, false, INSTRUCTION_LIMIT));

        Assertions.assertSame(target, factory.getRallyTargetUnit());
        Assertions.assertEquals(RESOURCE_POSITION, factory.getRallyPositionX());
        Assertions.assertEquals(RESOURCE_POSITION, factory.getRallyPositionY());
        Assertions.assertEquals(PLAYER_GUARD_ORDER, factory.getPrimaryOrder());

        OrderPool orders = game.getOrderPool();

        Assertions.assertNotNull(orders);

        Assertions.assertEquals(0, orders.getAllocatedCount());
        Assertions.assertEquals(actionFlags, factory.getUserActionFlags());
    }

    @Test
    void stop_should_replace_movement_and_return_to_idle_through_normal_order_completion() {
        Game game = createState(ScrUnitIds.ZERG_DRONE);
        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit unit = unitPool.getUnit(WORKER_INDEX);

        unit.setPrimaryOrder(ScrOrderIds.MOVE);
        unit.setMovementDestinationX(MOVEMENT_DESTINATION_X);
        unit.setMovementDestinationY(MOVEMENT_DESTINATION_Y);

        int randomCount = game.getRandom().getTotalRandomCounts();

        Assertions.assertTrue(game.getUnitCommands().stop(0, false, INSTRUCTION_LIMIT));

        Assertions.assertEquals(STOP_ORDER, unit.getPrimaryOrder());

        unit.setPrimaryOrderCountdown(0);

        game.getUnitOrders().updatePrimary(unit, PlayerTypes.HUMAN, INSTRUCTION_LIMIT);

        Assertions.assertNotNull(gameData);

        Assertions.assertEquals(gameData.getUnits().get("Return to Idle", ScrUnitIds.ZERG_DRONE), unit.getPrimaryOrder());
        Assertions.assertEquals(unit.getPositionX(), unit.getMovementDestinationX());
        Assertions.assertEquals(unit.getPositionY(), unit.getMovementDestinationY());
        Assertions.assertEquals(unit.getMovementDestinationX(), unit.getDirectionTargetX());
        Assertions.assertEquals(unit.getMovementDestinationY(), unit.getDirectionTargetY());
        Assertions.assertEquals(randomCount, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void stop_should_queue_after_current_movement_and_be_rejected_by_disabled_unit() {
        Game game = createState(ScrUnitIds.ZERG_DRONE);
        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit unit = unitPool.getUnit(WORKER_INDEX);

        unit.setPrimaryOrder(ScrOrderIds.MOVE);

        Assertions.assertTrue(game.getUnitCommands().stop(0, true, INSTRUCTION_LIMIT));

        Assertions.assertEquals(MOVE_ORDER, unit.getPrimaryOrder());

        Order firstOrder = unit.getFirstOrder();

        Assertions.assertNotNull(firstOrder);

        Assertions.assertEquals(STOP_ORDER, firstOrder.getOrderId());

        unit.setLockdownTimer(1);

        Assertions.assertFalse(game.getUnitCommands().stop(0, false, INSTRUCTION_LIMIT));

        Assertions.assertEquals(MOVE_ORDER, unit.getPrimaryOrder());

        OrderPool orders = game.getOrderPool();

        Assertions.assertNotNull(orders);

        Assertions.assertEquals(1, orders.getAllocatedCount());
    }

    @Test
    void right_click_should_make_flyer_follow_target_but_move_to_burrowed_target() {
        Game game = createState(ScrUnitIds.ZERG_OVERLORD);
        UnitCommands commands = game.getUnitCommands();
        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit unit = unitPool.getUnit(WORKER_INDEX);
        UnitPool targetUnitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(targetUnitPool);

        Unit target = targetUnitPool.getUnit(RESOURCE_INDEX);

        target.setUnitId(ScrUnitIds.ZERG_DRONE);
        target.setOwnerIndex(0);
        target.setStatus(UnitStatusFlags.COMPLETED);

        Assertions.assertTrue(commands.rightClick(0, CLICK_POSITION, CLICK_POSITION, target, ScrUnitIds.NONE, false, INSTRUCTION_LIMIT));

        Assertions.assertEquals(FOLLOW_ORDER, unit.getPrimaryOrder());
        Assertions.assertSame(target, unit.getOrderTargetUnit());
        Assertions.assertEquals(RESOURCE_POSITION, unit.getOrderTargetX());

        target.setStatus(UnitStatusFlags.COMPLETED | UnitStatusFlags.BURROWED);

        Assertions.assertTrue(commands.rightClick(0, CLICK_POSITION, CLICK_POSITION, target, ScrUnitIds.NONE, false, INSTRUCTION_LIMIT));

        Assertions.assertEquals(MOVE_ORDER, unit.getPrimaryOrder());

        Assertions.assertFalse(commands.rightClick(0, CLICK_POSITION, CLICK_POSITION, unit, ScrUnitIds.NONE, false, INSTRUCTION_LIMIT));

        Assertions.assertSame(target, unit.getOrderTargetUnit());
    }

    @Test
    void right_click_should_return_actual_cargo_or_follow_owned_depot_when_empty() {
        for (int workerType : new int[]{ScrUnitIds.ZERG_DRONE, ScrUnitIds.PROTOSS_PROBE}) {
            for (int carrying : new int[]{0, CarryingFlags.GAS, CarryingFlags.MINERALS, CarryingFlags.RESOURCES}) {
                Game game = createState(workerType);
                UnitCommands commands = game.getUnitCommands();
                UnitPool workerPool = game.getUnitSystem().getUnitPool();

                Assertions.assertNotNull(workerPool);

                Unit worker = workerPool.getUnit(WORKER_INDEX);
                UnitPool depotUnitPool = game.getUnitSystem().getUnitPool();

                Assertions.assertNotNull(depotUnitPool);

                Unit depot = depotUnitPool.getUnit(RESOURCE_INDEX);

                depot.setUnitId(ScrUnitIds.ZERG_HATCHERY);
                depot.setOwnerIndex(0);
                depot.setStatus(UnitStatusFlags.COMPLETED | UnitStatusFlags.GROUNDED_BUILDING);
                worker.setCarryingFlags(carrying);

                Assertions.assertTrue(commands.rightClick(0, CLICK_POSITION, CLICK_POSITION, depot, ScrUnitIds.NONE, false, INSTRUCTION_LIMIT));

                int expectedOrder = carrying == 0 ? FOLLOW_ORDER
                                                  : (carrying & CarryingFlags.MINERALS) != 0 ? RETURN_MINERALS_ORDER : RETURN_GAS_ORDER;

                Assertions.assertEquals(expectedOrder, worker.getPrimaryOrder());
                Assertions.assertSame(depot, worker.getOrderTargetUnit());
                Assertions.assertEquals(carrying, worker.getCarryingFlags());
            }
        }
    }

    @Test
    void right_click_should_move_powerup_carrier_to_minerals_without_gathering() {
        Game game = createState(ScrUnitIds.ZERG_DRONE);
        UnitPool workerPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(workerPool);

        Unit worker = workerPool.getUnit(WORKER_INDEX);

        worker.setCarryingFlags(CarryingFlags.POWERUP);

        Assertions.assertTrue(game.getUnitCommands().rightClick(0,
                                                                CLICK_POSITION, CLICK_POSITION,
                                                                game.getUnitSystem().getUnitPool().getUnit(RESOURCE_INDEX),
                                                                ScrUnitIds.NONE,
                                                                false,
                                                                INSTRUCTION_LIMIT));

        Assertions.assertEquals(MOVE_ORDER, worker.getPrimaryOrder());
        Assertions.assertEquals(CARRIED_POWERUP_FLAGS, worker.getCarryingFlags());
        Assertions.assertFalse(worker.isResourceBeingGathered());
    }

    @Test
    void retained_dying_selection_rejects_gather_and_rally_commands() {
        Game game = createState(ScrUnitIds.ZERG_DRONE);
        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit unit = unitPool.getUnit(WORKER_INDEX);

        unit.setPrimaryOrder(ScrOrderIds.DIE);
        unit.setPrimaryOrderState(Unit.DIE_STATE_DESTROYED);

        Assertions.assertFalse(game.getUnitCommands().rightClick(0,
                                                                 WORKER_POSITION, WORKER_POSITION,
                                                                 game.getUnitSystem().getUnitPool().getUnit(RESOURCE_INDEX),
                                                                 ScrUnitIds.NONE,
                                                                 false,
                                                                 INSTRUCTION_LIMIT));

        unit.setUnitId(ScrUnitIds.ZERG_HATCHERY);
        unit.setStatus(UnitStatusFlags.GROUNDED_BUILDING);

        Assertions.assertFalse(game.getUnitCommands().rally(0, ScrOrderIds.RALLY_POINT_UNIT, WORKER_POSITION, WORKER_POSITION, null));

        Assertions.assertNull(unit.getRallyTargetUnit());
        Assertions.assertEquals(DIE_ORDER, unit.getPrimaryOrder());
        Assertions.assertEquals(DESTROYED_DIE_STATE, unit.getPrimaryOrderState());

        OrderPool orders = game.getOrderPool();

        Assertions.assertNotNull(orders);

        Assertions.assertEquals(0, orders.getAllocatedCount());
    }

    @Test
    void order_should_issue_attack_unit_order_when_attack_default_has_target() {
        Assertions.assertNotNull(gameData);

        Game game = createDuelGame(ScrUnitIds.PROTOSS_PROBE, ScrUnitIds.PROTOSS_PROBE);
        UnitCommands commands = game.getUnitCommands();
        UnitPool attackerPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(attackerPool);

        Unit attacker = attackerPool.getUnit(CombatGameScenario.FIRST_UNIT_INDEX);
        UnitPool targetPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(targetPool);

        Unit target = targetPool.getUnit(CombatGameScenario.SECOND_UNIT_INDEX);
        int randomCount = game.getRandom().getTotalRandomCounts();
        int noUnitType = ScrUnitIds.NONE;

        Assertions.assertTrue(commands.order(0,
                                             ScrOrderIds.ATTACK_DEFAULT,
                                             FAR_POSITION, FAR_POSITION,
                                             target,
                                             noUnitType,
                                             false,
                                             INSTRUCTION_LIMIT));
        Assertions.assertEquals(gameData.getUnits().get("Attack Unit", ScrUnitIds.PROTOSS_PROBE), attacker.getPrimaryOrder());
        Assertions.assertSame(target, attacker.getOrderTargetUnit());

        Sprite targetSprite = target.getSpriteOrNull();

        Assertions.assertNotNull(targetSprite);
        Assertions.assertEquals(targetSprite.getX(), attacker.getOrderTargetX());
        Assertions.assertEquals(NO_UNIT_TYPE, attacker.getOrderTargetUnitType());
        Assertions.assertEquals(COMMANDED_ACTION_FLAG, attacker.getUserActionFlags() & Unit.USER_ACTION_COMMANDED);
        Assertions.assertEquals(randomCount, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void order_should_use_attack_move_without_target_and_heal_move_for_medic() {
        Assertions.assertNotNull(gameData);

        Game game = createDuelGame(ScrUnitIds.ZERG_MUTALISK, ScrUnitIds.PROTOSS_PROBE);
        UnitCommands commands = game.getUnitCommands();
        UnitPool attackerPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(attackerPool);

        Unit attacker = attackerPool.getUnit(CombatGameScenario.FIRST_UNIT_INDEX);
        int noUnitType = ScrUnitIds.NONE;

        Assertions.assertTrue(commands.order(0,
                                             ScrOrderIds.ATTACK_DEFAULT,
                                             ATTACK_MOVE_X, ATTACK_MOVE_Y,
                                             null,
                                             noUnitType,
                                             false,
                                             INSTRUCTION_LIMIT));
        Assertions.assertEquals(gameData.getUnits().get("Attack Move", ScrUnitIds.ZERG_MUTALISK), attacker.getPrimaryOrder());
        Assertions.assertNull(attacker.getOrderTargetUnit());
        Assertions.assertEquals(ATTACK_MOVE_X, attacker.getOrderTargetX());
        Assertions.assertEquals(ATTACK_MOVE_Y, attacker.getOrderTargetY());

        attacker.setUnitId(ScrUnitIds.TERRAN_MEDIC);

        Assertions.assertTrue(commands.order(0,
                                             ScrOrderIds.ATTACK_DEFAULT,
                                             ATTACK_MOVE_X, ATTACK_MOVE_Y,
                                             null,
                                             noUnitType,
                                             false,
                                             INSTRUCTION_LIMIT));
        Assertions.assertEquals(HEAL_MOVE_ORDER, attacker.getPrimaryOrder());
    }

    @Test
    void order_should_reject_excluded_orders_out_of_bounds_targets_and_ineligible_units() {
        Game game = createDuelGame(ScrUnitIds.PROTOSS_PROBE, ScrUnitIds.PROTOSS_PROBE);
        UnitCommands commands = game.getUnitCommands();
        UnitPool attackerPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(attackerPool);

        Unit attacker = attackerPool.getUnit(CombatGameScenario.FIRST_UNIT_INDEX);
        UnitPool targetPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(targetPool);

        Unit target = targetPool.getUnit(CombatGameScenario.SECOND_UNIT_INDEX);
        int order = attacker.getPrimaryOrder();
        int noUnitType = ScrUnitIds.NONE;

        Assertions.assertFalse(commands.order(0,
                                              ScrOrderIds.PLACE_PROTOSS_BUILDING,
                                              NEAR_POSITION, NEAR_POSITION,
                                              null,
                                              noUnitType,
                                              false,
                                              INSTRUCTION_LIMIT));
        Assertions.assertFalse(commands.order(0,
                                              ScrOrderIds.ATTACK_DEFAULT,
                                              OUT_OF_MAP_X, NEAR_POSITION,
                                              target,
                                              noUnitType,
                                              false,
                                              INSTRUCTION_LIMIT));
        Assertions.assertFalse(commands.order(0,
                                              ScrOrderIds.ATTACK_DEFAULT,
                                              NEAR_POSITION, NEAR_POSITION,
                                              attacker,
                                              noUnitType,
                                              false,
                                              INSTRUCTION_LIMIT));

        attacker.setStatus(attacker.getStatus() & ~UnitStatusFlags.COMPLETED);

        Assertions.assertFalse(commands.order(0,
                                              ScrOrderIds.ATTACK_DEFAULT,
                                              NEAR_POSITION, NEAR_POSITION,
                                              target,
                                              noUnitType,
                                              false,
                                              INSTRUCTION_LIMIT));

        attacker.setStatus(attacker.getStatus() | UnitStatusFlags.COMPLETED);
        attacker.setLockdownTimer(1);

        Assertions.assertFalse(commands.order(0,
                                              ScrOrderIds.ATTACK_DEFAULT,
                                              NEAR_POSITION, NEAR_POSITION,
                                              target,
                                              noUnitType,
                                              false,
                                              INSTRUCTION_LIMIT));
        Assertions.assertFalse(commands.order(SECOND_PLAYER,
                                              ScrOrderIds.ATTACK_DEFAULT,
                                              NEAR_POSITION, NEAR_POSITION,
                                              target,
                                              noUnitType,
                                              false,
                                              INSTRUCTION_LIMIT));
        Assertions.assertEquals(order, attacker.getPrimaryOrder());

        OrderPool emptyOrderPool = game.getOrderPool();

        Assertions.assertNotNull(emptyOrderPool);
        Assertions.assertEquals(0, emptyOrderPool.getAllocatedCount());
    }

    @Test
    void right_click_should_issue_attack_unit_order_on_enemy() {
        Assertions.assertNotNull(gameData);

        Game game = createDuelGame(ScrUnitIds.PROTOSS_PROBE, ScrUnitIds.PROTOSS_PROBE);
        UnitCommands commands = game.getUnitCommands();
        UnitPool attackerPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(attackerPool);

        Unit attacker = attackerPool.getUnit(CombatGameScenario.FIRST_UNIT_INDEX);
        UnitPool targetPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(targetPool);

        Unit target = targetPool.getUnit(CombatGameScenario.SECOND_UNIT_INDEX);

        Assertions.assertTrue(commands.rightClick(0, NEAR_POSITION, NEAR_POSITION, target, ScrUnitIds.NONE, false, INSTRUCTION_LIMIT));
        Assertions.assertEquals(gameData.getUnits().get("Attack Unit", ScrUnitIds.PROTOSS_PROBE), attacker.getPrimaryOrder());
        Assertions.assertSame(target, attacker.getOrderTargetUnit());
    }

    @Test
    void return_cargo_and_hold_position_issue_orders_to_eligible_selected_units() {
        Game game = createDuelGame(ScrUnitIds.PROTOSS_PROBE, ScrUnitIds.PROTOSS_PROBE);
        UnitCommands commands = game.getUnitCommands();
        UnitPool workerPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(workerPool);

        Unit worker = workerPool.getUnit(CombatGameScenario.FIRST_UNIT_INDEX);

        Assertions.assertFalse(commands.returnCargo(0, false, INSTRUCTION_LIMIT));

        worker.setCarryingFlags(CarryingFlags.GAS);

        Assertions.assertTrue(commands.returnCargo(0, false, INSTRUCTION_LIMIT));
        Assertions.assertEquals(RETURN_GAS_ORDER, worker.getPrimaryOrder());

        worker.setCarryingFlags(CarryingFlags.RESOURCES);

        Assertions.assertTrue(commands.returnCargo(0, false, INSTRUCTION_LIMIT));
        Assertions.assertEquals(RETURN_MINERALS_ORDER, worker.getPrimaryOrder());

        Assertions.assertTrue(commands.holdPosition(0, false, INSTRUCTION_LIMIT));
        Assertions.assertEquals(HOLD_POSITION_ORDER, worker.getPrimaryOrder());

        worker.setLockdownTimer(1);

        Assertions.assertFalse(commands.holdPosition(0, false, INSTRUCTION_LIMIT));
    }

    @Test
    void right_click_should_order_parent_tank_but_reject_selected_enemy_turret() {
        Assertions.assertNotNull(gameData);

        // The subunit receives the order before the owner of the selected turret is checked.
        Game game = CombatGameScenario.createGame(gameData);

        CombatGameScenario.initializeWorld(game, TURRET_UNIT_CAPACITY, TURRET_SPRITE_CAPACITY, TURRET_IMAGE_CAPACITY);
        CombatGameScenario.initializeCombat(game, gameData);
        CombatGameScenario.createUnit(game, gameData, TANK_INDEX, ScrUnitIds.TERRAN_SIEGE_TANK_TANK_MODE, TANK_POSITION_X, UNIT_POSITION_Y, 1);
        CombatGameScenario.createUnit(game, gameData,
                                      TURRET_INDEX, ScrUnitIds.TERRAN_SIEGE_TANK_TANK_MODE_TURRET,
                                      TANK_POSITION_X, UNIT_POSITION_Y,
                                      1);

        UnitPool parentLinkPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(parentLinkPool);

        Unit parentToLink = parentLinkPool.getUnit(TANK_INDEX);
        UnitPool parentSubunitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(parentSubunitPool);

        parentToLink.setSubunit(parentSubunitPool.getUnit(TURRET_INDEX));

        UnitPool turretLinkPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(turretLinkPool);

        Unit turretToLink = turretLinkPool.getUnit(TURRET_INDEX);
        UnitPool turretSubunitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(turretSubunitPool);

        turretToLink.setSubunit(turretSubunitPool.getUnit(TANK_INDEX));
        CombatGameScenario.createUnit(game, gameData, MARINE_INDEX, ScrUnitIds.TERRAN_MARINE, MARINE_POSITION_X, UNIT_POSITION_Y, 0);

        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        Unit tank = pool.getUnit(TANK_INDEX);
        Unit turret = tank.getSubunit();

        Assertions.assertNotNull(turret);

        Unit marine = pool.getUnit(MARINE_INDEX);
        UnitSelection selection = new UnitSelection(pool, false, TEAM_SIZE, LOCAL_PLAYER_INDEX);

        game.setSelection(selection);
        selection.execute(game,
                          new SelectionRequest(SelectionRequest.Operation.REPLACE,
                                               HANDLE_WIDTH_BYTES,
                                               new long[]{Integer.toUnsignedLong(pool.getHandle(turret.getIndex()))}),
                          0);

        Assertions.assertSame(turret, selection.getSelectedUnit(0, 0));
        Assertions.assertNull(tank.getOrderTargetUnit());
        Assertions.assertFalse(game.getUnitCommands().rightClick(0,
                                                                 MARINE_POSITION_X, UNIT_POSITION_Y,
                                                                 marine,
                                                                 ScrUnitIds.NONE,
                                                                 false,
                                                                 INSTRUCTION_LIMIT),
                               "the unowned turret itself is rejected by the owner check");
        Assertions.assertSame(marine, tank.getOrderTargetUnit(), "the enemy tank receives the attack order through the subunit link");
    }
}
