package org.fogofwarlabs.sc4j.scr.data.format.dat;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Checks reads of DAT elements at any index through the game memory layout of the column blocks.
 * <p>
 * The layout is the contract: a read past an array has no bound check, so it returns the bytes that follow the array in the DAT memory.
 */
final class DatTableTest {
    private static final int ALIGNMENT = 16;
    private static final int BYTE_COUNT = 3;
    private static final int PAIR_COUNT = 2;
    /**
     * The game-memory offset of the DWORD block, after the bytes and their alignment gap.
     */
    private static final int FLAGS_OFFSET = ALIGNMENT;
    /**
     * The game-memory offset of the WORD pair block, after the DWORDs.
     */
    private static final int PAIRS_OFFSET = 2 * ALIGNMENT;
    /**
     * The game-memory offset of the signed byte block, after the WORD pairs and their alignment gap.
     */
    private static final int SIGNED_OFFSET = 3 * ALIGNMENT;

    /**
     * Creates a {@link DatTable} instance whose blocks lie at {@code 0} (three bytes), {@link #FLAGS_OFFSET} (two DWORDs), {@link #PAIRS_OFFSET} (two
     * WORD pairs) and {@link #SIGNED_OFFSET} (one signed byte) in the game memory.
     *
     * @param nativeLayout whether the schema declares the game memory layout
     * @return the created {@link DatTable} instance
     * @throws IOException if the content does not match the schema
     */
    private static @NotNull DatTable createTable(boolean nativeLayout) throws IOException {
        DatSchema schema = new DatSchema();

        if (nativeLayout) {
            schema.alignNativeBlocks(ALIGNMENT);
        }

        schema.fieldU8("Bytes", BYTE_COUNT).fieldU32("Flags", PAIR_COUNT).fieldU16x2("Width", "Height", PAIR_COUNT).fieldS8("Signed", 1);

        ByteBuffer data = ByteBuffer.allocate(schema.getExpectedSize()).order(ByteOrder.LITTLE_ENDIAN);

        data.put((byte) 1).put((byte) 2).put((byte) 0x80);
        data.putInt(0x11223344).putInt(0x55667788);
        data.putShort((short) 0x0102).putShort((short) 0x0304).putShort((short) 0x0506).putShort((short) 0x0708);
        data.put((byte) -2);

        return schema.parse(data.array());
    }


    @Test
    void read_native_element_should_read_the_array_inside_its_bounds() throws IOException {
        DatTable table = createTable(true);

        Assertions.assertEquals(0x55667788, table.readNativeElement("Flags", 1));
        Assertions.assertEquals(0x0708, table.readNativeElement("Height", 1));
        Assertions.assertEquals(-2, table.readNativeElement("Signed", 0));
    }

    @Test
    void read_native_element_should_read_alignment_gaps_as_zero() throws IOException {
        DatTable table = createTable(true);

        Assertions.assertEquals(0, table.readNativeElement("Bytes", BYTE_COUNT));
        Assertions.assertEquals(0, table.readNativeElement("Flags", PAIR_COUNT));
        Assertions.assertEquals(0, table.readNativeElement("Height", PAIR_COUNT));
    }

    @Test
    void read_native_element_should_read_the_bytes_of_the_neighboring_blocks() throws IOException {
        DatTable table = createTable(true);
        int firstPairFlagsIndex = (PAIRS_OFFSET - FLAGS_OFFSET) / Integer.BYTES;

        Assertions.assertEquals(0x44, table.readNativeElement("Bytes", FLAGS_OFFSET));
        Assertions.assertEquals(0x03040102, table.readNativeElement("Flags", firstPairFlagsIndex));
        Assertions.assertEquals(1, table.readNativeElement("Signed", -SIGNED_OFFSET));
        Assertions.assertEquals(-128, table.readNativeElement("Signed", BYTE_COUNT - 1 - SIGNED_OFFSET));
        Assertions.assertEquals(0x03040102, table.copy().readNativeElement("Flags", firstPairFlagsIndex));
    }

    @Test
    void read_native_element_should_reject_an_element_outside_the_blocks() throws IOException {
        DatTable table = createTable(true);

        Assertions.assertThrows(UnsupportedOperationException.class, () -> table.readNativeElement("Bytes", -1));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> table.readNativeElement("Signed", 1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> table.readNativeElement("Missing", 0));
    }

    @Test
    void read_native_element_should_read_only_the_array_without_a_memory_layout() throws IOException {
        DatTable table = createTable(false);

        Assertions.assertEquals(0x55667788, table.readNativeElement("Flags", 1));
        Assertions.assertThrows(ArrayIndexOutOfBoundsException.class, () -> table.readNativeElement("Bytes", BYTE_COUNT));
    }

    @Test
    void read_native_element_should_follow_block_alignments_and_reject_reference_blocks() throws IOException {
        DatSchema schema = new DatSchema().alignNativeBlocks(ALIGNMENT)
                                          .fieldU8("Bytes", BYTE_COUNT)
                                          .fieldU32("References", PAIR_COUNT)
                                          .fieldU8("Tail", 1)
                                          .alignNativeBlock("Tail", Integer.BYTES)
                                          .holdNativeReferences("References");
        DatTable table = schema.parse(new byte[]{1, 2, 3, 0, 0, 0, 0, 0, 0, 0, 0, 9});

        // The references take 8 bytes each from 16 to 32, and the 4-byte aligned tail follows them at 32.
        Assertions.assertEquals(0, table.readNativeElement("Bytes", ALIGNMENT - 1));
        Assertions.assertEquals(9, table.readNativeElement("Bytes", 2 * ALIGNMENT));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> table.readNativeElement("Bytes", ALIGNMENT));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> table.readNativeElement("References", PAIR_COUNT));
    }
}
