package org.fogofwarlabs.sc4j.scr.data.format.mpq;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Tests PKWare DCL decompression against a fixed output vector.
 */
final class MpqExploderUtilsTest {
    /**
     * The length of {@link MpqTestResources#IMPLODED_TEXT}.
     */
    private static final int EXPLODED_SIZE = 13;
    private static final int LARGER_CAPACITY = 28;

    @Test
    void explode_should_decompress_known_vector() throws IOException {
        byte[] result = MpqExploderUtils.explode(MpqTestResources.IMPLODED_STREAM, EXPLODED_SIZE);

        Assertions.assertEquals(MpqTestResources.IMPLODED_TEXT, new String(result, StandardCharsets.US_ASCII));
    }

    @Test
    void exploding_keeps_actual_size_within_capacity_and_exact_size_validation() throws IOException {
        byte[] result = MpqExploderUtils.explodeUpTo(MpqTestResources.IMPLODED_STREAM, LARGER_CAPACITY);

        Assertions.assertEquals(MpqTestResources.IMPLODED_TEXT, new String(result, StandardCharsets.US_ASCII));
        Assertions.assertThrows(IOException.class, () -> MpqExploderUtils.explode(MpqTestResources.IMPLODED_STREAM, LARGER_CAPACITY));
        Assertions.assertThrows(IOException.class, () -> MpqExploderUtils.explodeUpTo(MpqTestResources.IMPLODED_STREAM, EXPLODED_SIZE - 1));
        Assertions.assertThrows(IOException.class, () -> MpqExploderUtils.explodeUpTo(MpqTestResources.IMPLODED_STREAM, 0));
        Assertions.assertThrows(IOException.class, () -> MpqExploderUtils.explodeUpTo(MpqTestResources.IMPLODED_STREAM, -1));
    }
}
