package org.fogofwarlabs.screcorder.sample.ai;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Checks that AI region rows from processes with different arena and image bases normalize to identical bytes.
 */
final class AiRegionNormalizationTest {
    private static final int FIRST_ARENA_POINTER_OFFSET = 32;
    private static final int SECOND_ARENA_POINTER_OFFSET = 40;
    private static final int THIRD_ARENA_POINTER_OFFSET = 56;
    private static final int FIRST_IMAGE_POINTER_OFFSET = 64;
    private static final int SECOND_IMAGE_POINTER_OFFSET = 72;

    @Test
    void normalize_region_should_make_arena_and_image_pointers_relative() {
        long[][] runs = {{0x7FF436DC0010L, 0x7FF64C4A0000L}, {0x7FF472210010L, 0x7FF686A90000L}};
        byte[][] rows = new byte[runs.length][];

        for (int run = 0; run < runs.length; run++) {
            long arenaAddress = runs[run][0];
            long moduleBase = runs[run][1];
            ByteBuffer row = ByteBuffer.allocate(AiRegionState.MEMORY_BYTES).order(ByteOrder.LITTLE_ENDIAN);

            row.putInt(0, 77);
            row.putLong(FIRST_ARENA_POINTER_OFFSET, arenaAddress - 0x1C318);
            row.putLong(SECOND_ARENA_POINTER_OFFSET, 0);
            row.putLong(THIRD_ARENA_POINTER_OFFSET, arenaAddress + 0x1234);
            row.putLong(FIRST_IMAGE_POINTER_OFFSET, moduleBase + 0x4E31E0);
            row.putLong(SECOND_IMAGE_POINTER_OFFSET, moduleBase + 0x10);
            rows[run] = row.array();
            AiSampleDefinition.normalizeRegion(rows[run], 0, arenaAddress, moduleBase);
        }

        ByteBuffer first = ByteBuffer.wrap(rows[0]).order(ByteOrder.LITTLE_ENDIAN);

        Assertions.assertArrayEquals(rows[0], rows[1]);
        Assertions.assertEquals(77, first.getInt(0));
        Assertions.assertEquals(-0x1C318, first.getLong(FIRST_ARENA_POINTER_OFFSET));
        Assertions.assertEquals(0, first.getLong(SECOND_ARENA_POINTER_OFFSET));
        Assertions.assertEquals(0x1234, first.getLong(THIRD_ARENA_POINTER_OFFSET));
        Assertions.assertEquals(0x4E31E0, first.getLong(FIRST_IMAGE_POINTER_OFFSET));
        Assertions.assertEquals(0x10, first.getLong(SECOND_IMAGE_POINTER_OFFSET));
    }
}
