package org.fogofwarlabs.sc4j.scr.data.format.terrain;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Verifies that terrain mask tables map megatiles to frames and reject a bad record count or magic.
 */
final class TerrainMaskTableTest {
    @Test
    void terrain_mask_table_maps_megatiles_to_frames_and_rejects_bad_count_or_magic() throws IOException {
        ByteBuffer contents = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);

        contents.put(new byte[]{'K', 'S', 'M', 'T'}).putShort((short) 1).putShort((short) 2);
        contents.putShort((short) 3).putShort((short) 7).putShort((short) 5).putShort((short) 1);

        TerrainMaskTable table = TerrainMaskTable.parse(contents.array(), 6);

        Assertions.assertEquals(2, table.getRecordCount());
        Assertions.assertEquals(7, table.getMaskFrame(3));
        Assertions.assertEquals(1, table.getMaskFrame(5));
        Assertions.assertEquals(-1, table.getMaskFrame(4));
        Assertions.assertThrows(IOException.class, () -> TerrainMaskTable.parse(contents.array(), 5));

        contents.put(0, (byte) 'X');

        Assertions.assertThrows(IOException.class, () -> TerrainMaskTable.parse(contents.array(), 6));
    }
}
