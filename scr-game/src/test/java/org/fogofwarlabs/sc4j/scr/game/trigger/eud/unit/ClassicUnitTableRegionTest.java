package org.fogofwarlabs.sc4j.scr.game.trigger.eud.unit;

import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicAddressSpace;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks the spare unit WORD of the classic unit table.
 */
final class ClassicUnitTableRegionTest {
    private static final int UNIT_TYPE = 0x64;
    private static final int WORD_MASK = 0xFFFF;

    @Test
    void unit_word_after_the_type_is_stored_as_the_spare_word() {
        UnitPool pool = new UnitPool(UnitPool.CLASSIC_UNIT_CAPACITY);
        ClassicAddressSpace space = new ClassicAddressSpace();

        space.add(new ClassicUnitTableRegion(pool));

        int address = ClassicUnitTableRegion.BASE + 3 * ClassicUnitTableRegion.ENTRY_SIZE + UNIT_TYPE;
        int type = space.readDword(address) & WORD_MASK;

        space.writeDword(address, type | 0x1234_0000);

        Assertions.assertEquals(0x1234, pool.getUnit(4).getSpareWord());
        Assertions.assertEquals(type | 0x1234_0000, space.readDword(address));
    }
}
