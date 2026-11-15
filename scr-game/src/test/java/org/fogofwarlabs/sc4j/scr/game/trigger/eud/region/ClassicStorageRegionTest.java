package org.fogofwarlabs.sc4j.scr.game.trigger.eud.region;

import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicAddressSpace;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks that the image, bullet and order tables are storage.
 */
final class ClassicStorageRegionTest {
    @Test
    void image_bullet_and_order_tables_are_storage() {
        ClassicAddressSpace space = new ClassicAddressSpace();

        space.add(ClassicStorageRegion.createImageTableRegion());
        space.add(ClassicStorageRegion.createBulletTableRegion());
        space.add(ClassicStorageRegion.createOrderTableRegion());

        Assertions.assertEquals(0, space.readDword(ClassicStorageRegion.IMAGE_TABLE + 64 * 4999 + 60));

        space.writeDword(ClassicStorageRegion.BULLET_TABLE + ClassicStorageRegion.BULLET_ENTRY_SIZE * 3, 42);

        Assertions.assertEquals(42, space.readDword(ClassicStorageRegion.BULLET_TABLE + 112 * 3));

        space.writeDword(ClassicStorageRegion.TYPE_3F_TABLE, -1);

        Assertions.assertEquals(-1, space.readDword(ClassicStorageRegion.TYPE_3F_TABLE));
    }
}
