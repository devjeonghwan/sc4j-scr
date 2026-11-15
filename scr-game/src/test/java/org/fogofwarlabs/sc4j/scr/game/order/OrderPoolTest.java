package org.fogofwarlabs.sc4j.scr.game.order;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatSchema;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.OrdersDat;
import org.fogofwarlabs.sc4j.scr.game.order.OrderPool.Order;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitMemoryScenario;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks target field storage, shared queue links, counter wrap and the capacity tests: the free order count, the Die order capacity test and the
 * nearly full pool.
 */
final class OrderPoolTest {
    /**
     * The order limit of the capacity tests.
     */
    private static final int ORDER_LIMIT = 3;
    /**
     * The order limit of the classic limits.
     */
    private static final int CLASSIC_ORDER_LIMIT = 2000;
    /**
     * The order limit of the extended limits.
     */
    private static final int EXTENDED_ORDER_LIMIT = 4000;
    /**
     * An order limit whose tenth is not a whole number.
     */
    private static final int UNEVEN_ORDER_LIMIT = 3999;
    /**
     * The allocated orders from which a pool of the classic order limit is nearly full.
     */
    private static final int CLASSIC_NEARLY_FULL_COUNT = 1800;
    /**
     * The allocated orders from which a pool of the extended or the uneven order limit is nearly full.
     */
    private static final int EXTENDED_NEARLY_FULL_COUNT = 3600;
    /**
     * The order limits of the nearly full test, each with the allocated count from which the pool is nearly full.
     */
    private static final int @NotNull [] @NotNull [] NEARLY_FULL_CASES = {
            {CLASSIC_ORDER_LIMIT, CLASSIC_NEARLY_FULL_COUNT},
            {EXTENDED_ORDER_LIMIT, EXTENDED_NEARLY_FULL_COUNT},
            {UNEVEN_ORDER_LIMIT, EXTENDED_NEARLY_FULL_COUNT}
    };
    /**
     * The length of the classic chain of the free-list commit in the nearly full test.
     */
    private static final int COMMITTED_CHAIN_LENGTH = 10;
    /**
     * The highlighted queued orders from which a unit refuses the Die order.
     */
    private static final int HIGHLIGHTED_ORDER_CAPACITY = 8;
    /**
     * The offset of the highlighted order count BYTE in the unit record.
     */
    private static final int HIGHLIGHTED_ORDER_COUNT_OFFSET = 0xC8;
    /**
     * The highest highlighted order count that the count BYTE holds.
     */
    private static final int FULL_HIGHLIGHTED_ORDER_COUNT = 255;
    /**
     * The number of order types of the Highlight column of the target field test.
     */
    private static final int HIGHLIGHT_ORDER_COUNT = 256;
    /**
     * An order id past the BYTE range, which the pool stores as {@code 2}.
     */
    private static final int RAW_ORDER_IDENTIFIER = 258;
    /**
     * A target type past the WORD range, which the pool stores as {@code 1}.
     */
    private static final int RAW_TARGET_TYPE = 65537;
    /**
     * A target position outside the WORD range on both axes, which the pool stores as {@code 65535} and {@code 2}.
     */
    private static final int RAW_TARGET_POSITION_X = -1;
    private static final int RAW_TARGET_POSITION_Y = 65538;
    /**
     * The target position of the second order of the target field test.
     */
    private static final int SECOND_TARGET_POSITION_X = 5;
    private static final int SECOND_TARGET_POSITION_Y = 6;
    /**
     * The order capacity of the target field test.
     */
    private static final int TARGET_FIELD_CAPACITY = 4;
    /**
     * The order capacities of the highlighted count wrap test and of the front enqueue test.
     */
    private static final int WRAP_CAPACITY = 1;
    private static final int FRONT_ENQUEUE_CAPACITY = 2;
    /**
     * The number of order types of the Highlight column of the wrap and front enqueue tests: a highlighted and an unhighlighted type.
     */
    private static final int HIGHLIGHT_CASE_ORDER_COUNT = 2;
    /**
     * The highlighted and the unhighlighted order type of the wrap and front enqueue tests.
     */
    private static final int HIGHLIGHTED_CASE_ORDER = 0;
    private static final int UNHIGHLIGHTED_CASE_ORDER = 1;
    /**
     * The Highlight column of the wrap and front enqueue tests: {@code 0} for {@link #HIGHLIGHTED_CASE_ORDER} and {@code 65535} for
     * {@link #UNHIGHLIGHTED_CASE_ORDER}.
     */
    private static final byte @NotNull [] HIGHLIGHT_CASE_COLUMN = {0, 0, -1, -1};
    /**
     * The name of the Highlight column of the order tables.
     */
    private static final @NotNull String HIGHLIGHT_COLUMN = "Highlight";
    /**
     * The order id, target type and target position that the pool stores for {@link #RAW_ORDER_IDENTIFIER}, {@link #RAW_TARGET_TYPE},
     * {@link #RAW_TARGET_POSITION_X} and {@link #RAW_TARGET_POSITION_Y}.
     */
    private static final int STORED_ORDER_IDENTIFIER = 2;
    private static final int STORED_TARGET_TYPE = 1;
    private static final int STORED_TARGET_POSITION_X = 65535;
    private static final int STORED_TARGET_POSITION_Y = 2;
    /**
     * The highlighted order count after the three orders of the target field test are enqueued.
     */
    private static final int ENQUEUED_HIGHLIGHTED_ORDER_COUNT = 3;
    /**
     * The pool indices of the free list of the target field test after its three orders are removed.
     */
    private static final int @NotNull [] RECYCLED_FREE_ORDER = {1, 3, 0, 2};
    /**
     * The pool indices of the ordered unit and of the target unit.
     */
    private static final int UNIT_POOL_INDEX = 1;
    private static final int TARGET_POOL_INDEX = 2;

    private static void allocateOrders(@NotNull OrderPool pool, int count) {
        for (int order = 0; order < count; order++) {
            Assertions.assertNotNull(pool.allocate(ScrOrderIds.PLAYER_GUARD, ScrUnitIds.NONE, 0, 0, null));
        }
    }


    @Test
    void pooled_orders_retain_exact_target_fields_and_recycle_after_free_head() throws IOException {
        OrderPool pool = new OrderPool(TARGET_FIELD_CAPACITY);
        Unit unit = UnitTestUtils.createUnit(UNIT_POOL_INDEX);
        Unit target = UnitTestUtils.createUnit(TARGET_POOL_INDEX);
        DatTable orders = new DatSchema().fieldU16(HIGHLIGHT_COLUMN, HIGHLIGHT_ORDER_COUNT).parse(new byte[HIGHLIGHT_ORDER_COUNT * Short.BYTES]);
        Order first = pool.allocate(RAW_ORDER_IDENTIFIER, RAW_TARGET_TYPE, RAW_TARGET_POSITION_X, RAW_TARGET_POSITION_Y, target);

        Assertions.assertNotNull(first);
        Assertions.assertEquals(0, first.getPoolIndex());
        Assertions.assertEquals(STORED_ORDER_IDENTIFIER, first.getOrderId());
        Assertions.assertEquals(STORED_TARGET_TYPE, first.getUnitType());
        Assertions.assertEquals(STORED_TARGET_POSITION_X, first.getTargetX());
        Assertions.assertEquals(STORED_TARGET_POSITION_Y, first.getTargetY());
        Assertions.assertSame(target, first.getTargetUnit());

        pool.enqueue(unit, first, null, OrdersDat.createTypeTable(orders));

        Order second = pool.allocate(ScrOrderIds.PLAYER_GUARD, ScrUnitIds.NONE, SECOND_TARGET_POSITION_X, SECOND_TARGET_POSITION_Y, null);
        Order third = pool.allocate(ScrOrderIds.TURRET_GUARD, ScrUnitIds.NONE, 0, 0, null);

        Assertions.assertNotNull(second);
        Assertions.assertNotNull(third);

        pool.enqueue(unit, second, null, OrdersDat.createTypeTable(orders));
        pool.enqueue(unit, third, first, OrdersDat.createTypeTable(orders));

        Assertions.assertSame(first, unit.getFirstOrder());
        Assertions.assertSame(third, first.getNext());
        Assertions.assertSame(second, third.getNext());
        Assertions.assertSame(third, second.getPrevious());
        Assertions.assertSame(second, unit.getLastOrder());
        Assertions.assertEquals(ENQUEUED_HIGHLIGHTED_ORDER_COUNT, UnitMemoryScenario.of(unit).readMemoryUnsignedByte(HIGHLIGHTED_ORDER_COUNT_OFFSET));

        pool.remove(unit, third, OrdersDat.createTypeTable(orders));
        pool.remove(unit, first, OrdersDat.createTypeTable(orders));
        pool.remove(unit, second, OrdersDat.createTypeTable(orders));

        Assertions.assertNull(unit.getFirstOrder());
        Assertions.assertNull(unit.getLastOrder());
        Assertions.assertEquals(0, UnitMemoryScenario.of(unit).readMemoryUnsignedByte(HIGHLIGHTED_ORDER_COUNT_OFFSET));
        Assertions.assertEquals(0, pool.getAllocatedCount());

        Order member = pool.getFirstFreeOrder();

        for (int poolIndex : RECYCLED_FREE_ORDER) {
            Assertions.assertNotNull(member);
            Assertions.assertEquals(poolIndex, member.getPoolIndex());

            member = member.getNext();
        }

        Assertions.assertNull(member);
        Assertions.assertSame(third, pool.getLastFreeOrder());
        Assertions.assertSame(target, first.getTargetUnit());
    }

    @Test
    void highlighted_order_count_wraps_as_byte_and_clamps_underflow_unconditionally() throws IOException {
        OrderPool pool = new OrderPool(WRAP_CAPACITY);
        Unit unit = UnitTestUtils.createUnit(UNIT_POOL_INDEX);
        DatTable orders = new DatSchema().fieldU16(HIGHLIGHT_COLUMN, HIGHLIGHT_CASE_ORDER_COUNT).parse(HIGHLIGHT_CASE_COLUMN.clone());
        Order order = pool.allocate(HIGHLIGHTED_CASE_ORDER, ScrUnitIds.NONE, 0, 0, null);

        Assertions.assertNotNull(order);

        Assertions.assertNull(pool.allocate(HIGHLIGHTED_CASE_ORDER, ScrUnitIds.NONE, 0, 0, null));

        UnitMemoryScenario.of(unit).writeMemoryByte(HIGHLIGHTED_ORDER_COUNT_OFFSET, FULL_HIGHLIGHTED_ORDER_COUNT);

        pool.enqueue(unit, order, null, OrdersDat.createTypeTable(orders));

        Assertions.assertEquals(0, UnitMemoryScenario.of(unit).readMemoryUnsignedByte(HIGHLIGHTED_ORDER_COUNT_OFFSET));

        pool.remove(unit, order, OrdersDat.createTypeTable(orders));

        Assertions.assertEquals(0, UnitMemoryScenario.of(unit).readMemoryUnsignedByte(HIGHLIGHTED_ORDER_COUNT_OFFSET));

        order = pool.allocate(UNHIGHLIGHTED_CASE_ORDER, ScrUnitIds.NONE, 0, 0, null);

        Assertions.assertNotNull(order);

        pool.enqueue(unit, order, null, OrdersDat.createTypeTable(orders));

        UnitMemoryScenario.of(unit).writeMemoryByte(HIGHLIGHTED_ORDER_COUNT_OFFSET, FULL_HIGHLIGHTED_ORDER_COUNT);

        pool.remove(unit, order, OrdersDat.createTypeTable(orders));

        Assertions.assertEquals(0, UnitMemoryScenario.of(unit).readMemoryUnsignedByte(HIGHLIGHTED_ORDER_COUNT_OFFSET));
        Assertions.assertSame(order, pool.getFirstFreeOrder());
        Assertions.assertSame(order, pool.getLastFreeOrder());
    }

    @Test
    void enqueue_front_should_prepend_orders_and_preserve_both_queue_endpoints() throws IOException {
        OrderPool pool = new OrderPool(FRONT_ENQUEUE_CAPACITY);
        Unit unit = UnitTestUtils.createUnit(UNIT_POOL_INDEX);
        DatTable orders = new DatSchema().fieldU16(HIGHLIGHT_COLUMN, HIGHLIGHT_CASE_ORDER_COUNT).parse(HIGHLIGHT_CASE_COLUMN.clone());
        Order tail = pool.allocate(HIGHLIGHTED_CASE_ORDER, ScrUnitIds.NONE, 0, 0, null);
        Order head = pool.allocate(UNHIGHLIGHTED_CASE_ORDER, ScrUnitIds.NONE, 0, 0, null);

        Assertions.assertNotNull(tail);
        Assertions.assertNotNull(head);

        pool.enqueueFront(unit, tail, OrdersDat.createTypeTable(orders));
        pool.enqueueFront(unit, head, OrdersDat.createTypeTable(orders));

        Assertions.assertSame(head, unit.getFirstOrder());
        Assertions.assertSame(tail, unit.getLastOrder());
        Assertions.assertSame(tail, head.getNext());
        Assertions.assertSame(head, tail.getPrevious());
        Assertions.assertNull(head.getPrevious());
        Assertions.assertNull(tail.getNext());
        Assertions.assertEquals(1, unit.getHighlightedOrderCount());

        pool.remove(unit, head, OrdersDat.createTypeTable(orders));

        Assertions.assertSame(tail, unit.getFirstOrder());
        Assertions.assertNull(tail.getPrevious());
        Assertions.assertEquals(1, unit.getHighlightedOrderCount());

        pool.remove(unit, tail, OrdersDat.createTypeTable(orders));

        Assertions.assertNull(unit.getFirstOrder());
        Assertions.assertNull(unit.getLastOrder());
        Assertions.assertEquals(0, pool.getAllocatedCount());
    }

    @Test
    void get_free_count_should_subtract_the_allocated_orders_from_the_order_limit() {
        OrderPool pool = new OrderPool(ORDER_LIMIT);

        Assertions.assertEquals(ORDER_LIMIT, pool.getFreeCount());

        pool.allocate(ScrOrderIds.PLAYER_GUARD, ScrUnitIds.NONE, 0, 0, null);

        Assertions.assertEquals(ORDER_LIMIT - 1, pool.getFreeCount());
    }

    @Test
    void can_queue_die_order_should_require_fewer_than_eight_highlighted_orders_and_a_free_order() {
        OrderPool pool = new OrderPool(ORDER_LIMIT);
        Unit unit = UnitTestUtils.createUnit(UNIT_POOL_INDEX);

        unit.setHighlightedOrderCount(HIGHLIGHTED_ORDER_CAPACITY - 1);

        Assertions.assertTrue(pool.canQueueDieOrder(unit));

        unit.setHighlightedOrderCount(HIGHLIGHTED_ORDER_CAPACITY);

        Assertions.assertFalse(pool.canQueueDieOrder(unit), "Eight highlighted queued orders refuse the Die order.");

        unit.setHighlightedOrderCount(0);

        for (int order = 0; order < ORDER_LIMIT; order++) {
            pool.allocate(ScrOrderIds.PLAYER_GUARD, ScrUnitIds.NONE, 0, 0, null);
        }

        Assertions.assertFalse(pool.canQueueDieOrder(unit), "A pool without a free order refuses the Die order.");
    }

    @Test
    void is_nearly_full_should_hold_from_the_native_capacity_less_its_truncated_tenth() {
        for (int[] nearlyFullCase : NEARLY_FULL_CASES) {
            OrderPool pool = new OrderPool(nearlyFullCase[0]);

            allocateOrders(pool, nearlyFullCase[1] - 1);

            Assertions.assertFalse(pool.isNearlyFull(), "Order limit " + nearlyFullCase[0]);

            allocateOrders(pool, 1);

            Assertions.assertTrue(pool.isNearlyFull(), "Order limit " + nearlyFullCase[0]);
        }
    }

    @Test
    void is_nearly_full_should_keep_the_native_capacity_after_a_free_list_commit() {
        OrderPool pool = new OrderPool(CLASSIC_ORDER_LIMIT);

        allocateOrders(pool, CLASSIC_NEARLY_FULL_COUNT - 1);
        pool.expandFreeList(COMMITTED_CHAIN_LENGTH);

        Assertions.assertFalse(pool.isNearlyFull());

        allocateOrders(pool, 1);

        Assertions.assertTrue(pool.isNearlyFull());
    }
}
