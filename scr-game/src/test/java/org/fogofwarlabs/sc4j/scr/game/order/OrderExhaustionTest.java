package org.fogofwarlabs.sc4j.scr.game.order;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.type.ScrOrderTypeView;
import org.fogofwarlabs.sc4j.scr.game.movement.UnitTargetMovement;
import org.fogofwarlabs.sc4j.scr.game.order.OrderPool.Order;
import org.fogofwarlabs.sc4j.scr.game.presentation.CommandErrorEvents;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;

/**
 * Checks allocation-failure status events, queue preservation, and the insertion limits and capacity tests of internal orders.
 */
@RequiresInstallation
final class OrderExhaustionTest {
    /**
     * The identifier of the status text of a failed order allocation.
     */
    private static final int TEXT_ORDER_ALLOCATION_FAILED = 872;
    /**
     * The race of a status event that names no race.
     */
    private static final int NO_EVENT_RACE = -2;
    /**
     * The highlight value of an order type without a highlight.
     */
    private static final int NO_HIGHLIGHT = 0xFFFF;
    /**
     * The order-done user action flag, which a patrol start records.
     */
    private static final int ORDER_DONE_ACTION_FLAG = 0x1;

    private static final int UNIT_POSITION_X = 3808;
    private static final int FILLER_POSITION_X = 3744;
    private static final int UNIT_POSITION_Y = 2480;

    /**
     * The lowest highlighted order count at which a computer unit skips a front insertion into its nonempty queue.
     */
    private static final int COMPUTER_BLOCKED_COUNT = 9;
    /**
     * The order states of a running patrol and of a finished unburrowing.
     */
    private static final int PATROL_RUNNING_STATE = 1;
    private static final int UNBURROW_FINISHED_STATE = 3;
    /**
     * The order allocations that fail in the exhausted-pool test.
     */
    private static final int ALLOCATION_ATTEMPTS = 2;

    /**
     * The raw order identifier, target type and target position, of which a queued order keeps the low BYTE or WORD.
     */
    private static final int RAW_ORDER_IDENTIFIER = 0x198;
    private static final int RAW_TARGET_TYPE = 0x100E4;
    private static final int RAW_TARGET_POSITION_X = -1;
    private static final int RAW_TARGET_POSITION_Y = 0x10002;

    private static final int BYTE_MASK = 0xFF;
    private static final int WORD_MASK = 0xFFFF;

    /**
     * The Harvest1 order.
     */
    private static final int HARVEST_ORDER = 79;
    /**
     * The Patrol order.
     */
    private static final int PATROL_ORDER = 152;
    /**
     * The order that removes the overlay after a shield recharge.
     */
    private static final int RECHARGE_REMOVE_OVERLAY_ORDER = 91;
    /**
     * The Reset Harvest Collision order.
     */
    private static final int RESET_HARVEST_COLLISION_ORDER = 151;
    /**
     * The Unburrowing order.
     */
    private static final int UNBURROWING_ORDER = 118;
    /**
     * The Zerg unit morph order.
     */
    private static final int ZERG_UNIT_MORPH_ORDER = 42;

    private static @Nullable GameData gameData;
    private static @Nullable ReplayRuntime runtime;

    @BeforeAll
    static void loadGameData() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
    }

    @AfterAll
    static void closeGameData() {
        GameData installedData = gameData;

        if (installedData != null) {
            installedData.close();
        }

        gameData = null;
    }

    /**
     * Creates a {@link Unit} instance as a completed unit without orders, adopted by the computer when the computer owns it.
     *
     * @param game     the running game
     * @param unitType the unit type
     * @param owner    the owning player
     * @return the created {@link Unit} instance
     */
    private static @NotNull Unit createOrderUnit(@NotNull Game game, int unitType, int owner) {
        Unit unit = RunningGameScenario.createUnit(game, unitType, UNIT_POSITION_X, UNIT_POSITION_Y, owner);

        if (owner == RunningGameScenario.COMPUTER && unit.getComputerRecord() == null) {
            game.getComputerUnits().notifyAdopted(unit, RunningGameScenario.INSTRUCTION_LIMIT);
        }

        Assertions.assertEquals(owner == RunningGameScenario.COMPUTER, unit.getComputerRecord() != null);
        Assertions.assertNull(unit.getFirstOrder(), "The genuine completed unit has no pending order before setup.");
        Assertions.assertNull(unit.getLastOrder());

        return unit;
    }

    private static void fillOrderPool(@NotNull Game game) {
        fillOrderPool(game, false);
    }

    /**
     * Queues filler orders on a separate unit until the order pool is exhausted, or only until it is nearly full when {@code keepsFreeOrders} is set.
     *
     * @param game            the running game
     * @param keepsFreeOrders whether the filling stops once the pool is nearly full
     */
    private static void fillOrderPool(@NotNull Game game, boolean keepsFreeOrders) {
        OrderPool pool = game.getOrderPool();

        Assertions.assertNotNull(pool);

        Unit filler = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_MARINE, FILLER_POSITION_X, UNIT_POSITION_Y, RunningGameScenario.HUMAN);
        ScrOrderTypeView orderTypes = game.requireGameData().getOrderTypes();

        while (pool.getFirstFreeOrder() != null && !(keepsFreeOrders && pool.isNearlyFull())) {
            Order order = pool.allocate(ScrOrderIds.NOTHING, ScrUnitIds.NONE, 0, 0, null);

            Assertions.assertNotNull(order);

            pool.enqueue(filler, order, null, orderTypes);
        }

        if (keepsFreeOrders) {
            Assertions.assertTrue(pool.isNearlyFull());
            Assertions.assertNotNull(pool.getFirstFreeOrder());
        } else {
            Assertions.assertEquals(pool.getCapacity(), pool.getAllocatedCount());
        }
    }

    private static void assertAllocationFailure(@NotNull CommandErrorEvents.Event event, int owner) {
        Assertions.assertEquals(owner, event.getPlayer());
        Assertions.assertEquals(TEXT_ORDER_ALLOCATION_FAILED, event.getTextIdentifier());
        Assertions.assertNull(event.getTextKey());
        Assertions.assertEquals(CommandErrorEvents.Sound.NONE, event.getSound());
        Assertions.assertEquals(NO_EVENT_RACE, event.getRace());
        Assertions.assertEquals(0, event.getPoolIndex());
    }


    @BeforeEach
    void startGame() throws IOException {
        GameData installedData = gameData;

        Assertions.assertNotNull(installedData);

        runtime = RunningGameScenario.startLiveGame(installedData);
    }

    @AfterEach
    void releaseGame() {
        runtime = null;
    }

    @Test
    void exhausted_order_pool_preserves_the_queue_and_records_owner_status_without_random_use() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit unit = createOrderUnit(game, ScrUnitIds.TERRAN_MARINE, RunningGameScenario.HUMAN);
            ScrOrderSystem assignment = game.getOrders();

            assignment.queueBack(unit, ScrOrderIds.MOVE, null, UNIT_POSITION_X, UNIT_POSITION_Y, ScrUnitIds.NONE);

            Order first = unit.getFirstOrder();
            OrderPool pool = game.getOrderPool();

            Assertions.assertNotNull(first);
            Assertions.assertNotNull(pool);

            fillOrderPool(game);

            CommandErrorEvents errors = game.getInterfaceLink().getCommandErrorEvents();
            int eventCount = errors.getCount();
            int highlightedCount = unit.getHighlightedOrderCount();
            long randomCount = game.getRandom().getTotalRandomCounts();

            assignment.queueBack(unit, ScrOrderIds.PATROL, null, 0, 0, ScrUnitIds.NONE);
            assignment.queueFront(unit, ScrOrderIds.PATROL, null, 0, 0, ScrUnitIds.NONE);

            Assertions.assertEquals(eventCount + ALLOCATION_ATTEMPTS, errors.getCount());
            assertAllocationFailure(errors.get(eventCount), unit.getOwnerIndex());
            assertAllocationFailure(errors.get(eventCount + 1), unit.getOwnerIndex());
            Assertions.assertSame(first, unit.getFirstOrder());
            Assertions.assertSame(first, unit.getLastOrder());
            Assertions.assertNull(first.getPrevious());
            Assertions.assertNull(first.getNext());
            Assertions.assertSame(unit, first.getOwner());
            Assertions.assertEquals(highlightedCount, unit.getHighlightedOrderCount());
            Assertions.assertEquals(pool.getCapacity(), pool.getAllocatedCount());
            Assertions.assertEquals(randomCount, game.getRandom().getTotalRandomCounts());
        });
    }

    @Test
    void patrol_turn_reports_failed_return_leg_allocation_to_its_owner() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit unit = createOrderUnit(game, ScrUnitIds.TERRAN_MARINE, RunningGameScenario.HUMAN);
            Sprite sprite = unit.getSpriteOrNull();

            Assertions.assertNotNull(sprite);
            Assertions.assertFalse(game.getRulesVersion().isLegacyReplay());
            Assertions.assertFalse(game.getRulesVersion().allowsOrderAtCapacity(ScrOrderIds.PATROL));
            Assertions.assertEquals(0, unit.getStatus() & UnitStatusFlags.IMMOVABLE);

            unit.setPrimaryOrder(ScrOrderIds.PATROL);
            unit.setPrimaryOrderState(PATROL_RUNNING_STATE);
            unit.setOrderTargetUnit(null);
            unit.setAutomaticTargetUnit(null);
            unit.setOrderTargetX(sprite.getX());
            unit.setOrderTargetY(sprite.getY());
            unit.setMovementDestinationX(sprite.getX());
            unit.setMovementDestinationY(sprite.getY());
            unit.setCooldownTimers(unit.getCooldownTimers() & ~Unit.ORDER_TIMER_MASK);

            Assertions.assertTrue(UnitTargetMovement.isAtMovementTarget(unit));

            fillOrderPool(game);

            CommandErrorEvents errors = game.getInterfaceLink().getCommandErrorEvents();
            int eventCount = errors.getCount();
            long randomCount = game.getRandom().getTotalRandomCounts();

            game.getUnitAttack().updatePatrol(unit, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(PATROL_ORDER, unit.getPrimaryOrder());
            Assertions.assertEquals(PATROL_RUNNING_STATE, unit.getPrimaryOrderState());
            Assertions.assertNull(unit.getFirstOrder());
            Assertions.assertNull(unit.getLastOrder());
            Assertions.assertEquals(eventCount + 1, errors.getCount());
            assertAllocationFailure(errors.get(eventCount), unit.getOwnerIndex());
            Assertions.assertEquals(randomCount, game.getRandom().getTotalRandomCounts());
        });
    }

    @Test
    void patrol_start_records_user_action_before_capacity_rejection_and_return_leg_failure() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit unit = createOrderUnit(game, ScrUnitIds.TERRAN_MARINE, RunningGameScenario.HUMAN);

            Assertions.assertFalse(game.getRulesVersion().isReplay());
            Assertions.assertEquals(0, unit.getUserActionFlags() & ORDER_DONE_ACTION_FLAG);

            unit.setPrimaryOrder(ScrOrderIds.PATROL);
            unit.setPrimaryOrderState(0);
            unit.setOrderTargetUnit(null);
            unit.setOrderTargetX(UNIT_POSITION_X);
            unit.setOrderTargetY(UNIT_POSITION_Y);

            fillOrderPool(game);

            CommandErrorEvents errors = game.getInterfaceLink().getCommandErrorEvents();
            int eventCount = errors.getCount();
            int actionFlags = unit.getUserActionFlags();
            long randomCount = game.getRandom().getTotalRandomCounts();

            game.getUnitAttack().updatePatrol(unit, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(actionFlags | ORDER_DONE_ACTION_FLAG, unit.getUserActionFlags());
            Assertions.assertEquals(PATROL_ORDER, unit.getPrimaryOrder());
            Assertions.assertEquals(0, unit.getPrimaryOrderState());
            Assertions.assertNull(unit.getFirstOrder());
            Assertions.assertNull(unit.getLastOrder());
            Assertions.assertEquals(eventCount + 1, errors.getCount());
            assertAllocationFailure(errors.get(eventCount), unit.getOwnerIndex());
            Assertions.assertEquals(randomCount, game.getRandom().getTotalRandomCounts());
        });
    }

    @Test
    void queue_replacing_same_orders_should_remove_matching_tail_before_rejecting_capacity() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit unit = createOrderUnit(game, ScrUnitIds.TERRAN_MARINE, RunningGameScenario.HUMAN);
            ScrOrderSystem assignment = game.getOrders();

            unit.setPrimaryOrder(ScrOrderIds.PLAYER_GUARD);
            assignment.queueBack(unit, ScrOrderIds.PATROL, null, 0, 0, ScrUnitIds.NONE);

            Order first = unit.getFirstOrder();
            OrderPool pool = game.getOrderPool();

            Assertions.assertNotNull(first);
            Assertions.assertNotNull(pool);
            Assertions.assertFalse(game.getRulesVersion().isReplay());

            fillOrderPool(game);

            CommandErrorEvents errors = game.getInterfaceLink().getCommandErrorEvents();
            int eventCount = errors.getCount();
            long randomCount = game.getRandom().getTotalRandomCounts();

            assignment.queueReplacingSameOrders(unit, ScrOrderIds.PATROL, null, 0, 0, ScrUnitIds.NONE);

            Assertions.assertNull(unit.getFirstOrder());
            Assertions.assertNull(unit.getLastOrder());
            Assertions.assertNull(first.getOwner());
            Assertions.assertEquals(pool.getCapacity() - 1, pool.getAllocatedCount());
            Assertions.assertEquals(eventCount, errors.getCount());
            Assertions.assertEquals(randomCount, game.getRandom().getTotalRandomCounts());
        });
    }

    @Test
    void recharge_overlay_removal_skips_idle_allocation_without_status_at_modern_capacity() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit unit = createOrderUnit(game, ScrUnitIds.PROTOSS_ZEALOT, RunningGameScenario.HUMAN);

            unit.setPrimaryOrder(ScrOrderIds.RECHARGE_SHIELDS_UNIT_REMOVE_OVERLAY);

            Assertions.assertFalse(game.getRulesVersion().isReplay());
            Assertions.assertFalse(game.getRulesVersion().allowsOrderAtCapacity(unit.getPrimaryOrder()));

            fillOrderPool(game);

            CommandErrorEvents errors = game.getInterfaceLink().getCommandErrorEvents();
            int eventCount = errors.getCount();
            long randomCount = game.getRandom().getTotalRandomCounts();

            game.getUnitShieldBattery().updateRemoveOverlay(unit, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertNull(unit.getFirstOrder());
            Assertions.assertNull(unit.getLastOrder());
            Assertions.assertEquals(RECHARGE_REMOVE_OVERLAY_ORDER, unit.getPrimaryOrder());
            Assertions.assertEquals(eventCount, errors.getCount());
            Assertions.assertEquals(randomCount, game.getRandom().getTotalRandomCounts());
        });
    }

    @Test
    void finished_unburrowing_skips_idle_allocation_without_status_at_modern_capacity() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit unit = createOrderUnit(game, ScrUnitIds.ZERG_HYDRALISK, RunningGameScenario.HUMAN);

            unit.setPrimaryOrder(ScrOrderIds.UNBURROWING);
            unit.setPrimaryOrderState(UNBURROW_FINISHED_STATE);

            Assertions.assertFalse(game.getRulesVersion().isReplay());
            Assertions.assertFalse(game.getRulesVersion().allowsOrderAtCapacity(unit.getPrimaryOrder()));

            fillOrderPool(game);

            CommandErrorEvents errors = game.getInterfaceLink().getCommandErrorEvents();
            int eventCount = errors.getCount();
            long randomCount = game.getRandom().getTotalRandomCounts();

            game.getUnitBurrowing().updateUnburrowing(unit, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertNull(unit.getFirstOrder());
            Assertions.assertNull(unit.getLastOrder());
            Assertions.assertEquals(UNBURROWING_ORDER, unit.getPrimaryOrder());
            Assertions.assertEquals(UNBURROW_FINISHED_STATE, unit.getPrimaryOrderState());
            Assertions.assertEquals(eventCount, errors.getCount());
            Assertions.assertEquals(randomCount, game.getRandom().getTotalRandomCounts());
        });
    }

    @Test
    void recharge_overlay_removal_skips_the_idle_order_when_the_order_pool_is_nearly_full() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit unit = createOrderUnit(game, ScrUnitIds.PROTOSS_ZEALOT, RunningGameScenario.HUMAN);
            OrderPool pool = game.getOrderPool();

            Assertions.assertNotNull(pool);

            unit.setPrimaryOrder(ScrOrderIds.RECHARGE_SHIELDS_UNIT_REMOVE_OVERLAY);

            Assertions.assertFalse(game.getRulesVersion().isReplay());
            Assertions.assertFalse(game.getRulesVersion().allowsOrderAtCapacity(unit.getPrimaryOrder()));

            fillOrderPool(game, true);

            CommandErrorEvents errors = game.getInterfaceLink().getCommandErrorEvents();
            int eventCount = errors.getCount();
            int allocatedCount = pool.getAllocatedCount();
            long randomCount = game.getRandom().getTotalRandomCounts();

            game.getUnitShieldBattery().updateRemoveOverlay(unit, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertNull(unit.getFirstOrder());
            Assertions.assertNull(unit.getLastOrder());
            Assertions.assertEquals(RECHARGE_REMOVE_OVERLAY_ORDER, unit.getPrimaryOrder());
            Assertions.assertEquals(allocatedCount, pool.getAllocatedCount());
            Assertions.assertEquals(eventCount, errors.getCount());
            Assertions.assertEquals(randomCount, game.getRandom().getTotalRandomCounts());
        });
    }

    @Test
    void finished_unburrowing_skips_the_idle_order_when_the_order_pool_is_nearly_full() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit unit = createOrderUnit(game, ScrUnitIds.ZERG_HYDRALISK, RunningGameScenario.HUMAN);
            OrderPool pool = game.getOrderPool();

            Assertions.assertNotNull(pool);

            unit.setPrimaryOrder(ScrOrderIds.UNBURROWING);
            unit.setPrimaryOrderState(UNBURROW_FINISHED_STATE);

            Assertions.assertFalse(game.getRulesVersion().isReplay());
            Assertions.assertFalse(game.getRulesVersion().allowsOrderAtCapacity(unit.getPrimaryOrder()));

            fillOrderPool(game, true);

            CommandErrorEvents errors = game.getInterfaceLink().getCommandErrorEvents();
            int eventCount = errors.getCount();
            int allocatedCount = pool.getAllocatedCount();
            long randomCount = game.getRandom().getTotalRandomCounts();

            game.getUnitBurrowing().updateUnburrowing(unit, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertNull(unit.getFirstOrder());
            Assertions.assertNull(unit.getLastOrder());
            Assertions.assertEquals(UNBURROWING_ORDER, unit.getPrimaryOrder());
            Assertions.assertEquals(UNBURROW_FINISHED_STATE, unit.getPrimaryOrderState());
            Assertions.assertEquals(allocatedCount, pool.getAllocatedCount());
            Assertions.assertEquals(eventCount, errors.getCount());
            Assertions.assertEquals(randomCount, game.getRandom().getTotalRandomCounts());
        });
    }

    @Test
    void harvest_collision_reset_reports_failure_and_retains_the_busy_harvest_queue() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit worker = createOrderUnit(game, ScrUnitIds.TERRAN_SCV, RunningGameScenario.HUMAN);

            game.getOrders().queueBack(worker, ScrOrderIds.HARVEST1, null, 0, 0, ScrUnitIds.NONE);
            worker.setPrimaryOrder(ScrOrderIds.RESET_HARVEST_COLLISION);
            worker.setStatus(worker.getStatus() | UnitStatusFlags.ISCRIPT_NO_BREAK | UnitStatusFlags.ORDER_NOT_INTERRUPTIBLE);

            Order first = worker.getFirstOrder();

            Assertions.assertNotNull(first);

            fillOrderPool(game);

            CommandErrorEvents errors = game.getInterfaceLink().getCommandErrorEvents();
            int eventCount = errors.getCount();
            long randomCount = game.getRandom().getTotalRandomCounts();

            game.getUnitHarvesting().resetHarvestCollision(worker, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertSame(first, worker.getFirstOrder());
            Assertions.assertSame(first, worker.getLastOrder());
            Assertions.assertNull(first.getNext());
            Assertions.assertEquals(HARVEST_ORDER, first.getOrderId());
            Assertions.assertEquals(0, worker.getStatus() & UnitStatusFlags.ORDER_NOT_INTERRUPTIBLE);
            Assertions.assertNotEquals(0, worker.getStatus() & UnitStatusFlags.ISCRIPT_NO_BREAK);
            Assertions.assertEquals(eventCount + 1, errors.getCount());
            assertAllocationFailure(errors.get(eventCount), worker.getOwnerIndex());
            Assertions.assertEquals(randomCount, game.getRandom().getTotalRandomCounts());
        });
    }

    @Test
    void harvest_collision_reset_appends_after_the_busy_harvest_when_capacity_remains() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit worker = createOrderUnit(game, ScrUnitIds.TERRAN_SCV, RunningGameScenario.HUMAN);

            game.getOrders().queueBack(worker, ScrOrderIds.HARVEST1, null, 0, 0, ScrUnitIds.NONE);
            worker.setPrimaryOrder(ScrOrderIds.RESET_HARVEST_COLLISION);
            worker.setStatus(worker.getStatus() | UnitStatusFlags.ISCRIPT_NO_BREAK);

            Order first = worker.getFirstOrder();

            Assertions.assertNotNull(first);

            CommandErrorEvents errors = game.getInterfaceLink().getCommandErrorEvents();
            int eventCount = errors.getCount();
            long randomCount = game.getRandom().getTotalRandomCounts();

            game.getUnitHarvesting().resetHarvestCollision(worker, RunningGameScenario.INSTRUCTION_LIMIT);

            Order reset = first.getNext();

            Assertions.assertNotNull(reset);
            Assertions.assertSame(first, worker.getFirstOrder());
            Assertions.assertSame(reset, worker.getLastOrder());
            Assertions.assertSame(first, reset.getPrevious());
            Assertions.assertSame(worker, reset.getOwner());
            Assertions.assertEquals(RESET_HARVEST_COLLISION_ORDER, reset.getOrderId());
            Assertions.assertEquals(eventCount, errors.getCount());
            Assertions.assertEquals(randomCount, game.getRandom().getTotalRandomCounts());
        });
    }

    @ParameterizedTest
    @CsvSource({"false, 9, false, true", "true, 8, false, true", "true, 9, false, false",
                "true, 255, false, false", "true, 256, false, true", "true, 9, true, true"})
    void queue_front_should_apply_the_computer_byte_limit_only_to_a_nonempty_queue(boolean hasComputerRecord,
                                                                                   int rawHighlightedCount,
                                                                                   boolean isQueueEmpty,
                                                                                   boolean shouldInsert) {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit unit = createOrderUnit(game, ScrUnitIds.TERRAN_MARINE, hasComputerRecord ? RunningGameScenario.COMPUTER : RunningGameScenario.HUMAN);
            ScrOrderSystem assignment = game.getOrders();
            OrderPool pool = game.getOrderPool();

            Assertions.assertNotNull(pool);
            Assertions.assertNotEquals(NO_HIGHLIGHT, game.requireGameData().getOrderTypes().getHighlight(ScrOrderIds.PATROL));

            if (!isQueueEmpty) {
                assignment.queueBack(unit, ScrOrderIds.MOVE, null, 0, 0, ScrUnitIds.NONE);
            }

            Order first = unit.getFirstOrder();
            int allocatedCount = pool.getAllocatedCount();
            CommandErrorEvents errors = game.getInterfaceLink().getCommandErrorEvents();
            int eventCount = errors.getCount();
            long randomCount = game.getRandom().getTotalRandomCounts();

            unit.setHighlightedOrderCount(rawHighlightedCount);
            assignment.queueFront(unit, ScrOrderIds.PATROL, null, 0, 0, ScrUnitIds.NONE);

            if (shouldInsert) {
                Order inserted = unit.getFirstOrder();

                Assertions.assertNotNull(inserted);
                Assertions.assertNotSame(first, inserted);
                Assertions.assertEquals(PATROL_ORDER, inserted.getOrderId());
                Assertions.assertSame(unit, inserted.getOwner());
                Assertions.assertNull(inserted.getPrevious());
                Assertions.assertSame(first, inserted.getNext());
                Assertions.assertEquals(allocatedCount + 1, pool.getAllocatedCount());
                Assertions.assertEquals((rawHighlightedCount + 1) & BYTE_MASK, unit.getHighlightedOrderCount());

                if (first == null) {
                    Assertions.assertSame(inserted, unit.getLastOrder());
                } else {
                    Assertions.assertSame(inserted, first.getPrevious());
                    Assertions.assertSame(first, unit.getLastOrder());
                }
            } else {
                Assertions.assertSame(first, unit.getFirstOrder());
                Assertions.assertSame(first, unit.getLastOrder());
                Assertions.assertNotNull(first);
                Assertions.assertNull(first.getPrevious());
                Assertions.assertEquals(allocatedCount, pool.getAllocatedCount());
                Assertions.assertEquals(rawHighlightedCount & BYTE_MASK, unit.getHighlightedOrderCount());
            }

            Assertions.assertEquals(eventCount, errors.getCount());
            Assertions.assertEquals(randomCount, game.getRandom().getTotalRandomCounts());
        });
    }

    @Test
    void queue_front_should_skip_a_full_computer_queue_before_exhaustion_is_reported() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit unit = createOrderUnit(game, ScrUnitIds.TERRAN_MARINE, RunningGameScenario.COMPUTER);

            game.getOrders().queueBack(unit, ScrOrderIds.MOVE, null, 0, 0, ScrUnitIds.NONE);
            unit.setHighlightedOrderCount(COMPUTER_BLOCKED_COUNT);

            Order first = unit.getFirstOrder();

            Assertions.assertNotNull(first);

            fillOrderPool(game);

            CommandErrorEvents errors = game.getInterfaceLink().getCommandErrorEvents();
            int eventCount = errors.getCount();
            long randomCount = game.getRandom().getTotalRandomCounts();

            game.getOrders().queueFront(unit, ScrOrderIds.PATROL, null, 0, 0, ScrUnitIds.NONE);

            Assertions.assertSame(first, unit.getFirstOrder());
            Assertions.assertSame(first, unit.getLastOrder());
            Assertions.assertEquals(COMPUTER_BLOCKED_COUNT, unit.getHighlightedOrderCount());
            Assertions.assertEquals(eventCount, errors.getCount());
            Assertions.assertEquals(randomCount, game.getRandom().getTotalRandomCounts());
        });
    }

    @Test
    void queue_back_should_append_for_a_computer_record_above_the_front_insertion_limit() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit unit = createOrderUnit(game, ScrUnitIds.TERRAN_MARINE, RunningGameScenario.COMPUTER);

            game.getOrders().queueBack(unit, ScrOrderIds.MOVE, null, 0, 0, ScrUnitIds.NONE);
            unit.setHighlightedOrderCount(COMPUTER_BLOCKED_COUNT);

            Order first = unit.getFirstOrder();

            Assertions.assertNotNull(first);

            long randomCount = game.getRandom().getTotalRandomCounts();
            CommandErrorEvents errors = game.getInterfaceLink().getCommandErrorEvents();
            int eventCount = errors.getCount();

            game.getOrders().queueBack(unit, ScrOrderIds.PATROL, null, 0, 0, ScrUnitIds.NONE);

            Order appended = first.getNext();

            Assertions.assertNotNull(appended);
            Assertions.assertSame(first, unit.getFirstOrder());
            Assertions.assertSame(appended, unit.getLastOrder());
            Assertions.assertSame(first, appended.getPrevious());
            Assertions.assertSame(unit, appended.getOwner());
            Assertions.assertEquals(PATROL_ORDER, appended.getOrderId());
            Assertions.assertEquals(COMPUTER_BLOCKED_COUNT + 1, unit.getHighlightedOrderCount());
            Assertions.assertEquals(eventCount, errors.getCount());
            Assertions.assertEquals(randomCount, game.getRandom().getTotalRandomCounts());
        });
    }

    @Test
    void burrowed_hydralisk_keeps_the_new_morph_immediately_after_the_original_head() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit unit = createOrderUnit(game, ScrUnitIds.ZERG_HYDRALISK, RunningGameScenario.HUMAN);
            ScrOrderSystem assignment = game.getOrders();

            Assertions.assertEquals(0, game.requireGameData().getOrderTypes().getCanBeInterrupted(ScrOrderIds.DIE));

            game.getUnitBurrowing().setBurrowed(unit);
            unit.setPrimaryOrder(ScrOrderIds.BURROWED);
            unit.setStatus(unit.getStatus() | UnitStatusFlags.ISCRIPT_NO_BREAK);
            assignment.queueBack(unit, ScrOrderIds.ZERG_UNIT_MORPH, null, 0, 0, ScrUnitIds.NONE);
            assignment.queueBack(unit, ScrOrderIds.DIE, null, 0, 0, ScrUnitIds.NONE);

            Order first = unit.getFirstOrder();
            Order protectedTail = unit.getLastOrder();

            Assertions.assertNotNull(first);
            Assertions.assertNotNull(protectedTail);
            Assertions.assertNotSame(first, protectedTail);

            long randomCount = game.getRandom().getTotalRandomCounts();
            CommandErrorEvents errors = game.getInterfaceLink().getCommandErrorEvents();
            int eventCount = errors.getCount();

            game.getUnitBurrowing().updateBurrowed(unit, RunningGameScenario.INSTRUCTION_LIMIT);

            Order inserted = first.getNext();
            Order unburrowing = unit.getLastOrder();

            Assertions.assertNotNull(inserted);
            Assertions.assertNotNull(unburrowing);
            Assertions.assertSame(first, unit.getFirstOrder());
            Assertions.assertNotSame(protectedTail, inserted);
            Assertions.assertEquals(ZERG_UNIT_MORPH_ORDER, inserted.getOrderId());
            Assertions.assertSame(first, inserted.getPrevious());
            Assertions.assertSame(protectedTail, inserted.getNext());
            Assertions.assertSame(inserted, protectedTail.getPrevious());
            Assertions.assertSame(unburrowing, protectedTail.getNext());
            Assertions.assertEquals(UNBURROWING_ORDER, unburrowing.getOrderId());
            Assertions.assertSame(unit, inserted.getOwner());
            Assertions.assertEquals(eventCount, errors.getCount());
            Assertions.assertEquals(randomCount, game.getRandom().getTotalRandomCounts());
        });
    }

    @Test
    void queue_back_should_reject_a_game_whose_order_pool_was_not_initialized() {
        GameData installedData = gameData;

        Assertions.assertNotNull(installedData);

        Game game = Game.create(installedData, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));
        Unit unit = UnitTestUtils.createUnit(1);

        Assertions.assertNull(game.getOrderPool());

        UnsupportedOperationException failure = Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            game.getOrders().queueBack(unit, ScrOrderIds.MOVE, null, 0, 0, ScrUnitIds.NONE);
        });

        Assertions.assertEquals("The order pool is not initialized.", failure.getMessage());
        Assertions.assertNull(unit.getFirstOrder());
        Assertions.assertNull(unit.getLastOrder());
        Assertions.assertEquals(0, game.getInterfaceLink().getCommandErrorEvents().getCount());
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void queue_back_should_preserve_byte_and_word_target_widths() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            Unit unit = createOrderUnit(game, ScrUnitIds.TERRAN_MARINE, RunningGameScenario.HUMAN);
            long randomCount = game.getRandom().getTotalRandomCounts();

            game.getOrders().queueBack(unit, RAW_ORDER_IDENTIFIER, null, RAW_TARGET_POSITION_X, RAW_TARGET_POSITION_Y, RAW_TARGET_TYPE);

            Order order = unit.getFirstOrder();

            Assertions.assertNotNull(order);
            Assertions.assertSame(order, unit.getLastOrder());
            Assertions.assertSame(unit, order.getOwner());
            Assertions.assertEquals(RAW_ORDER_IDENTIFIER & BYTE_MASK, order.getOrderId());
            Assertions.assertEquals(RAW_TARGET_TYPE & WORD_MASK, order.getUnitType());
            Assertions.assertEquals(RAW_TARGET_POSITION_X & WORD_MASK, order.getTargetX());
            Assertions.assertEquals(RAW_TARGET_POSITION_Y & WORD_MASK, order.getTargetY());
            Assertions.assertNull(order.getTargetUnit());
            Assertions.assertEquals(randomCount, game.getRandom().getTotalRandomCounts());
        });
    }
}
