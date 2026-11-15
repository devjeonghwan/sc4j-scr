package org.fogofwarlabs.sc4j.scr.game.trigger.eud;

import org.fogofwarlabs.sc4j.scr.game.trigger.eud.unit.ClassicUnitTableRegion;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks entry-start pointers, pointer shadows and failed offsets.
 */
final class EudUnitPointerTest {
    private static final int ORDER_TARGET = 0x5C;
    private static final int SUBUNIT = 0x70;
    private static final int ORDER_QUEUE_HEAD = 0x74;
    private static final int UNSERVED_FIELD = 0x140;
    /**
     * The address of the first entry of the classic unit table.
     */
    private static final int UNIT_TABLE_ADDRESS = 0x59CCA8;
    /**
     * The size of a classic unit table entry.
     */
    private static final int UNIT_ENTRY_SIZE = 336;

    @Test
    void pointer_writes_store_units_or_shadow_values() {
        UnitPool pool = new UnitPool(UnitPool.CLASSIC_UNIT_CAPACITY);
        ClassicAddressSpace space = new ClassicAddressSpace();

        space.add(new ClassicUnitTableRegion(pool));

        Unit first = pool.getUnit(1);
        Unit second = pool.getUnit(2);
        int orderTarget = ClassicUnitTableRegion.BASE + ORDER_TARGET;

        space.writeDword(orderTarget, ClassicUnitTableRegion.BASE + ClassicUnitTableRegion.ENTRY_SIZE);

        Assertions.assertSame(second, first.getOrderTargetUnit());
        Assertions.assertEquals(UNIT_TABLE_ADDRESS + UNIT_ENTRY_SIZE, space.readDword(orderTarget));

        space.writeDword(orderTarget, ClassicUnitTableRegion.BASE + ClassicUnitTableRegion.ENTRY_SIZE + 4);

        Assertions.assertSame(second, first.getOrderTargetUnit(), "Not an entry start: only the shadow changes.");
        Assertions.assertEquals(UNIT_TABLE_ADDRESS + UNIT_ENTRY_SIZE + 4, space.readDword(orderTarget), "The shadow is read while its flag is set.");

        space.writeDword(orderTarget, 0);

        Assertions.assertNull(first.getOrderTargetUnit());
        Assertions.assertEquals(0, space.readDword(orderTarget));

        space.writeDword(ClassicUnitTableRegion.BASE + ClassicUnitTableRegion.ENTRY_SIZE + SUBUNIT, ClassicUnitTableRegion.BASE);

        Assertions.assertSame(first, second.getSubunit());
    }

    @Test
    void unserved_unit_offsets_fail_trigger_access_and_notify_the_listener() {
        UnitPool pool = new UnitPool(UnitPool.CLASSIC_UNIT_CAPACITY);
        ClassicAddressSpace space = new ClassicAddressSpace();

        space.add(new ClassicUnitTableRegion(pool));

        int[] failed = {0};

        space.setFailureListener(address -> failed[0] = address);

        Assertions.assertEquals(0, space.readForTrigger(ClassicUnitTableRegion.BASE + ORDER_QUEUE_HEAD),
                                "The order-queue head is not served natively.");
        Assertions.assertEquals(UNIT_TABLE_ADDRESS + ORDER_QUEUE_HEAD, failed[0]);
        Assertions.assertFalse(space.writeForTrigger(ClassicUnitTableRegion.BASE + ClassicUnitTableRegion.ENTRY_SIZE + UNSERVED_FIELD, 5));
        Assertions.assertEquals(UNIT_TABLE_ADDRESS + UNIT_ENTRY_SIZE + UNSERVED_FIELD, failed[0]);
    }
}
