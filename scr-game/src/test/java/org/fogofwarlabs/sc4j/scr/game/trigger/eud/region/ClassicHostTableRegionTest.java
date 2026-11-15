package org.fogofwarlabs.sc4j.scr.game.trigger.eud.region;

import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicAddressSpace;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks that a host table region serves and stores the live host table.
 */
final class ClassicHostTableRegionTest {
    @Test
    void flee_distances_read_and_write_the_live_host_table() {
        int[] distances = {192, 128, 64, 32, 16};
        ClassicAddressSpace space = new ClassicAddressSpace();

        space.add(new ClassicHostTableRegion(ClassicHostTableRegion.FLEE_DISTANCES, distances));

        Assertions.assertEquals(128, space.readDword(ClassicHostTableRegion.FLEE_DISTANCES + Integer.BYTES));

        space.writeDword(ClassicHostTableRegion.FLEE_DISTANCES + 4 * Integer.BYTES, 7);

        Assertions.assertEquals(7, distances[4], "The write changes the host table the flight reads.");
        Assertions.assertEquals(64 >>> 8 | 32 << 24, space.readDword(ClassicHostTableRegion.FLEE_DISTANCES + 2 * Integer.BYTES + 1));
    }
}
