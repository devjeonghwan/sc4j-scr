package org.fogofwarlabs.sc4j.scr.game.trigger.eud.unit;

import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpritePool;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicAddressSpace;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks the free links, identity and dimension writes and the storage shadows of the classic sprite table.
 */
final class ClassicSpriteTableRegionTest {
    /**
     * The address of the first entry of the classic sprite table.
     */
    private static final int SPRITE_TABLE_ADDRESS = 0x629D98;
    /**
     * The size of a classic sprite table entry.
     */
    private static final int SPRITE_ENTRY_SIZE = 36;

    @Test
    void free_sprite_links_store_entry_starts_and_shadow_other_values() {
        SpritePool pool = new SpritePool(ClassicSpriteTableRegion.ENTRY_COUNT);
        ClassicAddressSpace space = new ClassicAddressSpace();

        space.add(new ClassicSpriteTableRegion(pool));

        int next = pool.getNextFreeIndex(0);

        Assertions.assertEquals(SPRITE_TABLE_ADDRESS + next * SPRITE_ENTRY_SIZE,
                                space.readDword(ClassicSpriteTableRegion.BASE + 0x04),
                                "A free sprite links to the next free one.");

        space.writeDword(ClassicSpriteTableRegion.BASE + 0x04, ClassicSpriteTableRegion.BASE + 7 * ClassicSpriteTableRegion.ENTRY_SIZE);

        Assertions.assertEquals(7, pool.getNextFreeIndex(0), "A write of an entry start stores that sprite.");

        space.writeDword(ClassicSpriteTableRegion.BASE + 0x04, ClassicSpriteTableRegion.BASE + 7 * ClassicSpriteTableRegion.ENTRY_SIZE + 2);

        Assertions.assertEquals(7, pool.getNextFreeIndex(0));
        Assertions.assertEquals(SPRITE_TABLE_ADDRESS + 7 * SPRITE_ENTRY_SIZE + 2,
                                space.readDword(ClassicSpriteTableRegion.BASE + 0x04),
                                "Other values stay in the shadow.");
        Assertions.assertEquals(0,
                                space.readDword(ClassicSpriteTableRegion.BASE + ClassicSpriteTableRegion.ENTRY_SIZE + 0x14),
                                "The position is storage of the sub-region, zero at first.");

        space.writeDword(ClassicSpriteTableRegion.BASE + ClassicSpriteTableRegion.ENTRY_SIZE + 0x14, 0x00400020);

        Assertions.assertEquals(0x00400020, space.readDword(ClassicSpriteTableRegion.BASE + ClassicSpriteTableRegion.ENTRY_SIZE + 0x14));
        Assertions.assertEquals(0, space.readDword(ClassicSpriteTableRegion.BASE + 0x08), "No selection index, id or owner yet.");
    }

    @Test
    void identity_writes_store_valid_ranges_and_dimension_writes_clear() {
        SpritePool pool = new SpritePool(ClassicSpriteTableRegion.ENTRY_COUNT);
        ClassicAddressSpace space = new ClassicAddressSpace();

        space.add(new ClassicSpriteTableRegion(pool));

        Sprite sprite = pool.getSprite(3);
        int identity = ClassicSpriteTableRegion.BASE + 3 * ClassicSpriteTableRegion.ENTRY_SIZE + 0x08;

        space.writeDword(identity, 0x0205_00E3);

        Assertions.assertEquals(0xE3, sprite.getSpriteTypeId());
        Assertions.assertEquals(5, sprite.getOwner());
        Assertions.assertEquals(2, sprite.getSelectionIndex());

        space.writeDword(identity, 0x010C_0100);

        Assertions.assertEquals(0xE3, sprite.getSpriteTypeId(), "Ids from 0xE4 stay in the shadow.");
        Assertions.assertEquals(5, sprite.getOwner(), "Owners from 12 stay in the shadow.");
        Assertions.assertEquals(1, sprite.getSelectionIndex());
        Assertions.assertEquals(0x010C_0100, space.readDword(identity), "The shadows are read while their flags are set.");

        space.writeDword(identity, 0x0003_0010);

        Assertions.assertEquals(0x0003_0010, space.readDword(identity));

        int dimensions = ClassicSpriteTableRegion.BASE + 3 * ClassicSpriteTableRegion.ENTRY_SIZE + 0x10;

        Assertions.assertEquals(3, space.readDword(dimensions), "The pool numbers the WORD of every entry after the first.");

        sprite.setRawDimensions(9, 32, 24);

        Assertions.assertEquals(9 | 32 << 16 | 24 << 24, space.readDword(dimensions));

        space.writeDword(dimensions, 0x1234_5678);

        Assertions.assertEquals(0, space.readDword(dimensions), "A dimension write clears the WORD, width and height whatever the value.");
    }
}
