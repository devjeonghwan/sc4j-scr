package org.fogofwarlabs.sc4j.scr.data.resource.importer;

import org.fogofwarlabs.sc4j.engine.resource.graphics.PixelFormat;
import org.fogofwarlabs.sc4j.engine.resource.graphics.SpriteFrame;
import org.fogofwarlabs.sc4j.engine.resource.graphics.SpriteSheet;
import org.fogofwarlabs.sc4j.scr.data.format.image.GrpFile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Tests the sprite sheet of a GRP file: canvas, anchor, frame rectangles, decoded pixels and frames that share line data.
 */
final class GrpSheetUtilsTest {
    @Test
    void create_sheet_should_store_frame_rectangles_and_share_repeated_frames() throws IOException {
        ByteBuffer contents = ByteBuffer.allocate(6 + 3 * 8 + 6 + 6).order(ByteOrder.LITTLE_ENDIAN);

        // Header: three frames on a 5 by 3 canvas.
        contents.putShort((short) 3).putShort((short) 5).putShort((short) 3);
        // Frames 0 and 1 share the line data at 30; frame 2 has its own at 36.
        contents.put((byte) 1).put((byte) 1).put((byte) 3).put((byte) 1).putInt(30);
        contents.put((byte) 1).put((byte) 1).put((byte) 3).put((byte) 1).putInt(30);
        contents.put((byte) 0).put((byte) 2).put((byte) 2).put((byte) 1).putInt(36);
        // Line data: a literal of three pixels, then a repeat of two pixels.
        contents.putShort((short) 2).put((byte) 0x03).put((byte) 4).put((byte) 5).put((byte) 6);
        contents.putShort((short) 2).put((byte) 0x42).put((byte) 7).put((byte) 0).put((byte) 0);

        SpriteSheet sheet = GrpSheetUtils.createSheet(GrpFile.parse(contents.array()));
        SpriteFrame first = sheet.getFrame(0);
        SpriteFrame third = sheet.getFrame(2);

        Assertions.assertEquals(5, sheet.getWidth());
        Assertions.assertEquals(3, sheet.getHeight());
        Assertions.assertEquals(2, sheet.getAnchorX());
        Assertions.assertEquals(1, sheet.getAnchorY());
        Assertions.assertEquals(3, sheet.getFrameCount());
        Assertions.assertEquals(PixelFormat.INDEXED_8, sheet.getColor().getFormat());
        Assertions.assertEquals(5, sheet.getColor().getLength());
        Assertions.assertEquals(1, first.getX());
        Assertions.assertEquals(1, first.getY());
        Assertions.assertEquals(3, first.getWidth());
        Assertions.assertEquals(first.getPixelOffset(), sheet.getFrame(1).getPixelOffset());
        Assertions.assertEquals(3, third.getPixelOffset());
        Assertions.assertEquals(2, third.getY());
        Assertions.assertEquals(4, sheet.getColor().getValue(first.getPixelOffset()));
        Assertions.assertEquals(6, sheet.getColor().getValue(first.getPixelOffset() + 2));
        Assertions.assertEquals(7, sheet.getColor().getValue(third.getPixelOffset() + 1));
        Assertions.assertFalse(GrpSheetUtils.isPreviousFrameRepeated(sheet, 0));
        Assertions.assertTrue(GrpSheetUtils.isPreviousFrameRepeated(sheet, 1));
        Assertions.assertFalse(GrpSheetUtils.isPreviousFrameRepeated(sheet, 2));
        Assertions.assertNull(sheet.getTeamMask());
        Assertions.assertNull(sheet.getShadow());
    }
}
