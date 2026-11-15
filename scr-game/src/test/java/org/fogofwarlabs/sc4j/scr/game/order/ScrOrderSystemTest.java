package org.fogofwarlabs.sc4j.scr.game.order;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.OrdersDat;
import org.fogofwarlabs.sc4j.scr.data.type.ScrOrderTypeView;
import org.fogofwarlabs.sc4j.scr.game.order.OrderPool.Order;
import org.fogofwarlabs.sc4j.scr.game.presentation.CommandErrorEvents;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpritePool;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.sprite.script.IscriptExecutor;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitDiagnosticAssert;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitMemoryScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks initial order assignment, subunit inheritance, queued target activation, the order pool capacity tests, and untargeted order activation
 * for living units and units whose death has been requested.
 */
@RequiresInstallation
final class ScrOrderSystemTest {
    /**
     * The SHA-256 digests of the initial order capture and of the subunit initial order capture.
     */
    private static final @NotNull String INITIAL_ORDERS_CAPTURE_HASH = "9c21e59a4becb988f80e3fbc28d21a1167bcabe51e913af3d80f4db56814106a";
    private static final @NotNull String SUBUNIT_INITIAL_ORDERS_CAPTURE_HASH = "535f703e235384d29dc97bf361c5797bec97d8554ff050fc58eee4510e2353f4";
    /**
     * The size of the captured unit record.
     */
    private static final int UNIT_RECORD_SIZE = 488;
    /**
     * The byte that fills a captured unit record before its fields are written.
     */
    private static final int UNIT_FILL_BYTE = 0xA5;
    /**
     * The offsets and the size of the unit references that a captured unit record clears.
     */
    private static final int @NotNull [] REFERENCE_OFFSETS = {0x18, 0x80, 0xA0, 0xA8, 0xB0, 0x1C8};
    private static final int REFERENCE_SIZE = 8;
    /**
     * The unit memory offsets of the primary order BYTE, the unit type WORD, the highlighted order count BYTE and the status flags DWORD.
     */
    private static final int PRIMARY_ORDER_OFFSET = 0x69;
    private static final int UNIT_TYPE_OFFSET = 0x8C;
    private static final int HIGHLIGHTED_ORDER_COUNT_OFFSET = 0xC8;
    private static final int STATUS_OFFSET = 0x140;
    /**
     * The status with the ready-to-attack and the automatic-attack-started bits.
     */
    private static final int READY_STATUS = 0x08000008;
    /**
     * The status with the order-not-interruptible and the script-no-break bits.
     */
    private static final int UNINTERRUPTIBLE_STATUS = 0x3000;
    /**
     * The initial statuses of the captured units.
     */
    private static final int @NotNull [] INITIAL_STATUSES = {READY_STATUS, UNINTERRUPTIBLE_STATUS};

    /**
     * The order capacity of the games of the captured cases.
     */
    private static final int CAPTURE_ORDER_CAPACITY = 4;
    /**
     * The order capacity of the games of the queue tests.
     */
    private static final int QUEUE_ORDER_CAPACITY = 16;
    /**
     * The instruction budget of each animation script invocation in the games without a running scenario.
     */
    private static final int FIXTURE_INSTRUCTION_LIMIT = 512;

    /**
     * The order limit of the classic limits.
     */
    private static final int CLASSIC_ORDER_LIMIT = 2000;
    /**
     * The allocated orders from which a pool of the classic order limit is nearly full.
     */
    private static final int CLASSIC_NEARLY_FULL_COUNT = 1800;
    /**
     * The divisor of the order capacity that gives the free orders of a nearly full pool.
     */
    private static final int NEARLY_FULL_RESERVE_DIVISOR = 10;
    /**
     * The {@code stat_txt} id of the exhausted-order-pool text.
     */
    private static final int TEXT_ORDER_POOL_FULL = 0x369;

    /**
     * The row of the units of the untargeted order test.
     */
    private static final int UNTARGETED_TEST_POSITION_Y = 2440;
    /**
     * The row of the units of the queued order test.
     */
    private static final int QUEUE_TEST_POSITION_Y = 2480;
    /**
     * The column of the unit that holds the filling orders.
     */
    private static final int FILLER_POSITION_X = 3780;
    /**
     * The column of the unit that receives the orders.
     */
    private static final int MARINE_POSITION_X = 3820;
    /**
     * The row of the move targets of the queued order test.
     */
    private static final int MOVE_TARGET_Y = 2400;
    /**
     * The column of the first move target of the queued order test.
     */
    private static final int MOVE_TARGET_X = 3700;
    /**
     * The column of the queued move target of the queued order test.
     */
    private static final int QUEUED_TARGET_X = 3600;

    /**
     * The order position that the position of the unit target replaces in the queued target test.
     */
    private static final int REPLACED_POSITION_X = 1;
    private static final int REPLACED_POSITION_Y = 2;
    /**
     * The sprite position of the unit target when its order is queued.
     */
    private static final int TARGET_SPRITE_X = 120;
    private static final int TARGET_SPRITE_Y = 200;
    /**
     * The sprite position of the unit target when its queued order is activated.
     */
    private static final int MOVED_TARGET_SPRITE_X = 144;
    private static final int MOVED_TARGET_SPRITE_Y = 224;
    /**
     * The target position of the pending order that the repeated order replaces.
     */
    private static final int REPLACED_ORDER_X = 100;
    private static final int REPLACED_ORDER_Y = 200;
    /**
     * The target position of the repeated order.
     */
    private static final int REPLACING_ORDER_X = 300;
    private static final int REPLACING_ORDER_Y = 400;
    /**
     * The target position of the first queued or issued order of the queue tests.
     */
    private static final int FIRST_TARGET_X = 40;
    private static final int FIRST_TARGET_Y = 80;
    /**
     * The target position of the second queued order of the queue tests.
     */
    private static final int SECOND_TARGET_X = 60;
    private static final int SECOND_TARGET_Y = 90;
    /**
     * The position of the units of the untargeted order activation tests.
     */
    private static final int UNIT_POSITION_X = 3808;
    private static final int UNIT_POSITION_Y = 2480;
    /**
     * The pool index of the ordered unit and of a capture's unit.
     */
    private static final int FIRST_UNIT_INDEX = 1;
    /**
     * The pool index of the target unit and of a capture's subunit.
     */
    private static final int SECOND_UNIT_INDEX = 2;

    /**
     * The Die order.
     */
    private static final int DIE_ORDER = 0;
    /**
     * The Guard order.
     */
    private static final int GUARD_ORDER = 2;
    /**
     * The Move order.
     */
    private static final int MOVE_ORDER = 6;
    /**
     * The Nothing order, which a unit whose script must not break runs while its repeated order waits.
     */
    private static final int NOTHING_ORDER = 23;
    /**
     * The Reset Collision order.
     */
    private static final int RESET_COLLISION_ORDER = 150;
    /**
     * The unit type id that names no unit type.
     */
    private static final int NO_UNIT_TYPE = 228;
    /**
     * The unit type of the repeated order: Mineral Field Type 2.
     */
    private static final int REPLACING_ORDER_UNIT_TYPE = 177;

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

    private static @NotNull Game createGame(int orderCapacity) {
        Game game = Game.create(gameData, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));

        game.initializeOrderPool(orderCapacity);

        return game;
    }

    /**
     * Fills the captured record of a unit, clears its references and writes its type, an empty order queue and its status.
     *
     * @param unit     the captured unit
     * @param unitType the {@code units.dat} type of the unit
     * @param status   the status of the unit
     */
    private static void writeCaptureMemory(@NotNull Unit unit, int unitType, int status) {
        for (int offset = 0; offset < UNIT_RECORD_SIZE; offset++) {
            UnitMemoryScenario.of(unit).writeMemoryByte(offset, UNIT_FILL_BYTE);
        }

        for (int offset : REFERENCE_OFFSETS) {
            UnitMemoryScenario.of(unit).clearMemoryBytes(offset, REFERENCE_SIZE);
        }

        UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, unitType);
        UnitMemoryScenario.of(unit).writeMemoryByte(PRIMARY_ORDER_OFFSET, ScrOrderIds.NOTHING);
        UnitMemoryScenario.of(unit).writeMemoryByte(HIGHLIGHTED_ORDER_COUNT_OFFSET, 0);
        UnitMemoryScenario.of(unit).writeMemoryInteger(STATUS_OFFSET, status);
    }

    /**
     * Gives a captured unit the allocated sprite with a built main image and the hidden and script-no-break flags.
     *
     * @param unit   the captured unit
     * @param sprite the allocated sprite, which must not be {@code null}
     * @return the sprite of the unit
     */
    private static @NotNull Sprite attachCaptureSprite(@NotNull Unit unit, @Nullable Sprite sprite) {
        Assertions.assertNotNull(sprite);

        SpriteImage image = SpriteScenario.createImage(0, 0, 0);

        image.setAnimationType(IscriptExecutor.ANIMATION_BUILT);
        sprite.setMainImage(image);
        sprite.setFlags(Sprite.FLAG_HIDDEN | Sprite.FLAG_ISCRIPT_NO_BREAK);
        unit.setSprite(sprite);

        return sprite;
    }

    private static @NotNull Unit createOrderUnit(int poolIndex) {
        Unit unit = UnitTestUtils.createUnit(poolIndex);

        unit.setPrimaryOrder(ScrOrderIds.NOTHING);

        Sprite sprite = new SpritePool(1).allocate();

        Assertions.assertNotNull(sprite);

        SpriteImage image = SpriteScenario.createImage(0, 0, 0);

        image.setAnimationType(IscriptExecutor.ANIMATION_BUILT);
        sprite.setMainImage(image);
        unit.setSprite(sprite);

        return unit;
    }

    private static void fillOrderPool(@NotNull OrderPool pool,
                                      @NotNull Unit filler,
                                      int allocatedCount,
                                      @NotNull ScrOrderTypeView orderTypes,
                                      @NotNull List<Order> filled) {
        while (pool.getAllocatedCount() < allocatedCount) {
            Order order = pool.allocate(ScrOrderIds.MOVE, ScrUnitIds.NONE, 0, 0, null);

            Assertions.assertNotNull(order);

            pool.enqueue(filler, order, null, orderTypes);
            filled.add(order);
        }
    }

    private static @NotNull ReplayRuntime startLiveGame() throws IOException {
        GameData installedData = gameData;

        Assertions.assertNotNull(installedData);

        return RunningGameScenario.startLiveGame(installedData);
    }

    private static @NotNull Unit createDyingUnit(@NotNull Game game) {
        Unit unit = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_MARINE, UNIT_POSITION_X, UNIT_POSITION_Y, RunningGameScenario.HUMAN);

        Assertions.assertNull(unit.getFirstOrder());

        game.getUnitLifecycle().kill(unit, RunningGameScenario.INSTRUCTION_LIMIT);

        Sprite sprite = unit.getSpriteOrNull();

        Assertions.assertEquals(DIE_ORDER, unit.getPrimaryOrder());
        Assertions.assertNotNull(sprite);
        Assertions.assertNotNull(sprite.getMainImage());
        Assertions.assertNull(unit.getFirstOrder());

        game.getOrders().queueBack(unit, ScrOrderIds.DIE, null, 0, 0, ScrUnitIds.NONE);

        Order pendingOrder = unit.getFirstOrder();

        Assertions.assertNotNull(pendingOrder);
        Assertions.assertSame(pendingOrder, unit.getLastOrder());
        Assertions.assertSame(unit, pendingOrder.getOwner());
        Assertions.assertEquals(DIE_ORDER, pendingOrder.getOrderId());

        return unit;
    }

    private static void assertDeathOrderActivated(@NotNull Unit unit, @NotNull OrderPool pool, int allocatedCount) {
        Assertions.assertNull(unit.getFirstOrder(), "A dying unit's pending death order must activate.");
        Assertions.assertNull(unit.getLastOrder());
        Assertions.assertEquals(allocatedCount - 1, pool.getAllocatedCount());
        Assertions.assertEquals(0, unit.getUserActionFlags() & Unit.USER_ACTION_ORDER_DONE);
        Assertions.assertEquals(DIE_ORDER, unit.getPrimaryOrder());
        Assertions.assertNull(unit.getOrderTargetUnit());
        Assertions.assertEquals(NO_UNIT_TYPE, unit.getOrderTargetUnitType());
        Assertions.assertEquals(0, unit.getOrderTargetX());
        Assertions.assertEquals(0, unit.getOrderTargetY());
    }


    @Test
    void matches_native_initial_order_cases() throws Exception {
        byte[] fixture = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_INITIAL_ORDERS, INITIAL_ORDERS_CAPTURE_HASH);
        ByteBuffer expected = ByteBuffer.wrap(fixture).order(ByteOrder.LITTLE_ENDIAN);
        byte[] expectedUnit = new byte[UNIT_RECORD_SIZE];

        for (int orderId = 0; orderId < OrdersDat.ORDER_COUNT; orderId++) {
            if (orderId == ScrOrderIds.CLOAK) {
                continue;
            }

            for (int initialStatus : INITIAL_STATUSES) {
                Game game = createGame(CAPTURE_ORDER_CAPACITY);
                Unit unit = UnitTestUtils.createUnit(FIRST_UNIT_INDEX);

                writeCaptureMemory(unit, ScrUnitIds.TERRAN_MARINE, initialStatus);

                Sprite sprite = attachCaptureSprite(unit, new SpritePool(1).allocate());

                game.getOrders().issueUntargeted(unit, orderId, FIXTURE_INSTRUCTION_LIMIT);

                expected.get(expectedUnit);

                UnitDiagnosticAssert.assertDomainEquals(expectedUnit, unit, "Initial order " + orderId);

                Assertions.assertEquals(Byte.toUnsignedInt(expected.get()), unit.getFirstOrder() == null ? 0 : 1);
                Assertions.assertEquals(Byte.toUnsignedInt(expected.get()), sprite.getFlags());
                Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
            }
        }

        Assertions.assertFalse(expected.hasRemaining());
    }

    @Test
    void matches_native_subunit_initial_order_cases() throws Exception {
        byte[] fixture = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_SUBUNIT_INITIAL_ORDERS, SUBUNIT_INITIAL_ORDERS_CAPTURE_HASH);
        ByteBuffer expected = ByteBuffer.wrap(fixture).order(ByteOrder.LITTLE_ENDIAN);
        byte[] expectedUnit = new byte[UNIT_RECORD_SIZE];

        for (int[] unitTypes : new int[][]{
                {ScrUnitIds.TERRAN_GOLIATH, ScrUnitIds.TERRAN_GOLIATH_TURRET},
                {ScrUnitIds.TERRAN_SIEGE_TANK_TANK_MODE, ScrUnitIds.TERRAN_SIEGE_TANK_TANK_MODE_TURRET},
                {ScrUnitIds.TERRAN_SIEGE_TANK_SIEGE_MODE, ScrUnitIds.TERRAN_SIEGE_TANK_SIEGE_MODE_TURRET}
        }) {
            for (int orderId = 0; orderId < OrdersDat.ORDER_COUNT; orderId++) {
                if (orderId == ScrOrderIds.CLOAK) {
                    continue;
                }

                Game game = createGame(CAPTURE_ORDER_CAPACITY);
                Unit[] units = {UnitTestUtils.createUnit(FIRST_UNIT_INDEX), UnitTestUtils.createUnit(SECOND_UNIT_INDEX)};
                SpritePool sprites = new SpritePool(units.length);

                for (int unitIndex = 0; unitIndex < units.length; unitIndex++) {
                    Unit unit = units[unitIndex];

                    writeCaptureMemory(unit, unitTypes[unitIndex], READY_STATUS);
                    attachCaptureSprite(unit, sprites.allocate());
                }

                units[0].setSubunit(units[1]);

                game.getOrders().issueUntargeted(units[0], orderId, FIXTURE_INSTRUCTION_LIMIT);

                OrderPool orders = game.getOrderPool();

                Assertions.assertNotNull(orders);

                Assertions.assertEquals(0, orders.getAllocatedCount());

                for (Unit unit : units) {
                    expected.get(expectedUnit);

                    UnitDiagnosticAssert.assertDomainEquals(expectedUnit, unit, "Subunit order " + orderId);

                    Assertions.assertEquals(Byte.toUnsignedInt(expected.get()), unit.getFirstOrder() == null ? 0 : 1);

                    int expectedFlags = Byte.toUnsignedInt(expected.get());
                    Sprite observedSprite = unit.getSpriteOrNull();

                    Assertions.assertNotNull(observedSprite);

                    Assertions.assertEquals(expectedFlags, observedSprite.getFlags());
                }
            }
        }

        Assertions.assertFalse(expected.hasRemaining());
    }

    @Test
    void activate_next_should_refresh_queued_unit_target_and_recycle_order() {
        Game game = createGame(QUEUE_ORDER_CAPACITY);
        ScrOrderSystem assignment = game.getOrders();
        Unit unit = createOrderUnit(FIRST_UNIT_INDEX);
        Unit target = createOrderUnit(SECOND_UNIT_INDEX);

        Sprite horizontalSprite = target.getSpriteOrNull();

        Assertions.assertNotNull(horizontalSprite);

        horizontalSprite.setX(TARGET_SPRITE_X);

        Sprite verticalSprite = target.getSpriteOrNull();

        Assertions.assertNotNull(verticalSprite);

        verticalSprite.setY(TARGET_SPRITE_Y);
        unit.setPrimaryOrder(ScrOrderIds.MOVE);

        assignment.issue(unit,
                         ScrOrderIds.MOVE,
                         target,
                         REPLACED_POSITION_X, REPLACED_POSITION_Y,
                         ScrUnitIds.RESOURCE_MINERAL_FIELD,
                         true,
                         FIXTURE_INSTRUCTION_LIMIT);

        Assertions.assertEquals(MOVE_ORDER, unit.getPrimaryOrder());
        Assertions.assertNotNull(unit.getFirstOrder());

        Order positionXOrder = unit.getFirstOrder();

        Assertions.assertNotNull(positionXOrder);

        Assertions.assertEquals(TARGET_SPRITE_X, positionXOrder.getTargetX());

        horizontalSprite.setX(MOVED_TARGET_SPRITE_X);
        verticalSprite.setY(MOVED_TARGET_SPRITE_Y);

        assignment.activateNext(unit, FIXTURE_INSTRUCTION_LIMIT);

        Assertions.assertSame(target, unit.getOrderTargetUnit());
        Assertions.assertEquals(MOVED_TARGET_SPRITE_X, unit.getOrderTargetX());
        Assertions.assertEquals(MOVED_TARGET_SPRITE_Y, unit.getOrderTargetY());
        Assertions.assertEquals(NO_UNIT_TYPE, unit.getOrderTargetUnitType());
        Assertions.assertNull(unit.getFirstOrder());

        OrderPool orders = game.getOrderPool();

        Assertions.assertNotNull(orders);

        Assertions.assertEquals(0, orders.getAllocatedCount());
        Assertions.assertEquals(0, unit.getHighlightedOrderCount());
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void repeated_runtime_order_replaces_pending_target_and_waits_for_script() {
        Game game = createGame(QUEUE_ORDER_CAPACITY);
        ScrOrderSystem assignment = game.getOrders();
        Unit unit = createOrderUnit(FIRST_UNIT_INDEX);

        unit.setStatus(UnitStatusFlags.ISCRIPT_NO_BREAK);

        assignment.issue(unit,
                         ScrOrderIds.MOVE,
                         null,
                         REPLACED_ORDER_X, REPLACED_ORDER_Y,
                         ScrUnitIds.RESOURCE_MINERAL_FIELD,
                         false,
                         FIXTURE_INSTRUCTION_LIMIT);
        assignment.issue(unit,
                         ScrOrderIds.MOVE,
                         null,
                         REPLACING_ORDER_X, REPLACING_ORDER_Y,
                         ScrUnitIds.RESOURCE_MINERAL_FIELD_TYPE_2,
                         false,
                         FIXTURE_INSTRUCTION_LIMIT);

        Assertions.assertEquals(NOTHING_ORDER, unit.getPrimaryOrder());
        Assertions.assertSame(unit.getFirstOrder(), unit.getLastOrder());

        OrderPool orders = game.getOrderPool();

        Assertions.assertNotNull(orders);

        Assertions.assertEquals(1, orders.getAllocatedCount());

        unit.setStatus(0);

        assignment.activateNext(unit, FIXTURE_INSTRUCTION_LIMIT);

        Assertions.assertEquals(MOVE_ORDER, unit.getPrimaryOrder());
        Assertions.assertEquals(REPLACING_ORDER_X, unit.getOrderTargetX());
        Assertions.assertEquals(REPLACING_ORDER_Y, unit.getOrderTargetY());
        Assertions.assertEquals(REPLACING_ORDER_UNIT_TYPE, unit.getOrderTargetUnitType());
        Assertions.assertNull(unit.getOrderTargetUnit());
        Assertions.assertEquals(0, orders.getAllocatedCount());
    }

    @Test
    void issue_should_replace_interruptible_queued_orders_and_activate_the_new_order() {
        Game game = createGame(QUEUE_ORDER_CAPACITY);
        Unit unit = createOrderUnit(FIRST_UNIT_INDEX);

        unit.setPrimaryOrder(ScrOrderIds.FOLLOW);

        game.getOrders().queueBack(unit, ScrOrderIds.ATTACK_UNIT, null, FIRST_TARGET_X, FIRST_TARGET_Y, ScrUnitIds.NONE);
        game.getOrders().queueBack(unit, ScrOrderIds.MOVE, null, SECOND_TARGET_X, SECOND_TARGET_Y, ScrUnitIds.NONE);

        game.getOrders().issueUntargeted(unit, ScrOrderIds.GUARD, FIXTURE_INSTRUCTION_LIMIT);

        Assertions.assertEquals(GUARD_ORDER, unit.getPrimaryOrder());
        Assertions.assertNull(unit.getFirstOrder());
        Assertions.assertNull(unit.getLastOrder());
        Assertions.assertEquals(0, unit.getHighlightedOrderCount());

        OrderPool orders = game.getOrderPool();

        Assertions.assertNotNull(orders);

        Assertions.assertEquals(0, orders.getAllocatedCount());
    }

    @Test
    void issue_should_activate_the_pending_death_order_when_a_targeted_order_reaches_a_dying_unit() {
        Game game = createGame(QUEUE_ORDER_CAPACITY);
        ScrOrderSystem assignment = game.getOrders();
        Unit unit = createOrderUnit(FIRST_UNIT_INDEX);
        Unit target = createOrderUnit(SECOND_UNIT_INDEX);

        unit.setPrimaryOrder(ScrOrderIds.DIE);

        for (Unit orderTarget : new Unit[]{null, target}) {
            assignment.queueBack(unit, ScrOrderIds.DIE, null, 0, 0, ScrUnitIds.NONE);
            assignment.issue(unit, ScrOrderIds.MOVE, orderTarget, FIRST_TARGET_X, FIRST_TARGET_Y, ScrUnitIds.NONE, false, FIXTURE_INSTRUCTION_LIMIT);

            Assertions.assertNull(unit.getFirstOrder(), "A dying unit's pending death order must activate.");
            Assertions.assertEquals(DIE_ORDER, unit.getPrimaryOrder());
            Assertions.assertNull(unit.getOrderTargetUnit());
            Assertions.assertEquals(0, unit.getUserActionFlags() & Unit.USER_ACTION_ORDER_DONE);
        }

        OrderPool orders = game.getOrderPool();

        Assertions.assertNotNull(orders);

        Assertions.assertEquals(0, orders.getAllocatedCount());
    }

    @Test
    void finish_order_should_complete_internal_queue_before_returning_to_idle() {
        Game game = createGame(QUEUE_ORDER_CAPACITY);
        ScrOrderSystem assignment = game.getOrders();
        Unit unit = createOrderUnit(FIRST_UNIT_INDEX);

        unit.setPrimaryOrder(ScrOrderIds.MOVE);

        assignment.issue(unit, ScrOrderIds.MOVE, null, FIRST_TARGET_X, FIRST_TARGET_Y, ScrUnitIds.NONE, true, FIXTURE_INSTRUCTION_LIMIT);
        assignment.queueFront(unit, ScrOrderIds.RESET_COLLISION, null, 0, 0, ScrUnitIds.NONE);

        assignment.finishOrder(unit, FIXTURE_INSTRUCTION_LIMIT);

        Assertions.assertEquals(RESET_COLLISION_ORDER, unit.getPrimaryOrder());

        Order firstOrder = unit.getFirstOrder();

        Assertions.assertNotNull(firstOrder);

        Assertions.assertEquals(MOVE_ORDER, firstOrder.getOrderId());

        unit.setStatus(unit.getStatus() & ~UnitStatusFlags.ORDER_NOT_INTERRUPTIBLE);

        assignment.finishOrder(unit, FIXTURE_INSTRUCTION_LIMIT);

        Assertions.assertEquals(MOVE_ORDER, unit.getPrimaryOrder());
        Assertions.assertEquals(FIRST_TARGET_X, unit.getOrderTargetX());

        assignment.finishOrder(unit, FIXTURE_INSTRUCTION_LIMIT);

        Assertions.assertNotNull(gameData);

        Assertions.assertEquals(gameData.getUnits().get("Return to Idle", unit.getUnitId()), unit.getPrimaryOrder());
        Assertions.assertNull(unit.getFirstOrder());

        OrderPool orders = game.getOrderPool();

        Assertions.assertNotNull(orders);

        Assertions.assertEquals(0, orders.getAllocatedCount());
    }

    @Test
    void issue_should_refuse_order_when_order_pool_is_nearly_full() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            OrderPool pool = game.getOrderPool();

            Assertions.assertNotNull(pool);

            GameData scenarioGameData = game.getGameData();

            Assertions.assertNotNull(scenarioGameData);

            DatTable orders = scenarioGameData.getOrders();
            Unit filler = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_MARINE,
                                                         FILLER_POSITION_X, UNTARGETED_TEST_POSITION_Y, RunningGameScenario.HUMAN);
            Unit marine = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_MARINE,
                                                         MARINE_POSITION_X, UNTARGETED_TEST_POSITION_Y, RunningGameScenario.HUMAN);
            int primary = marine.getPrimaryOrder();
            List<Order> filled = new ArrayList<>();

            while (pool.getAllocatedCount() < pool.getNativeCapacity() - pool.getNativeCapacity() / NEARLY_FULL_RESERVE_DIVISOR) {
                Order order = pool.allocate(ScrOrderIds.MOVE, ScrUnitIds.NONE, 0, 0, null);

                Assertions.assertNotNull(order);

                pool.enqueue(filler, order, null, OrdersDat.createTypeTable(orders));
                filled.add(order);
            }

            try {
                Assertions.assertNull(marine.getFirstOrder());

                Assertions.assertDoesNotThrow(() -> {
                    game.getOrders().issueUntargeted(marine, ScrOrderIds.HOLD_POSITION, RunningGameScenario.INSTRUCTION_LIMIT);
                });

                Assertions.assertNull(marine.getFirstOrder(), "A unit that is not dying or harvesting gets no order.");
                Assertions.assertEquals(primary, marine.getPrimaryOrder());
            } finally {
                for (Order order : filled) {
                    pool.remove(filler, order, OrdersDat.createTypeTable(orders));
                }
            }
        });
    }

    @Test
    void issue_should_refuse_a_queued_order_only_from_the_nearly_full_order_pool() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            OrderPool pool = game.getOrderPool();

            Assertions.assertNotNull(pool);
            Assertions.assertTrue(pool.getNativeCapacity() > CLASSIC_ORDER_LIMIT, "The scenario has the extended order limit.");

            GameData scenarioGameData = game.getGameData();

            Assertions.assertNotNull(scenarioGameData);

            ScrOrderTypeView orderTypes = OrdersDat.createTypeTable(scenarioGameData.getOrders());
            CommandErrorEvents errors = game.getInterfaceLink().getCommandErrorEvents();
            ScrOrderSystem assignment = game.getOrders();
            Unit filler = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_MARINE,
                                                         FILLER_POSITION_X, QUEUE_TEST_POSITION_Y, RunningGameScenario.HUMAN);
            Unit marine = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_MARINE,
                                                         MARINE_POSITION_X, QUEUE_TEST_POSITION_Y, RunningGameScenario.HUMAN);
            List<Order> filled = new ArrayList<>();

            assignment.issue(marine,
                             ScrOrderIds.MOVE,
                             null,
                             MOVE_TARGET_X, MOVE_TARGET_Y,
                             ScrUnitIds.NONE,
                             false,
                             RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(MOVE_ORDER, marine.getPrimaryOrder());

            try {
                fillOrderPool(pool, filler, CLASSIC_NEARLY_FULL_COUNT, orderTypes, filled);

                int errorCount = errors.getCount();

                assignment.issue(marine,
                                 ScrOrderIds.MOVE,
                                 null,
                                 QUEUED_TARGET_X, MOVE_TARGET_Y,
                                 ScrUnitIds.NONE,
                                 true,
                                 RunningGameScenario.INSTRUCTION_LIMIT);

                Order queued = marine.getLastOrder();

                Assertions.assertNotNull(queued, "The pool is not nearly full at the classic count.");
                Assertions.assertEquals(MOVE_ORDER, queued.getOrderId());
                Assertions.assertEquals(errorCount, errors.getCount());

                pool.remove(marine, queued, orderTypes);

                fillOrderPool(pool, filler, pool.getNativeCapacity() - pool.getNativeCapacity() / NEARLY_FULL_RESERVE_DIVISOR, orderTypes, filled);

                assignment.issue(marine,
                                 ScrOrderIds.MOVE,
                                 null,
                                 QUEUED_TARGET_X, MOVE_TARGET_Y,
                                 ScrUnitIds.NONE,
                                 true,
                                 RunningGameScenario.INSTRUCTION_LIMIT);

                Assertions.assertNull(marine.getLastOrder(), "A nearly full pool refuses the queued order.");
                Assertions.assertEquals(errorCount + 1, errors.getCount());
                Assertions.assertEquals(TEXT_ORDER_POOL_FULL, errors.get(errorCount).getTextIdentifier());
            } finally {
                for (Order order : filled) {
                    pool.remove(filler, order, orderTypes);
                }
            }
        });
    }

    @Test
    void issue_untargeted_should_activate_the_pending_death_order_when_the_unit_is_dying() throws IOException {
        ReplayRuntime liveRuntime = startLiveGame();

        RunningGameScenario.runInCommandDispatch(liveRuntime, () -> {
            Assertions.assertNotNull(liveRuntime);

            Game game = liveRuntime.getGame();
            Unit unit = createDyingUnit(game);
            OrderPool pool = game.getOrderPool();

            Assertions.assertNotNull(pool);

            int allocatedCount = pool.getAllocatedCount();

            game.getOrders().issueUntargeted(unit, ScrOrderIds.GUARD, RunningGameScenario.INSTRUCTION_LIMIT);

            assertDeathOrderActivated(unit, pool, allocatedCount);
        });
    }

    @Test
    void return_to_idle_should_activate_the_pending_death_order_when_the_unit_is_dying() throws IOException {
        ReplayRuntime liveRuntime = startLiveGame();

        RunningGameScenario.runInCommandDispatch(liveRuntime, () -> {
            Assertions.assertNotNull(liveRuntime);

            Game game = liveRuntime.getGame();
            Unit unit = createDyingUnit(game);
            OrderPool pool = game.getOrderPool();

            Assertions.assertNotNull(pool);

            int allocatedCount = pool.getAllocatedCount();

            game.getOrders().returnToIdle(unit, RunningGameScenario.INSTRUCTION_LIMIT);

            assertDeathOrderActivated(unit, pool, allocatedCount);
        });
    }

    @Test
    void issue_untargeted_should_activate_an_idle_order_for_a_living_unit() throws IOException {
        ReplayRuntime liveRuntime = startLiveGame();

        RunningGameScenario.runInCommandDispatch(liveRuntime, () -> {
            Assertions.assertNotNull(liveRuntime);

            Game game = liveRuntime.getGame();
            Unit unit = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_MARINE, UNIT_POSITION_X, UNIT_POSITION_Y, RunningGameScenario.HUMAN);
            OrderPool pool = game.getOrderPool();

            Assertions.assertNotNull(pool);
            Assertions.assertNull(unit.getFirstOrder());

            int allocatedCount = pool.getAllocatedCount();

            game.getOrders().issueUntargeted(unit, ScrOrderIds.GUARD, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(GUARD_ORDER, unit.getPrimaryOrder());
            Assertions.assertNull(unit.getFirstOrder());
            Assertions.assertNull(unit.getLastOrder());
            Assertions.assertEquals(allocatedCount, pool.getAllocatedCount());
            Assertions.assertEquals(0, unit.getUserActionFlags() & Unit.USER_ACTION_ORDER_DONE);
            Assertions.assertNull(unit.getOrderTargetUnit());
            Assertions.assertEquals(NO_UNIT_TYPE, unit.getOrderTargetUnitType());
            Assertions.assertEquals(0, unit.getOrderTargetX());
            Assertions.assertEquals(0, unit.getOrderTargetY());
        });
    }
}
