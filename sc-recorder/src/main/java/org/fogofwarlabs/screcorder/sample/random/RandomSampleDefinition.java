package org.fogofwarlabs.screcorder.sample.random;

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
 * Defines unsigned random generator values and bulk memory sampling.
 */
public final class RandomSampleDefinition extends SampleDefinition<RandomSample> {
    private static final int SAMPLE_IDENTIFIER = 3;
    private static final int PAYLOAD_BYTES = 3 * Integer.BYTES;

    private static final long MAXIMUM_UNSIGNED_INTEGER = 0xFFFFFFFFL;

    private static final int CAPTURE_BYTES = 16;

    private static final @NotNull SampleReader<RandomSample> READER = RandomSampleDefinition::read;
    private static final @NotNull SampleWriter<RandomSample> WRITER = RandomSampleDefinition::write;

    private static @NotNull RandomSample read(@NotNull ByteBuffer input) throws IOException {
        if (input.remaining() != PAYLOAD_BYTES) {
            throw Exceptions.newIOException("Invalid random payload size: {0}", input.remaining());
        }

        input.order(ByteOrder.LITTLE_ENDIAN);

        return new RandomSample(Integer.toUnsignedLong(input.getInt()),
                                Integer.toUnsignedLong(input.getInt()),
                                Integer.toUnsignedLong(input.getInt()));
    }

    private static void write(@NotNull ByteBuffer output, @NotNull RandomSample sample) throws IOException {
        validate(sample);

        if (output.remaining() < PAYLOAD_BYTES || output.isReadOnly()) {
            throw Exceptions.newIOException("Insufficient writable random buffer: {0}", PAYLOAD_BYTES);
        }

        output.order(ByteOrder.LITTLE_ENDIAN).putInt((int) sample.getState()).putInt((int) sample.getTotal()).putInt((int) sample.getGate());
    }

    private static void validate(@NotNull RandomSample sample) throws IOException {
        if (sample.getState() < 0 || sample.getState() > MAXIMUM_UNSIGNED_INTEGER ||
            sample.getTotal() < 0 || sample.getTotal() > MAXIMUM_UNSIGNED_INTEGER ||
            sample.getGate() < 0 || sample.getGate() > MAXIMUM_UNSIGNED_INTEGER) {
            throw Exceptions.newIOException("Random values must fit an unsigned integer");
        }
    }


    /**
     * Creates a {@link RandomSampleDefinition} instance.
     */
    public RandomSampleDefinition() {
        super(SAMPLE_IDENTIFIER, "random", RandomSample.class, PAYLOAD_BYTES);
    }


    /**
     * @return the bounded payload reader
     */
    @Override
    public @NotNull SampleReader<RandomSample> getReader() {
        return READER;
    }

    /**
     * @return the canonical payload writer
     */
    @Override
    public @NotNull SampleWriter<RandomSample> getWriter() {
        return WRITER;
    }


    /**
     * Calculates the exact payload size before reserving reusable output storage.
     *
     * @param sample the sample to encode
     * @return the exact encoded byte count
     * @throws IOException if the sample is invalid
     */
    @Override
    public int getEncodedSize(@NotNull RandomSample sample) throws IOException {
        validate(sample);

        return PAYLOAD_BYTES;
    }

    /**
     * Creates a {@link Sampler} instance with session-owned capture buffers.
     *
     * @param context the capture session
     * @return the created {@link Sampler} instance
     */
    @Override
    public @NotNull Sampler<RandomSample> createSampler(@NotNull CaptureContext context) {
        byte[] storage = new byte[CAPTURE_BYTES];
        ByteBuffer memory = ByteBuffer.wrap(storage).order(ByteOrder.LITTLE_ENDIAN);

        return () -> {
            context.getMemory().read(context.getModuleBase() + StarCraftLayout.RANDOM_BLOCK, storage, 0, storage.length);

            return new RandomSample(Integer.toUnsignedLong(memory.getInt(0)),
                                    Integer.toUnsignedLong(memory.getInt(StarCraftLayout.RANDOM_TOTAL_OFFSET)),
                                    Integer.toUnsignedLong(memory.getInt(StarCraftLayout.RANDOM_GATE_OFFSET)));
        };
    }
}
