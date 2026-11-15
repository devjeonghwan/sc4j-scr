package org.fogofwarlabs.sc4j.scr.game.unit;

import org.fogofwarlabs.sc4j.scr.data.format.dat.DatSchema;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Tests native creation comparisons, including sign-flag and WORD-width behavior.
 */
final class UnitCreationBoundsUtilsTest {
    /**
     * The four extent WORDs of one unit type.
     */
    private static final int EXTENTS_SIZE = 4 * Short.BYTES;

    private static @NotNull DatTable createExtents(int left, int upper, int right, int lower) throws IOException {
        DatSchema schema = new DatSchema().fieldU16("Unit Size Left", 1).fieldU16("Unit Size Up", 1)
                                          .fieldU16("Unit Size Right", 1).fieldU16("Unit Size Down", 1);
        ByteBuffer contents = ByteBuffer.allocate(EXTENTS_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        contents.putShort((short) left).putShort((short) upper).putShort((short) right).putShort((short) lower);

        return schema.parse(contents.array());
    }


    @Test
    void is_within_creation_bounds_should_accept_only_coordinates_inside_all_four_edges() throws IOException {
        DatTable unitTypes = createExtents(3, 5, 7, 11);
        GameMap map = new GameMap(4, 3, 0, new int[12]);

        Assertions.assertTrue(UnitCreationBoundsUtils.isWithinCreationBounds(UnitsDat.createTypeTable(unitTypes), map, 0, 3, 5));
        Assertions.assertTrue(UnitCreationBoundsUtils.isWithinCreationBounds(UnitsDat.createTypeTable(unitTypes), map, 0, 120, 84));
        Assertions.assertFalse(UnitCreationBoundsUtils.isWithinCreationBounds(UnitsDat.createTypeTable(unitTypes), map, 0, 2, 5));
        Assertions.assertFalse(UnitCreationBoundsUtils.isWithinCreationBounds(UnitsDat.createTypeTable(unitTypes), map, 0, 3, 4));
        Assertions.assertFalse(UnitCreationBoundsUtils.isWithinCreationBounds(UnitsDat.createTypeTable(unitTypes), map, 0, 121, 84));
        Assertions.assertFalse(UnitCreationBoundsUtils.isWithinCreationBounds(UnitsDat.createTypeTable(unitTypes), map, 0, 120, 85));
    }

    @Test
    void is_within_creation_bounds_should_read_extent_words_as_signed() throws IOException {
        DatTable unitTypes = createExtents(0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF);
        GameMap map = new GameMap(1, 1, 0, new int[1]);

        Assertions.assertTrue(UnitCreationBoundsUtils.isWithinCreationBounds(UnitsDat.createTypeTable(unitTypes), map, 0, -1, -1));
        Assertions.assertFalse(UnitCreationBoundsUtils.isWithinCreationBounds(UnitsDat.createTypeTable(unitTypes), map, 0, -2, -1));
        Assertions.assertFalse(UnitCreationBoundsUtils.isWithinCreationBounds(UnitsDat.createTypeTable(unitTypes), map, 0, -1, -2));
    }

    @Test
    void is_within_creation_bounds_should_accept_coordinates_whose_edges_overflow() throws IOException {
        GameMap map = new GameMap(1, 1, 0, new int[1]);
        DatTable lowerOverflow = createExtents(1, 1, 0, 0);
        DatTable upperOverflow = createExtents(0, 0, 1, 1);

        Assertions.assertTrue(UnitCreationBoundsUtils.isWithinCreationBounds(UnitsDat.createTypeTable(lowerOverflow), map, 0, Integer.MIN_VALUE,
                                                                             Integer.MIN_VALUE));
        Assertions.assertTrue(UnitCreationBoundsUtils.isWithinCreationBounds(UnitsDat.createTypeTable(upperOverflow), map, 0, Integer.MAX_VALUE,
                                                                             Integer.MAX_VALUE));
    }

    @Test
    void is_within_creation_bounds_should_use_low_word_pixel_dimensions() throws IOException {
        DatTable unitTypes = createExtents(0, 0, 0, 0);
        GameMap map = new GameMap(2049, 2049, 0, new int[0]);

        Assertions.assertTrue(UnitCreationBoundsUtils.isWithinCreationBounds(UnitsDat.createTypeTable(unitTypes), map, 0, 31, 31));
        Assertions.assertFalse(UnitCreationBoundsUtils.isWithinCreationBounds(UnitsDat.createTypeTable(unitTypes), map, 0, 32, 31));
        Assertions.assertFalse(UnitCreationBoundsUtils.isWithinCreationBounds(UnitsDat.createTypeTable(unitTypes), map, 0, 31, 32));
    }
}
