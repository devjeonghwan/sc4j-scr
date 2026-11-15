package org.fogofwarlabs.screcorder.sample.random;

import org.fogofwarlabs.screcorder.binary.LittleEndianUtils;
import org.fogofwarlabs.screcorder.capture.sampling.CaptureContext;
import org.fogofwarlabs.screcorder.capture.sampling.Sampler;
import org.fogofwarlabs.screcorder.capture.starcraft.StarCraftLayout;
import org.fogofwarlabs.screcorder.exception.Exceptions;
import org.fogofwarlabs.screcorder.sample.SampleDefinition;
import org.fogofwarlabs.screcorder.sample.io.SampleReader;
import org.fogofwarlabs.screcorder.sample.io.SampleWriter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Defines the fixed-width random source counter payload and native collection.
 */
public final class RandomCountersSampleDefinition extends SampleDefinition<RandomCountersSample> {
    private static final int SAMPLE_IDENTIFIER = 5;
    private static final long COUNTERS_OFFSET = StarCraftLayout.RANDOM_BLOCK + StarCraftLayout.RANDOM_COUNTERS_OFFSET;
    private static final int PAYLOAD_BYTES = RandomCountersSample.COUNTER_COUNT * Integer.BYTES;

    private static final @NotNull SampleReader<RandomCountersSample> READER = RandomCountersSampleDefinition::read;
    private static final @NotNull SampleWriter<RandomCountersSample> WRITER = RandomCountersSampleDefinition::write;

    private static @NotNull RandomCountersSample read(@NotNull ByteBuffer input) throws IOException {
        input.order(ByteOrder.LITTLE_ENDIAN);

        if (input.remaining() != PAYLOAD_BYTES) {
            throw Exceptions.newIOException("Invalid random counter payload length");
        }

        long[] counters = new long[RandomCountersSample.COUNTER_COUNT];

        for (int source = 0; source < counters.length; source++) {
            counters[source] = Integer.toUnsignedLong(input.getInt());
        }

        return new RandomCountersSample(counters);
    }

    private static void write(@NotNull ByteBuffer output, @NotNull RandomCountersSample sample) throws IOException {
        if (output.remaining() < PAYLOAD_BYTES || output.isReadOnly()) {
            throw Exceptions.newIOException("Insufficient random counter destination");
        }

        output.order(ByteOrder.LITTLE_ENDIAN);

        for (int source = 0; source < RandomCountersSample.COUNTER_COUNT; source++) {
            output.putInt((int) sample.getCounter(source));
        }
    }


    /**
     * Creates a {@link RandomCountersSampleDefinition} instance.
     */
    public RandomCountersSampleDefinition() {
        super(SAMPLE_IDENTIFIER, "random-counters", RandomCountersSample.class, PAYLOAD_BYTES);
    }


    /**
     * @return the bounded payload reader
     */
    @Override
    public @NotNull SampleReader<RandomCountersSample> getReader() {
        return READER;
    }

    /**
     * @return the canonical payload writer
     */
    @Override
    public @NotNull SampleWriter<RandomCountersSample> getWriter() {
        return WRITER;
    }


    /**
     * Calculates the exact payload size before reserving reusable output storage.
     *
     * @param sample the sample to encode
     * @return the exact encoded byte count
     */
    @Override
    public int getEncodedSize(@NotNull RandomCountersSample sample) {
        return PAYLOAD_BYTES;
    }

    /**
     * Creates a {@link Sampler} instance with session-owned capture buffers.
     *
     * @param context the capture session
     * @return the created {@link Sampler} instance
     */
    @Override
    public @NotNull Sampler<RandomCountersSample> createSampler(@NotNull CaptureContext context) {
        byte[] storage = new byte[PAYLOAD_BYTES];

        return () -> {
            context.getMemory().read(context.getModuleBase() + COUNTERS_OFFSET, storage, 0, storage.length);

            long[] counters = new long[RandomCountersSample.COUNTER_COUNT];

            for (int source = 0; source < counters.length; source++) {
                counters[source] = LittleEndianUtils.readUnsigned32(storage, source * Integer.BYTES);
            }

            return new RandomCountersSample(counters);
        };
    }
}
