package org.fogofwarlabs.sc4j.scr.data.format.dat;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Tests interleaved DAT fields and adjacent column boundaries.
 */
final class DatSchemaTest {
    @Test
    void parse_should_read_interleaved_byte_pairs() throws IOException {
        DatSchema schema = new DatSchema().fieldU8("Prefix", 1).fieldU8x2("First", "Second", 3).fieldU8("Suffix", 1);
        DatTable table = schema.parse(new byte[]{99, 1, 11, 2, 12, 3, (byte) 255, 100});

        Assertions.assertEquals(99, table.get("Prefix", 0));
        Assertions.assertEquals(1, table.get("First", 0));
        Assertions.assertEquals(3, table.get("First", 2));
        Assertions.assertEquals(11, table.get("Second", 0));
        Assertions.assertEquals(12, table.get("Second", 1));
        Assertions.assertEquals(255, table.get("Second", 2));
        Assertions.assertEquals(100, table.get("Suffix", 0));
    }

    @Test
    void parse_should_read_interleaved_word_pairs() throws IOException {
        DatSchema schema = new DatSchema().fieldU16("Prefix", 1).fieldU16x2("Horizontal", "Vertical", 2).fieldU16("Suffix", 1);
        ByteBuffer data = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);

        data.putShort((short) 0x1122).putShort((short) 320).putShort((short) 640);
        data.putShort((short) 65535).putShort((short) 32768).putShort((short) 0x3344);

        DatTable table = schema.parse(data.array());

        Assertions.assertEquals(0x1122, table.get("Prefix", 0));
        Assertions.assertEquals(320, table.get("Horizontal", 0));
        Assertions.assertEquals(65535, table.get("Horizontal", 1));
        Assertions.assertEquals(640, table.get("Vertical", 0));
        Assertions.assertEquals(32768, table.get("Vertical", 1));
        Assertions.assertEquals(0x3344, table.get("Suffix", 0));
    }

    @Test
    void parse_should_read_successive_interleaved_groups() throws IOException {
        DatSchema schema = new DatSchema().fieldU16x2("Width", "Height", 1).fieldU16x4("Left", "Up", "Right", "Down", 2);
        ByteBuffer data = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);

        data.putShort((short) 128).putShort((short) 96);

        for (int value = 1; value <= 8; value++) {
            data.putShort((short) value);
        }

        DatTable table = schema.parse(data.array());

        Assertions.assertEquals(128, table.get("Width", 0));
        Assertions.assertEquals(96, table.get("Height", 0));

        String[] fields = {"Left", "Up", "Right", "Down"};

        for (int fieldIndex = 0; fieldIndex < fields.length; fieldIndex++) {
            Assertions.assertEquals(fieldIndex + 1, table.get(fields[fieldIndex], 0));
            Assertions.assertEquals(fieldIndex + 5, table.get(fields[fieldIndex], 1));
        }
    }
}
