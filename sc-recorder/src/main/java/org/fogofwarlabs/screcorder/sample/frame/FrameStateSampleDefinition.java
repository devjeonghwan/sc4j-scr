package org.fogofwarlabs.screcorder.sample.frame;

import org.fogofwarlabs.screcorder.capture.MemoryReader;
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
 * Defines explicit simulation counter payloads and native capture.
 */
public final class FrameStateSampleDefinition extends SampleDefinition<FrameStateSample> {
    private static final int SAMPLE_IDENTIFIER = 4;
    private static final int COUNTER_FIELD_COUNT = 7;
    private static final int PAYLOAD_BYTES = COUNTER_FIELD_COUNT * Integer.BYTES;

    private static final long DWORD_MASK = 0xFFFFFFFFL;

    private static final int RANDOM_BYTES = 16;
    private static final int COUNTER_BYTES = 0x3C;
    private static final int SECONDARY_TIMER_COUNTER_OFFSET = Integer.BYTES;
    private static final int UNIT_COUNT_OFFSET = 0x38;
    private static final long COUNTERS_OFFSET = 0xE77FC0;
    private static final long CAPACITY_OFFSET = 0xE7E024;

    private static final @NotNull SampleReader<FrameStateSample> READER = FrameStateSampleDefinition::read;
    private static final @NotNull SampleWriter<FrameStateSample> WRITER = FrameStateSampleDefinition::write;

    private static @NotNull FrameStateSample read(@NotNull ByteBuffer input) throws IOException {
        if (input.remaining() != PAYLOAD_BYTES) {
            throw Exceptions.newIOException("Invalid frame counter payload length");
        }

        input.order(ByteOrder.LITTLE_ENDIAN);

        return new FrameStateSample(Integer.toUnsignedLong(input.getInt()), Integer.toUnsignedLong(input.getInt()),
                                    Integer.toUnsignedLong(input.getInt()), Integer.toUnsignedLong(input.getInt()),
                                    Integer.toUnsignedLong(input.getInt()), Integer.toUnsignedLong(input.getInt()),
                                    Integer.toUnsignedLong(input.getInt()));
    }

    private static void write(@NotNull ByteBuffer output, @NotNull FrameStateSample sample) throws IOException {
        validate(sample);

        if (output.remaining() < PAYLOAD_BYTES || output.isReadOnly()) {
            throw Exceptions.newIOException("Insufficient writable frame counter buffer");
        }

        output.order(ByteOrder.LITTLE_ENDIAN);

        output.putInt((int) sample.getRandomState());
        output.putInt((int) sample.getRandomTotal());
        output.putInt((int) sample.getSeconds());
        output.putInt((int) sample.getUnitCount());
        output.putInt((int) sample.getOrderTimerCounter());
        output.putInt((int) sample.getSecondaryTimerCounter());
        output.putInt((int) sample.getUnitCapacity());
    }

    private static void validate(@NotNull FrameStateSample sample) throws IOException {
        if ((sample.getRandomState() & ~DWORD_MASK) != 0 ||
            (sample.getRandomTotal() & ~DWORD_MASK) != 0 ||
            (sample.getSeconds() & ~DWORD_MASK) != 0 ||
            (sample.getUnitCount() & ~DWORD_MASK) != 0 ||
            (sample.getOrderTimerCounter() & ~DWORD_MASK) != 0 ||
            (sample.getSecondaryTimerCounter() & ~DWORD_MASK) != 0 ||
            (sample.getUnitCapacity() & ~DWORD_MASK) != 0) {
            throw Exceptions.newIOException("Frame counter exceeds its unsigned integer representation");
        }
    }


    /**
     * Creates a {@link FrameStateSampleDefinition} instance.
     */
    public FrameStateSampleDefinition() {
        super(SAMPLE_IDENTIFIER, "frame", FrameStateSample.class, PAYLOAD_BYTES);
    }


    /**
     * @return the bounded payload reader
     */
    @Override
    public @NotNull SampleReader<FrameStateSample> getReader() {
        return READER;
    }

    /**
     * @return the canonical payload writer
     */
    @Override
    public @NotNull SampleWriter<FrameStateSample> getWriter() {
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
    public int getEncodedSize(@NotNull FrameStateSample sample) throws IOException {
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
    public @NotNull Sampler<FrameStateSample> createSampler(@NotNull CaptureContext context) {
        byte[] randomStorage = new byte[RANDOM_BYTES];
        byte[] counterStorage = new byte[COUNTER_BYTES];
        byte[] clockStorage = new byte[Integer.BYTES];
        byte[] capacityStorage = new byte[Integer.BYTES];
        ByteBuffer random = ByteBuffer.wrap(randomStorage).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer counters = ByteBuffer.wrap(counterStorage).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer clock = ByteBuffer.wrap(clockStorage).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer capacity = ByteBuffer.wrap(capacityStorage).order(ByteOrder.LITTLE_ENDIAN);

        return () -> {
            MemoryReader memory = context.getMemory();

            memory.read(context.getModuleBase() + StarCraftLayout.RANDOM_BLOCK, randomStorage, 0, randomStorage.length);
            memory.read(context.getModuleBase() + COUNTERS_OFFSET, counterStorage, 0, counterStorage.length);
            memory.read(context.getContextAddress() + StarCraftLayout.ELAPSED_SECONDS_OFFSET, clockStorage, 0, clockStorage.length);
            memory.read(context.getModuleBase() + CAPACITY_OFFSET, capacityStorage, 0, capacityStorage.length);

            return new FrameStateSample(Integer.toUnsignedLong(random.getInt(0)),
                                        Integer.toUnsignedLong(random.getInt(StarCraftLayout.RANDOM_TOTAL_OFFSET)),
                                        Integer.toUnsignedLong(clock.getInt(0)),
                                        Integer.toUnsignedLong(counters.getInt(UNIT_COUNT_OFFSET)),
                                        Integer.toUnsignedLong(counters.getInt(0)),
                                        Integer.toUnsignedLong(counters.getInt(SECONDARY_TIMER_COUNTER_OFFSET)),
                                        Integer.toUnsignedLong(capacity.getInt(0)));
        };
    }
}
