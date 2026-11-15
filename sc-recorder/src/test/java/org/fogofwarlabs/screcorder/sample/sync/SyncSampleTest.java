package org.fogofwarlabs.screcorder.sample.sync;

import org.fogofwarlabs.screcorder.capture.sampling.CaptureContext;
import org.fogofwarlabs.screcorder.sample.RangeMemoryScenario;
import org.fogofwarlabs.screcorder.sample.SampleRoundTripCheck;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Checks that the sync sampler captures its scalars, blocks and ring as a detached snapshot and that its reader rejects a short payload.
 */
final class SyncSampleTest {
    private static final long MODULE_BASE = 0x100000000L;
    private static final long CONTEXT_ADDRESS = 0x120000000L;
    private static final int MODULE_BYTES = 0x1100000;

    private static final int RANDOM_STATE_OFFSET = 0x10936A0;
    private static final int RANDOM_TOTAL_OFFSET = 0x10936A8;
    private static final int SYNC_DRAWING_REFRESH_FLAG_OFFSET = 0xE76C88;
    private static final int SYNC_VISIBILITY_FOLD_OFFSET = 0xE76C8C;
    private static final int TURN_LENGTH_OFFSET = 0x1092294;
    private static final int SYNC_HEAD_OFFSET = 0x1094320;
    private static final int SYNC_RING_OFFSET = 0x1094330;
    private static final int SYNC_STATUS_OFFSET = 0x10953F0;
    private static final int SYNC_VISIBILITY_ROWS_OFFSET = 0xE77C90;
    private static final int SYNC_SPRITE_VECTOR_SIZE_INDEX = 8;
    private static final int SYNC_SPRITE_VECTOR_SIZE_OFFSET = 0xE76C70 + SYNC_SPRITE_VECTOR_SIZE_INDEX;
    private static final int VISION_OFFSET = 0x10908E0;
    private static final int SYNC_PLAYERS_OFFSET = 0xDD5B50;
    private static final int TURN_OFFSET = 0x10922A0;

    private static final int SYNC_RING_LAST_INDEX = 4287;

    @Test
    void sync_sampler_captures_blocks_ring_and_unsigned_scalars() throws IOException {
        RangeMemoryScenario memory = new RangeMemoryScenario();
        byte[] moduleStorage = memory.allocateRange(MODULE_BASE, MODULE_BYTES);
        ByteBuffer module = ByteBuffer.wrap(moduleStorage).order(ByteOrder.LITTLE_ENDIAN);

        module.putInt(RANDOM_STATE_OFFSET, -1).putInt(RANDOM_TOTAL_OFFSET, 7)
              .putInt(SYNC_DRAWING_REFRESH_FLAG_OFFSET, 9).putInt(SYNC_VISIBILITY_FOLD_OFFSET, 11).putInt(TURN_LENGTH_OFFSET, 16);
        moduleStorage[SYNC_HEAD_OFFSET]                        = 1;
        moduleStorage[SYNC_RING_OFFSET]                        = 2;
        moduleStorage[SYNC_RING_OFFSET + SYNC_RING_LAST_INDEX] = 3;
        moduleStorage[SYNC_STATUS_OFFSET]                      = 4;
        moduleStorage[SYNC_VISIBILITY_ROWS_OFFSET]             = 5;
        moduleStorage[SYNC_SPRITE_VECTOR_SIZE_OFFSET]          = 6;
        moduleStorage[VISION_OFFSET]                           = 7;
        moduleStorage[SYNC_PLAYERS_OFFSET]                     = 8;
        moduleStorage[TURN_OFFSET]                             = 9;

        SyncSampleDefinition definition = new SyncSampleDefinition();
        SyncSample sample = definition.createSampler(new CaptureContext(memory, MODULE_BASE, CONTEXT_ADDRESS)).sample();

        moduleStorage[SYNC_RING_OFFSET] = 99;

        SyncSample decoded = SampleRoundTripCheck.readAfterWrite(definition, sample);

        Assertions.assertEquals(0xFFFFFFFFL, decoded.getRandomState());
        Assertions.assertEquals(7, decoded.getRandomTotal());
        Assertions.assertEquals(9, decoded.getDrawingRefreshFlag());
        Assertions.assertEquals(11, decoded.getVisibilityFold());
        Assertions.assertEquals(16, decoded.getTurnLength());
        Assertions.assertEquals(1, decoded.getHead()[0]);
        Assertions.assertEquals(2, decoded.getRing()[0]);
        Assertions.assertEquals(3, decoded.getRing()[SYNC_RING_LAST_INDEX]);
        Assertions.assertEquals(4, decoded.getStatus()[0]);
        Assertions.assertEquals(5, decoded.getVisibilityRows()[0]);
        Assertions.assertEquals(6, decoded.getSpriteVector()[SYNC_SPRITE_VECTOR_SIZE_INDEX]);
        Assertions.assertEquals(7, decoded.getVision()[0]);
        Assertions.assertEquals(8, decoded.getPlayers()[0]);
        Assertions.assertEquals(9, decoded.getTurn()[0]);
        Assertions.assertThrows(IOException.class, () -> definition.getReader().read(ByteBuffer.allocate(definition.getMaximumPayloadBytes() - 1)));
    }
}
