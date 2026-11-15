package org.fogofwarlabs.sc4j.scr.game.trigger.eud;

import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.region.ClassicCodeConstantRegion;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Checks executable constants, writable shadows and unsupported images.
 */
final class NativeCodeConstantsTest {
    private static final int DOS_MAGIC = 0x5A4D;
    private static final int PE_SIGNATURE = 0x4550;
    private static final int PE_MACHINE_FIELD = 4;
    private static final int PE_SECTION_COUNT_FIELD = 6;
    private static final int PE_OPTIONAL_SIZE_FIELD = 20;
    private static final int PE_CHARACTERISTICS_FIELD = 22;
    private static final int PE_IMAGE_BASE_FIELD = 24;
    private static final int PE_SECTION_ALIGNMENT_FIELD = 32;
    private static final int PE_FILE_ALIGNMENT_FIELD = 36;
    private static final int PE_IMAGE_SIZE_FIELD = 56;
    private static final int PE_HEADERS_SIZE_FIELD = 60;
    private static final int PE_SECTION_VIRTUAL_SIZE_FIELD = 8;
    private static final int PE_SECTION_VIRTUAL_ADDRESS_FIELD = 12;
    private static final int PE_SECTION_RAW_SIZE_FIELD = 16;
    private static final int PE_SECTION_RAW_POINTER_FIELD = 20;
    private static final int PE_SECTION_CHARACTERISTICS_FIELD = 36;

    private static final int PE_HEADER_OFFSET = 0x80;
    private static final int PE_HEADER_POINTER_OFFSET = 0x3C;
    private static final int PE_FILE_HEADER_BYTES = 24;
    private static final int PE_OPTIONAL_HEADER_BYTES = 0xF0;
    private static final int PE_SECTION_ENTRY_BYTES = 40;
    private static final int PE_FILE_ALIGNMENT = 0x200;
    private static final int PE_SECTION_ALIGNMENT = 0x1000;
    private static final int PE_HEADERS_SIZE = PE_FILE_ALIGNMENT;
    private static final int PE_MACHINE = 0x8664;
    private static final int PE_CHARACTERISTICS = 0x22;
    private static final int PE_OPTIONAL_MAGIC = 0x20B;
    private static final long PE_IMAGE_BASE = 0x140000000L;
    private static final int STORM_SECTION_VIRTUAL_ADDRESS = 0xDB1000;
    private static final int STORM_SIZE_OFFSET = 0x6E4;
    private static final int STORM_STREAM_SIZE = 0x1CFFF;
    private static final int STORM_VIRTUAL_SIZE = STORM_SIZE_OFFSET + Integer.BYTES + STORM_STREAM_SIZE;
    private static final int STORM_RAW_SIZE = (STORM_VIRTUAL_SIZE + PE_FILE_ALIGNMENT - 1) & -PE_FILE_ALIGNMENT;
    private static final int STORM_SECTION_CHARACTERISTICS = 0x40000040;
    private static final int STORM_SECTION_COUNT = 1;

    private static final int FIRST_CODE_CONSTANT = 0x4A3898;
    private static final int SECOND_CODE_CONSTANT = 0x4A389C;

    @RequiresInstallation
    @Test
    void load_executable_should_read_every_constant_from_installed_executable() {
        Path executable = ExternalInput.INSTALLATION.resolveFile(GameData.EXECUTABLE_DIRECTORY, GameData.EXECUTABLE_FILE_NAME);

        NativeCodeConstants constants = new NativeCodeConstants();

        Assertions.assertTrue(constants.loadExecutable(executable));

        ClassicAddressSpace space = new ClassicAddressSpace();

        for (int address : NativeCodeConstants.ADDRESSES) {
            space.add(new ClassicCodeConstantRegion(address, constants));
        }

        Assertions.assertEquals(0x0F000001, space.readDword(FIRST_CODE_CONSTANT));
        Assertions.assertEquals(0x148B0EB6, space.readDword(SECOND_CODE_CONSTANT));
        Assertions.assertEquals(0x0F, new ClassicCodeConstantRegion(FIRST_CODE_CONSTANT, constants).readByte(3));

        space.writeDword(FIRST_CODE_CONSTANT, 0x12345678);

        Assertions.assertEquals(0x12345678, space.readDword(FIRST_CODE_CONSTANT), "A write is stored in the region's own DWORD.");
    }

    @Test
    void parse_storm_archives_should_reject_negative_decoded_size() {
        byte[] image = new byte[PE_HEADERS_SIZE + STORM_RAW_SIZE];
        ByteBuffer buffer = ByteBuffer.wrap(image).order(ByteOrder.LITTLE_ENDIAN);
        int optionalHeader = PE_HEADER_OFFSET + PE_FILE_HEADER_BYTES;
        int section = optionalHeader + PE_OPTIONAL_HEADER_BYTES;
        int imageSize = (STORM_SECTION_VIRTUAL_ADDRESS + STORM_VIRTUAL_SIZE + PE_SECTION_ALIGNMENT - 1) & -PE_SECTION_ALIGNMENT;

        buffer.putShort(0, (short) DOS_MAGIC);
        buffer.putInt(PE_HEADER_POINTER_OFFSET, PE_HEADER_OFFSET);
        buffer.putInt(PE_HEADER_OFFSET, PE_SIGNATURE);
        buffer.putShort(PE_HEADER_OFFSET + PE_MACHINE_FIELD, (short) PE_MACHINE);
        buffer.putShort(PE_HEADER_OFFSET + PE_SECTION_COUNT_FIELD, (short) STORM_SECTION_COUNT);
        buffer.putShort(PE_HEADER_OFFSET + PE_OPTIONAL_SIZE_FIELD, (short) PE_OPTIONAL_HEADER_BYTES);
        buffer.putShort(PE_HEADER_OFFSET + PE_CHARACTERISTICS_FIELD, (short) PE_CHARACTERISTICS);
        buffer.putShort(optionalHeader, (short) PE_OPTIONAL_MAGIC);
        buffer.putLong(optionalHeader + PE_IMAGE_BASE_FIELD, PE_IMAGE_BASE);
        buffer.putInt(optionalHeader + PE_SECTION_ALIGNMENT_FIELD, PE_SECTION_ALIGNMENT);
        buffer.putInt(optionalHeader + PE_FILE_ALIGNMENT_FIELD, PE_FILE_ALIGNMENT);
        buffer.putInt(optionalHeader + PE_IMAGE_SIZE_FIELD, imageSize);
        buffer.putInt(optionalHeader + PE_HEADERS_SIZE_FIELD, PE_HEADERS_SIZE);
        buffer.position(section);
        buffer.put(".rdata".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(section + PE_SECTION_VIRTUAL_SIZE_FIELD, STORM_VIRTUAL_SIZE);
        buffer.putInt(section + PE_SECTION_VIRTUAL_ADDRESS_FIELD, STORM_SECTION_VIRTUAL_ADDRESS);
        buffer.putInt(section + PE_SECTION_RAW_SIZE_FIELD, STORM_RAW_SIZE);
        buffer.putInt(section + PE_SECTION_RAW_POINTER_FIELD, PE_HEADERS_SIZE);
        buffer.putInt(section + PE_SECTION_CHARACTERISTICS_FIELD, STORM_SECTION_CHARACTERISTICS);
        buffer.putInt(PE_HEADERS_SIZE + STORM_SIZE_OFFSET, -1);

        Assertions.assertTrue(section + PE_SECTION_ENTRY_BYTES <= PE_HEADERS_SIZE);
        Assertions.assertThrows(IllegalArgumentException.class, () -> NativeCodeConstants.parseStormArchives(image));
    }

    @Test
    void load_executable_should_leave_constants_unknown_for_another_image() throws IOException {
        Path file = Files.createTempFile("not-starcraft", ".exe");

        try {
            Files.write(file, new byte[]{'M', 'Z', 0, 0});

            NativeCodeConstants constants = new NativeCodeConstants();

            Assertions.assertFalse(constants.loadExecutable(file));
            Assertions.assertFalse(constants.loadExecutable(file.resolveSibling("missing-starcraft.exe")));

            ClassicCodeConstantRegion region = new ClassicCodeConstantRegion(FIRST_CODE_CONSTANT, constants);

            Assertions.assertThrows(UnsupportedOperationException.class, () -> region.readDword(0));
        } finally {
            Files.delete(file);
        }
    }
}
