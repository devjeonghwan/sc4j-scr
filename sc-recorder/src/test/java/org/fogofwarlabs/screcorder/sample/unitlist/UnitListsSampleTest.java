package org.fogofwarlabs.screcorder.sample.unitlist;

import org.fogofwarlabs.screcorder.capture.MemoryReader;
import org.fogofwarlabs.screcorder.capture.sampling.CaptureContext;
import org.fogofwarlabs.screcorder.capture.sampling.Sampler;
import org.fogofwarlabs.screcorder.capture.starcraft.StarCraftLayout;
import org.fogofwarlabs.screcorder.exception.Exceptions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Checks that the unit-list sampler captures the traversal order of both lists and that the unit-list sample validates its payload.
 */
final class UnitListsSampleTest {
    private static final long VISIBLE_HEAD_OFFSET = 0xE77FC8;
    private static final long MODULE_BASE = 0x140000000L;
    private static final long CONTEXT_ADDRESS = 0x200000000L;
    private static final long POOL_ADDRESS = 0x300000000L;
    private static final int HIDDEN_HEAD_OFFSET = 32;
    private static final int UNIT_NEXT_OFFSET = 8;
    private static final int UNIT_POOL_SLOT_COUNT = 3;
    private static final int SECOND_UNIT_OFFSET = StarCraftLayout.UNIT_STRIDE;
    private static final int THIRD_UNIT_OFFSET = 2 * StarCraftLayout.UNIT_STRIDE;
    private static final int UNIT_POOL_BYTES = UNIT_POOL_SLOT_COUNT * StarCraftLayout.UNIT_STRIDE;

    private static final class UnitListMemoryScenario implements MemoryReader {
        private final byte @NotNull [] metadata;
        private final byte @NotNull [] heads;
        private final byte @NotNull [] pool;

        UnitListMemoryScenario() {
            this.metadata = new byte[2 * Long.BYTES];
            this.heads    = new byte[HIDDEN_HEAD_OFFSET + Long.BYTES];
            this.pool     = new byte[UNIT_POOL_BYTES];

            ByteBuffer.wrap(this.metadata).order(ByteOrder.LITTLE_ENDIAN).putLong(POOL_ADDRESS).putLong(UNIT_POOL_SLOT_COUNT);
        }


        /**
         * Reads the entire requested range.
         *
         * @param address     the target address
         * @param destination the destination storage
         * @param offset      the destination offset
         * @param length      the byte count
         * @throws IOException if the entire range cannot be read
         */
        @Override
        public void read(long address, byte @NotNull [] destination, int offset, int length) throws IOException {
            byte[] source;

            if (address == MODULE_BASE + StarCraftLayout.UNIT_POOL_POINTER) {
                source = this.metadata;
            } else if (address == MODULE_BASE + VISIBLE_HEAD_OFFSET) {
                source = this.heads;
            } else if (address == POOL_ADDRESS) {
                source = this.pool;
            } else {
                throw Exceptions.newIOException("Unexpected restored sample address: {0}", address);
            }

            if (length > source.length) {
                throw Exceptions.newIOException("Read exceeds fixture region");
            }

            System.arraycopy(source, 0, destination, offset, length);
        }
    }


    @Test
    void unit_lists_sampler_captures_traversal_order_by_pool_identifiers() throws IOException {
        UnitListMemoryScenario scenario = new UnitListMemoryScenario();
        ByteBuffer heads = ByteBuffer.wrap(scenario.heads).order(ByteOrder.LITTLE_ENDIAN);

        heads.putLong(0, POOL_ADDRESS + THIRD_UNIT_OFFSET);
        heads.putLong(HIDDEN_HEAD_OFFSET, POOL_ADDRESS + SECOND_UNIT_OFFSET);

        ByteBuffer pool = ByteBuffer.wrap(scenario.pool).order(ByteOrder.LITTLE_ENDIAN);

        pool.putLong(THIRD_UNIT_OFFSET + UNIT_NEXT_OFFSET, POOL_ADDRESS);

        UnitListsSampleDefinition definition = new UnitListsSampleDefinition();
        Sampler<UnitListsSample> sampler = definition.createSampler(new CaptureContext(scenario, MODULE_BASE, CONTEXT_ADDRESS));
        UnitListsSample sample = sampler.sample();

        Assertions.assertArrayEquals(new int[]{3, 1}, sample.getVisibleUnitIdentifiers());
        Assertions.assertArrayEquals(new int[]{2}, sample.getHiddenUnitIdentifiers());

        ByteBuffer output = ByteBuffer.allocate(definition.getEncodedSize(sample));

        definition.getWriter().write(output, sample);
        Assertions.assertArrayEquals(new byte[]{2, 0, 0, 0, 3, 0, 1, 0, 1, 0, 0, 0, 2, 0}, output.array());
        output.flip();

        UnitListsSample decoded = definition.getReader().read(output);

        Assertions.assertArrayEquals(new int[]{3, 1}, decoded.getVisibleUnitIdentifiers());

        int[] exposed = sample.getVisibleUnitIdentifiers();

        exposed[0] = 100;

        Assertions.assertArrayEquals(new int[]{3, 1}, sample.getVisibleUnitIdentifiers());

        pool.putLong(THIRD_UNIT_OFFSET + UNIT_NEXT_OFFSET, 0);

        Assertions.assertArrayEquals(new int[]{3}, sampler.sample().getVisibleUnitIdentifiers());
        Assertions.assertArrayEquals(new int[]{3, 1}, sample.getVisibleUnitIdentifiers());
    }

    @Test
    void unit_lists_sample_rejects_cycles_invalid_pointers_and_malformed_payloads() throws IOException {
        UnitListMemoryScenario scenario = new UnitListMemoryScenario();
        ByteBuffer heads = ByteBuffer.wrap(scenario.heads).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer pool = ByteBuffer.wrap(scenario.pool).order(ByteOrder.LITTLE_ENDIAN);

        heads.putLong(0, POOL_ADDRESS);
        pool.putLong(UNIT_NEXT_OFFSET, POOL_ADDRESS);

        UnitListsSampleDefinition definition = new UnitListsSampleDefinition();
        Sampler<UnitListsSample> sampler = definition.createSampler(new CaptureContext(scenario, MODULE_BASE, CONTEXT_ADDRESS));

        Assertions.assertThrows(IOException.class, sampler::sample);

        heads.putLong(0, POOL_ADDRESS + 1);
        Assertions.assertThrows(IOException.class, sampler::sample);

        heads.putLong(0, POOL_ADDRESS + UNIT_POOL_BYTES);
        Assertions.assertThrows(IOException.class, sampler::sample);

        ByteBuffer.wrap(scenario.metadata).order(ByteOrder.LITTLE_ENDIAN).putLong(Long.BYTES, 0x100000000L);
        Assertions.assertThrows(IOException.class, sampler::sample);
        Assertions.assertThrows(IOException.class, () -> definition.getEncodedSize(new UnitListsSample(new int[]{1, 1}, new int[0])));
        Assertions.assertThrows(IOException.class, () -> definition.getEncodedSize(new UnitListsSample(new int[]{3401}, new int[0])));
        Assertions.assertThrows(IOException.class, () -> definition.getReader().read(ByteBuffer.wrap(new byte[]{1, 0, 0, 0, 0, 0, 0, 0, 0, 0})));
        Assertions.assertThrows(IOException.class, () -> definition.getReader().read(ByteBuffer.wrap(new byte[9])));

        UnitListsSample empty = new UnitListsSample(new int[0], new int[0]);

        Assertions.assertEquals(8, definition.getEncodedSize(empty));
        Assertions.assertThrows(IOException.class, () -> definition.getWriter().write(ByteBuffer.allocate(7), empty));
    }
}
