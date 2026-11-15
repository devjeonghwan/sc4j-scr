package org.fogofwarlabs.sc4j.scr.data.format.iscript;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Tests the iscript header layout: the identifier table, the WORD type and the animation offsets after the eight-byte header start.
 */
final class IscriptHeaderTest {
    private static final int PLAYFRAM = 0x00;
    private static final int END = 0x16;
    /**
     * The identifier table offset of the small test scripts.
     */
    private static final int TABLE_OFFSET = 64;
    /**
     * An identifier table offset beyond the {@code 16}-bit range.
     */
    private static final int WIDE_TABLE_OFFSET = 65540;
    private static final int DEATH_BLOCK_OFFSET = 52;
    private static final int PLAYFRAM_OPERAND = 0x1234;

    private static @NotNull ByteBuffer createScript(int tableOffset) {
        ByteBuffer data = IscriptFileScenario.createScript(tableOffset);

        data.putShort(IscriptFileScenario.ANIMATION_TABLE_POSITION + Short.BYTES, (short) DEATH_BLOCK_OFFSET);
        data.put(IscriptFileScenario.INIT_BLOCK_OFFSET, (byte) PLAYFRAM);
        data.putShort(IscriptFileScenario.INIT_BLOCK_OFFSET + 1, (short) PLAYFRAM_OPERAND);
        data.put(IscriptFileScenario.INIT_BLOCK_OFFSET + 1 + Short.BYTES, (byte) END);
        data.put(DEATH_BLOCK_OFFSET, (byte) END);

        return data;
    }


    @Test
    void find_header_should_read_initial_and_death_animations_at_eight_byte_header_boundary() throws IOException {
        ByteBuffer data = createScript(TABLE_OFFSET);

        IscriptFile script = IscriptFile.parse(data.array());
        IscriptHeader header = script.findHeader(IscriptFileScenario.SCRIPT_ID);

        Assertions.assertNotNull(header);
        Assertions.assertEquals(IscriptFileScenario.INIT_BLOCK_OFFSET, header.getAnimationOffset(0));
        Assertions.assertEquals(DEATH_BLOCK_OFFSET, header.getAnimationOffset(1));
        Assertions.assertEquals(PLAYFRAM_OPERAND, script.decodeInstruction(header.getAnimationOffset(0)).getOperands()[0]);
        Assertions.assertEquals(END, script.decodeInstruction(header.getAnimationOffset(1)).getOpcode());
    }

    @Test
    void parse_should_use_full_unsigned_table_pointer_and_word_type() throws IOException {
        ByteBuffer data = createScript(WIDE_TABLE_OFFSET);
        int type = 256;
        int lastAnimationOffset = 600;

        data.putShort(IscriptFileScenario.HEADER_TYPE_POSITION, (short) type);
        data.putShort(IscriptFileScenario.ANIMATION_TABLE_POSITION, (short) 0);
        data.putShort(IscriptFileScenario.ANIMATION_TABLE_POSITION + Short.BYTES, (short) 0);
        data.put(IscriptFileScenario.INIT_BLOCK_OFFSET, (byte) 0);
        data.putShort(IscriptFileScenario.INIT_BLOCK_OFFSET + 1, (short) 0);
        data.put(IscriptFileScenario.INIT_BLOCK_OFFSET + 1 + Short.BYTES, (byte) 0);
        data.put(DEATH_BLOCK_OFFSET, (byte) 0);
        data.putShort(IscriptFileScenario.ANIMATION_TABLE_POSITION + type * Short.BYTES, (short) lastAnimationOffset);
        data.put(lastAnimationOffset, (byte) END);

        IscriptHeader header = IscriptFile.parse(data.array()).findHeader(IscriptFileScenario.SCRIPT_ID);

        Assertions.assertNotNull(header);
        Assertions.assertEquals(type, header.getType());
        Assertions.assertEquals(lastAnimationOffset, header.getAnimationOffset(type));

        data.putInt(0, 0x80000000);

        Assertions.assertThrows(IOException.class, () -> IscriptFile.parse(data.array()));
    }

    @Test
    void get_animation_offset_should_read_past_declared_extent_and_keep_sentinel_comparison_order() throws IOException {
        ByteBuffer data = createScript(TABLE_OFFSET);

        data.putShort(IscriptFileScenario.ANIMATION_TABLE_POSITION + 2 * Short.BYTES, (short) DEATH_BLOCK_OFFSET);
        data.putShort(TABLE_OFFSET + 3 * Short.BYTES, (short) IscriptFileScenario.HEADER_OFFSET);

        IscriptFile script = IscriptFile.parse(data.array());

        Assertions.assertEquals(1, script.getHeaders().size());

        IscriptHeader header = script.findHeader(IscriptFileScenario.SCRIPT_ID);

        Assertions.assertNotNull(header);

        Assertions.assertEquals(0, header.getAnimationOffset(2));
        Assertions.assertEquals(DEATH_BLOCK_OFFSET, script.getAnimationOffset(IscriptFileScenario.SCRIPT_ID, 2));
        Assertions.assertEquals(IscriptFileScenario.INIT_BLOCK_OFFSET, script.getAnimationOffset(IscriptFileScenario.TABLE_TERMINATOR_ID, 0));
        Assertions.assertThrows(IOException.class, () -> script.getAnimationOffset(0x10007, 0));
        Assertions.assertThrows(IOException.class, () -> script.getAnimationOffset(IscriptFileScenario.SCRIPT_ID, 255));
        Assertions.assertThrows(IllegalArgumentException.class, () -> script.getAnimationOffset(IscriptFileScenario.SCRIPT_ID, -1));
    }
}
