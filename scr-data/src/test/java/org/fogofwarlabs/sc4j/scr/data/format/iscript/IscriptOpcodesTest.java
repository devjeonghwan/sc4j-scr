package org.fogofwarlabs.sc4j.scr.data.format.iscript;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests the operand decoding and operand sizes of {@link IscriptOpcodes} inside a supplied byte range.
 */
final class IscriptOpcodesTest {
    private static final int MAXIMUM_OPCODE = 0x44;
    private static final int PLAYFRAM = 0x00;
    private static final int IMGOL = 0x08;
    private static final int END = 0x16;
    private static final int PLAYSNDRAND = 0x19;
    private static final int ATTACKMELEE = 0x1C;

    @Test
    void decode_operands_should_decode_signed_coordinates_and_words_inside_supplied_range() {
        byte[] data = {(byte) 0xff, 0x34, 0x12, (byte) 0x80, 0x7f, (byte) 0xee};

        Assertions.assertArrayEquals(new int[]{0x1234, -128, 127}, IscriptOpcodes.decodeOperands(IMGOL, data, 1, 5));
        Assertions.assertEquals(4, IscriptOpcodes.getOperandSize(IMGOL, data, 1, 5));
        Assertions.assertNull(IscriptOpcodes.decodeOperands(IMGOL, data, 1, 4));
        Assertions.assertArrayEquals(new int[0], IscriptOpcodes.decodeOperands(END, data, 5, 5));
        Assertions.assertNull(IscriptOpcodes.decodeOperands(MAXIMUM_OPCODE + 1, data, 1, 5));
    }

    @Test
    void decode_operands_should_bound_counted_sounds_without_reading_past_limit() {
        byte[] data = {0x7f, 2, 0x34, 0x12, (byte) 0xff, (byte) 0xff, 0};

        for (int opcode : new int[]{PLAYSNDRAND, ATTACKMELEE}) {
            Assertions.assertArrayEquals(new int[]{2, 0x1234, 0xffff}, IscriptOpcodes.decodeOperands(opcode, data, 1, 6));
            Assertions.assertNull(IscriptOpcodes.decodeOperands(opcode, data, 1, 5));
            Assertions.assertNull(IscriptOpcodes.decodeOperands(opcode, data, 1, 1));
            Assertions.assertEquals(5, IscriptOpcodes.getOperandSize(opcode, data, 1, 6));
            Assertions.assertEquals(-1, IscriptOpcodes.getOperandSize(opcode, data, 1, 1));
            Assertions.assertArrayEquals(new int[]{0}, IscriptOpcodes.decodeOperands(opcode, data, 6, 7));
        }
    }

    @Test
    void decode_operands_should_keep_unsigned_maximum_count_and_reject_invalid_ranges() {
        byte[] data = new byte[513];

        data[1]   = (byte) 255;
        data[510] = (byte) 0xfe;
        data[511] = (byte) 0xff;

        int[] operands = IscriptOpcodes.decodeOperands(PLAYSNDRAND, data, 1, data.length);

        Assertions.assertNotNull(operands);
        Assertions.assertEquals(256, operands.length);
        Assertions.assertEquals(255, operands[0]);
        Assertions.assertEquals(0xfffe, operands[255]);
        Assertions.assertEquals(511, IscriptOpcodes.getOperandSize(PLAYSNDRAND, data, 1, data.length));
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> IscriptOpcodes.decodeOperands(PLAYFRAM, data, -1, 2));
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> IscriptOpcodes.decodeOperands(PLAYFRAM, data, 3, 2));
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> IscriptOpcodes.getOperandSize(PLAYSNDRAND, data, 0, data.length + 1));
    }
}
