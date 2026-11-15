package org.fogofwarlabs.sc4j.scr.game.trigger.eud;

import org.fogofwarlabs.sc4j.scr.game.map.MapStringSection;
import org.fogofwarlabs.sc4j.scr.game.order.OrderPool;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpritePool;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.region.ClassicStorageRegion;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.unit.ClassicSpriteTableRegion;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Checks free-list snapshots, string-section chains and pool expansion.
 */
final class EudPoolHeadsTest {
    private static final int SPRITE_FIRST = 0x63FE30;
    private static final int SPRITE_LAST = 0x63FE34;
    private static final int ORDER_FIRST = 0x64B2E0;
    private static final int SPRITE_CAPACITY = 2500;
    private static final int ORDER_CAPACITY = 2000;
    private static final int STRING_NODE_LINK = 4;
    /**
     * The address of the first entry of the classic sprite table.
     */
    private static final int SPRITE_TABLE_ADDRESS = 0x629D98;
    /**
     * The size of a classic sprite table entry.
     */
    private static final int SPRITE_ENTRY_SIZE = 36;
    /**
     * The address of the classic order table.
     */
    private static final int ORDER_TABLE_ADDRESS = 0x6416A0;
    /**
     * The address of the map string section.
     */
    private static final int STRING_SECTION_ADDRESS = 0x191943C8;

    @Test
    void heads_start_at_the_free_list_and_a_written_head_commit_expands_the_pool() {
        SpritePool sprites = new SpritePool(ClassicSpriteTableRegion.ENTRY_COUNT);
        OrderPool orders = new OrderPool(ORDER_CAPACITY);

        ByteBuffer strings = ByteBuffer.allocate(0x100).order(ByteOrder.LITTLE_ENDIAN);

        strings.putInt(0x10 + STRING_NODE_LINK, ClassicDynamicRegionsUtils.STRING_SECTION_BASE + 0x20)
               .putInt(0x20 + STRING_NODE_LINK, ClassicDynamicRegionsUtils.STRING_SECTION_BASE + 0x30)
               .putInt(0x30 + STRING_NODE_LINK, 0);

        ClassicAddressSpace space = new ClassicAddressSpace();

        space.addAll(ClassicDynamicRegionsUtils.create(new MapStringSection(strings.array(), false)));

        ClassicPoolHeads heads = ClassicPoolHeads.create(null, null, sprites, orders);

        space.addAll(heads.getRegions());

        Assertions.assertEquals(SPRITE_TABLE_ADDRESS, space.readDword(SPRITE_FIRST));
        Assertions.assertEquals(SPRITE_TABLE_ADDRESS + SPRITE_ENTRY_SIZE, space.readDword(SPRITE_LAST));
        Assertions.assertEquals(ORDER_TABLE_ADDRESS, space.readDword(ORDER_FIRST));

        space.writeDword(SPRITE_FIRST, ClassicDynamicRegionsUtils.STRING_SECTION_BASE + 0x10);
        heads.commit(space);

        Assertions.assertEquals(SPRITE_CAPACITY + 4, sprites.getCapacity(), "Three nodes give four fresh sprites.");
        Assertions.assertEquals(SPRITE_CAPACITY, sprites.getFirstFreeIndex());
        Assertions.assertEquals(SPRITE_CAPACITY + 3, sprites.getNextFreeIndex(SPRITE_CAPACITY));
        Assertions.assertEquals(SPRITE_CAPACITY + 2, sprites.getNextFreeIndex(SPRITE_CAPACITY + 3));
        Assertions.assertEquals(SPRITE_CAPACITY + 1, sprites.getNextFreeIndex(SPRITE_CAPACITY + 2));
        Assertions.assertEquals(-1, sprites.getNextFreeIndex(SPRITE_CAPACITY + 1));
        Assertions.assertEquals(STRING_SECTION_ADDRESS + 0x10, space.readDword(SPRITE_FIRST), "The head keeps the written value.");
        Assertions.assertEquals(ORDER_CAPACITY, orders.getCapacity(), "Only the written list is committed.");

        space.writeDword(SPRITE_FIRST, ClassicStorageRegion.TYPE_3F_TABLE);
        space.addAll(List.of(ClassicStorageRegion.createOrderTableRegion()));
        heads.commit(space);

        Assertions.assertEquals(SPRITE_CAPACITY + 4, sprites.getCapacity());

        space.writeDword(ORDER_FIRST, 0);
        heads.commit(space);

        Assertions.assertEquals(ORDER_CAPACITY + 1, orders.getCapacity());
        Assertions.assertSame(orders.getOrder(ORDER_CAPACITY), orders.getFirstFreeOrder());
        Assertions.assertSame(orders.getOrder(ORDER_CAPACITY), orders.getLastFreeOrder());
        Assertions.assertEquals(ORDER_CAPACITY, orders.getNativeCapacity());
    }
}
