package org.fogofwarlabs.sc4j.scr.data.resource.importer;

import org.fogofwarlabs.sc4j.engine.resource.graphics.SpriteSheet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Tests the one-frame sprite sheet of an indexed PCX picture.
 */
final class PcxSheetUtilsTest {
    @Test
    void create_sheet_should_decode_runs_across_the_stride_and_drop_padding() throws IOException {
        ByteBuffer contents = ByteBuffer.allocate(128 + 5).order(ByteOrder.LITTLE_ENDIAN);

        contents.put(0, (byte) 0x0A).put(2, (byte) 1).put(3, (byte) 8).put(65, (byte) 1);
        contents.putShort(8, (short) 2).putShort(10, (short) 1).putShort(66, (short) 4);
        contents.position(128);
        // A run of three 5s, a literal 6 (row padding) and a literal 9 on the second row.
        contents.put((byte) 0xC3).put((byte) 5).put((byte) 6).put((byte) 0xC4).put((byte) 9);

        SpriteSheet sheet = PcxSheetUtils.createSheet(contents.array());

        Assertions.assertEquals(3, sheet.getWidth());
        Assertions.assertEquals(2, sheet.getHeight());
        Assertions.assertEquals(5, sheet.getColor().getValue(2));
        Assertions.assertEquals(9, sheet.getColor().getValue(3));
        Assertions.assertEquals(9, sheet.getColor().getValue(5));
        Assertions.assertThrows(IOException.class, () -> PcxSheetUtils.createSheet(new byte[127]));
    }
}
