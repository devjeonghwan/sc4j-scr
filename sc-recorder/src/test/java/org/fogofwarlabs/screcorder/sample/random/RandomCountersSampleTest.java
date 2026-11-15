package org.fogofwarlabs.screcorder.sample.random;

import org.fogofwarlabs.screcorder.capture.sampling.CaptureContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Verifies that the sampler collects every random source counter and that the canonical encoding keeps them unsigned.
 */
final class RandomCountersSampleTest {
    private static final long MODULE_BASE = 0x140000000L;
    private static final long CONTEXT_ADDRESS = 0x20000000;
    private static final int NATIVE_COUNTER_COUNT = 256;
    private static final int NATIVE_COUNTER_BYTES = NATIVE_COUNTER_COUNT * Integer.BYTES;
    private static final long RANDOM_COUNTERS_OFFSET = 0x10936B0;

    @Test
    void random_counters_sampler_collects_and_round_trips_every_counter() throws IOException {
        byte[] storage = new byte[NATIVE_COUNTER_BYTES];
        ByteBuffer source = ByteBuffer.wrap(storage).order(ByteOrder.LITTLE_ENDIAN);

        for (int index = 0; index < NATIVE_COUNTER_COUNT; index++) {
            source.putInt(index * Integer.BYTES, index == NATIVE_COUNTER_COUNT - 1 ? -1 : index * 3);
        }

        CaptureContext context = new CaptureContext((address, destination, offset, length) -> {
            Assertions.assertEquals(MODULE_BASE + RANDOM_COUNTERS_OFFSET, address);
            Assertions.assertEquals(storage.length, length);

            System.arraycopy(storage, 0, destination, offset, length);
        }, MODULE_BASE, CONTEXT_ADDRESS);
        RandomCountersSampleDefinition definition = new RandomCountersSampleDefinition();
        RandomCountersSample sample = definition.createSampler(context).sample();

        Assertions.assertEquals(0xFFFFFFFFL, sample.getCounter(NATIVE_COUNTER_COUNT - 1));

        ByteBuffer payload = ByteBuffer.allocate(definition.getEncodedSize(sample)).order(ByteOrder.LITTLE_ENDIAN);

        definition.getWriter().write(payload, sample);
        payload.flip();

        RandomCountersSample restored = definition.getReader().read(payload);

        Assertions.assertArrayEquals(sample.getCounters(), restored.getCounters());

        long[] copy = sample.getCounters();

        copy[0] = 99;

        Assertions.assertEquals(0, sample.getCounter(0));
    }

    @Test
    void random_counters_writer_writes_little_endian_and_rejects_read_only_destinations() throws IOException {
        long[] counters = new long[RandomCountersSample.COUNTER_COUNT];

        counters[0]                   = 0x01020304L;
        counters[counters.length - 1] = 0xFFFFFFFFL;

        RandomCountersSample sample = new RandomCountersSample(counters);
        RandomCountersSampleDefinition definition = new RandomCountersSampleDefinition();
        ByteBuffer payload = ByteBuffer.allocate(definition.getEncodedSize(sample));

        definition.getWriter().write(payload, sample);
        Assertions.assertArrayEquals(new byte[]{4, 3, 2, 1}, Arrays.copyOf(payload.array(), Integer.BYTES));
        payload.flip().order(ByteOrder.BIG_ENDIAN);
        Assertions.assertArrayEquals(counters, definition.getReader().read(payload).getCounters());

        ByteBuffer readOnly = ByteBuffer.allocate(definition.getEncodedSize(sample)).asReadOnlyBuffer();

        Assertions.assertThrows(IOException.class, () -> definition.getWriter().write(readOnly, sample));
        Assertions.assertEquals(0, readOnly.position());
    }

    @Test
    void random_counters_sample_rejects_wrong_lengths_and_out_of_range_counters() {
        RandomCountersSampleDefinition definition = new RandomCountersSampleDefinition();

        Assertions.assertThrows(IOException.class, () -> definition.getReader().read(ByteBuffer.allocate(NATIVE_COUNTER_BYTES - 1)));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new RandomCountersSample(new long[NATIVE_COUNTER_COUNT - 1]));

        long[] counters = new long[NATIVE_COUNTER_COUNT];

        counters[50] = 0x100000000L;

        Assertions.assertThrows(IllegalArgumentException.class, () -> new RandomCountersSample(counters));
    }
}
