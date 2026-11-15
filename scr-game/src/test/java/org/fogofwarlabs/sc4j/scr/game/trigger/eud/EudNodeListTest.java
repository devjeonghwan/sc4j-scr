package org.fogofwarlabs.sc4j.scr.game.trigger.eud;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks node-list links, sentinels and hostless storage.
 */
final class EudNodeListTest {
    private static final int LIST_POINTER = 0x68C148;
    private static final int FIRST_NODE = 0x6DE000;
    private static final int SECOND_NODE = 0x6DE4C0;
    private static final int TENTH_NODE = 0x7657C0;
    private static final int BUFFER_FIELD = 0x10;

    @Test
    void node_lists_hold_their_links_sentinels_and_buffers() {
        ClassicAddressSpace space = new ClassicAddressSpace();

        space.addAll(ClassicDynamicRegionsUtils.create(null));

        Assertions.assertEquals(FIRST_NODE, space.readDword(LIST_POINTER), "The list pointer holds the first node.");
        Assertions.assertEquals(SECOND_NODE, space.readDword(FIRST_NODE), "+0x00: the next node.");
        Assertions.assertEquals(0x6DE980, space.readDword(FIRST_NODE + BUFFER_FIELD), "+0x10: the node's 0x109A0 buffer.");
        Assertions.assertEquals(0xDEADBEE2, space.readDword(FIRST_NODE + 0x14));
        Assertions.assertEquals(0xDEADBEEF, space.readDword(FIRST_NODE + 0x2A));
        Assertions.assertEquals(0xDEADBEE0, space.readDword(FIRST_NODE + 0x2E));
        Assertions.assertEquals(0x6EF320, space.readDword(SECOND_NODE), "Node 2 follows the buffer of node 0.");
        Assertions.assertEquals(0x6EF7E0, space.readDword(SECOND_NODE + BUFFER_FIELD));
        Assertions.assertEquals(0, space.readDword(TENTH_NODE), "The tenth node is the last: no links.");
        Assertions.assertEquals(0, space.readDword(TENTH_NODE + 0x14));
        Assertions.assertEquals(0x19172910, space.readDword(0x19160BE0), "The 0x68C1F0 list ends at its ninth node.");
        Assertions.assertEquals(0, space.readDword(0x19172910));

        space.writeDword(0x96D920 + 0x40, 1234);

        Assertions.assertEquals(1234, space.readDword(0x96D920 + 0x40), "A node's bytes are its own host buffer.");
    }

    @Test
    void hostless_regions_read_zero_and_ignore_writes() {
        ClassicAddressSpace space = new ClassicAddressSpace();

        space.addAll(ClassicDynamicRegionsUtils.create(null));

        for (int address : new int[]{0x50CDC0, 0x512708, 0x6CEFF8, 0x6D1200, 0x1505E670}) {
            space.writeDword(address, -1);

            Assertions.assertEquals(0, space.readDword(address), String.format("0x%X", address));
        }
    }
}
