package org.fogofwarlabs.sc4j.scr.data.format.iscript;

import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.TreeMap;

/**
 * Tests instruction decoding from synthetic iscript data and header and animation parsing from installed iscript data.
 */
final class IscriptFileTest {
    private static final int MAXIMUM_OPCODE = 0x44;
    private static final int PLAYFRAM = 0x00;
    private static final int END = 0x16;
    /**
     * The identifier table offset of the synthetic decoding script, whose DWORD starts the file with the bytes {@code E6 97 00 00} as in the
     * installed file.
     */
    private static final int DECODING_TABLE_OFFSET = 0x97E6;
    /**
     * The size of an identifier table entry: the script identifier WORD and the header offset WORD.
     */
    private static final int TABLE_ENTRY_SIZE = 4;
    private static final int TABLE_ENTRY_HEADER_OFFSET = 2;
    private static final int TABLE_TERMINATOR_ID = 65535;
    private static final int HEADER_TYPE_OFFSET = 4;
    /**
     * The offset of the animation offset table inside a header, after the magic and the type.
     */
    private static final int HEADER_ANIMATIONS_OFFSET = 8;

    private static @Nullable IscriptFile iscriptFile;
    private static byte @Nullable [] scriptBytes;

    private static void loadIscript() throws IOException {
        if (iscriptFile != null) {
            return;
        }

        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            scriptBytes = storage.readFile("scripts/iscript.bin");
            iscriptFile = IscriptFile.parse(scriptBytes);
        }
    }


    @Test
    void decode_instruction_should_decode_offset_zero_and_skip_bytes_above_last_opcode() throws IOException {
        ByteBuffer data = IscriptFileScenario.createScript(DECODING_TABLE_OFFSET);

        data.put(IscriptFileScenario.INIT_BLOCK_OFFSET, (byte) END);

        IscriptFile script = IscriptFile.parse(data.array());

        Assertions.assertEquals(0xE6, script.decodeInstruction(0).getOpcode(), "The installed file begins with bytes E6 97.");
        Assertions.assertEquals(0, script.decodeInstruction(0).getOperands().length);
        Assertions.assertEquals(1, script.getInstructionSize(0));
        Assertions.assertEquals(0x97, script.decodeInstruction(1).getOpcode());
        Assertions.assertEquals(1, script.getInstructionSize(1));
        Assertions.assertEquals(PLAYFRAM, script.decodeInstruction(2).getOpcode(), "The following zero bytes decode as playfram.");
        Assertions.assertEquals(3, script.getInstructionSize(2));
        Assertions.assertThrows(IOException.class, () -> script.decodeInstruction(-1));
        Assertions.assertThrows(IOException.class, () -> script.decodeInstruction(DECODING_TABLE_OFFSET + IscriptFileScenario.TABLE_SIZE));
    }

    @RequiresInstallation
    @Test
    void parse_should_read_all_installed_headers_animations_and_instructions() throws IOException {
        loadIscript();

        IscriptFile activeIscriptFile = iscriptFile;

        Assertions.assertNotNull(activeIscriptFile);

        int headerCount = activeIscriptFile.getHeaders().size();
        int animationCount = 0;
        int instructionCount = 0;
        Map<String, Integer> opcodeCounts = new TreeMap<>();

        for (IscriptHeader header : activeIscriptFile.getHeaders()) {
            for (IscriptHeader.Animation animation : header.getAnimations()) {
                animationCount++;

                for (IscriptInstruction instruction : animation.getInstructions()) {
                    instructionCount++;
                    opcodeCounts.merge(instruction.getName(), 1, Integer::sum);
                }
            }
        }

        System.out.println("Headers: " + headerCount + ", animations: " + animationCount + ", instructions: " + instructionCount);
        System.out.println("Opcodes used: " + opcodeCounts.size() + " / " + (MAXIMUM_OPCODE + 1));

        for (Map.Entry<String, Integer> opcodeCount : opcodeCounts.entrySet()) {
            System.out.println("  " + opcodeCount.getKey() + ": " + opcodeCount.getValue());
        }

        Assertions.assertTrue(headerCount > 300, "Too few iscript headers: " + headerCount);
        Assertions.assertTrue(animationCount > 1000, "Too few animations: " + animationCount);
        Assertions.assertTrue(instructionCount > 10000, "Too few instructions: " + instructionCount);
    }

    @RequiresInstallation
    @Test
    void headers_match_animation_offsets_in_installed_bytes() throws IOException {
        loadIscript();

        byte[] activeScriptBytes = scriptBytes;
        IscriptFile activeIscriptFile = iscriptFile;

        Assertions.assertNotNull(activeScriptBytes);
        Assertions.assertNotNull(activeIscriptFile);

        ByteBuffer data = ByteBuffer.wrap(activeScriptBytes).order(ByteOrder.LITTLE_ENDIAN);
        int tableOffset = data.getInt(0);
        int checkedHeaders = 0;

        for (int entryOffset = tableOffset; entryOffset <= data.capacity() - TABLE_ENTRY_SIZE; entryOffset += TABLE_ENTRY_SIZE) {
            int identifier = Short.toUnsignedInt(data.getShort(entryOffset));

            if (identifier == TABLE_TERMINATOR_ID) {
                break;
            }

            int headerOffset = Short.toUnsignedInt(data.getShort(entryOffset + TABLE_ENTRY_HEADER_OFFSET));
            int declaredType = Short.toUnsignedInt(data.getShort(headerOffset + HEADER_TYPE_OFFSET));
            IscriptHeader header = activeIscriptFile.findHeader(identifier);

            Assertions.assertNotNull(header);
            Assertions.assertEquals(declaredType, header.getType());

            for (int animationIndex = 0; animationIndex <= declaredType; animationIndex++) {
                int expected = Short.toUnsignedInt(data.getShort(headerOffset + HEADER_ANIMATIONS_OFFSET + animationIndex * Short.BYTES));

                Assertions.assertEquals(expected, header.getAnimationOffset(animationIndex),
                                        "Script " + identifier + ", animation " + animationIndex);
                Assertions.assertEquals(expected, activeIscriptFile.getAnimationOffset(identifier, animationIndex));
            }

            checkedHeaders++;
        }

        Assertions.assertEquals(398, checkedHeaders);
        Assertions.assertTrue(activeIscriptFile.getAnimationOffset(78, 0) > 0);
        Assertions.assertTrue(activeIscriptFile.getAnimationOffset(11, 0) > 0);
    }
}
