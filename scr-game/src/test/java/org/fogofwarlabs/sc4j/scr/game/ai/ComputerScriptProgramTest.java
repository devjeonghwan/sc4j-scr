package org.fogofwarlabs.sc4j.scr.game.ai;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Checks script directory lookup, resource identity and invalid entries.
 */
final class ComputerScriptProgramTest {
    private static byte @NotNull [] createResource(int stride, int identifier, int address) {
        byte[] resource = new byte[16 + stride + 4];
        ByteBuffer values = ByteBuffer.wrap(resource).order(ByteOrder.LITTLE_ENDIAN);

        values.putInt(0, 16);
        values.put(4, (byte) 3);
        values.putInt(16, identifier);
        values.putInt(20, address);

        return resource;
    }


    @Test
    void program_searches_main_directory_before_expansion_and_keeps_own_copy() {
        byte[] main = createResource(16, 0x78434D5A, 4);
        byte[] expansion = createResource(8, 0x78434D54, 5);
        ComputerScriptProgram program = new ComputerScriptProgram(main, expansion);

        Assertions.assertEquals(4, program.find(0x78434D5A));
        Assertions.assertEquals(1L << 32 | 5, program.find(0x78434D54));
        Assertions.assertEquals(-1, program.find(0x12345678));
        Assertions.assertEquals(3, program.readByte(false, 4));

        main[4] = 99;

        Assertions.assertEquals(3, program.readByte(false, 4));
    }

    @Test
    void program_rejects_truncated_directories_and_invalid_entry_addresses() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ComputerScriptProgram(new byte[3], new byte[4]));

        byte[] main = createResource(16, 1, 0x7000);
        ComputerScriptProgram program = new ComputerScriptProgram(main, createResource(8, 2, 4));

        Assertions.assertThrows(IllegalArgumentException.class, () -> program.find(1));
    }
}
