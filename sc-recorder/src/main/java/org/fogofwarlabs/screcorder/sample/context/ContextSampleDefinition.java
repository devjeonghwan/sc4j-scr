package org.fogofwarlabs.screcorder.sample.context;

import org.fogofwarlabs.screcorder.capture.sampling.CaptureContext;
import org.fogofwarlabs.screcorder.capture.sampling.Sampler;
import org.fogofwarlabs.screcorder.capture.starcraft.StarCraftLayout;
import org.fogofwarlabs.screcorder.exception.Exceptions;
import org.fogofwarlabs.screcorder.sample.SampleDefinition;
import org.fogofwarlabs.screcorder.sample.io.SampleReader;
import org.fogofwarlabs.screcorder.sample.io.SampleWriter;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Defines the fixed context memory image payload and bulk sampling.
 */
public final class ContextSampleDefinition extends SampleDefinition<ContextSample> {
    private static final int SAMPLE_IDENTIFIER = 8;

    private static final int MAP_PATH_OFFSET = 0xC4C;
    private static final int MAP_PATH_BYTES = 260;
    private static final int SCREEN_TILE_BYTES = 2 * Short.BYTES;

    private static final @NotNull SampleReader<ContextSample> READER = input -> {
        if (input.remaining() != ContextSample.MEMORY_BYTES) {
            throw Exceptions.newIOException("Invalid context payload size: {0}", input.remaining());
        }

        byte[] memory = new byte[ContextSample.MEMORY_BYTES];

        input.get(memory);

        return new ContextSample(memory);
    };

    private static final @NotNull SampleWriter<ContextSample> WRITER = (output, sample) -> {
        if (output.remaining() < ContextSample.MEMORY_BYTES || output.isReadOnly()) {
            throw Exceptions.newIOException("Insufficient writable context buffer");
        }

        output.put(sample.getMemory());
    };

    /**
     * Clears the context bytes that are not simulation state: the loaded map or replay path (it names the recorder's per-session directory) and the
     * screen tile position of the local view.
     *
     * @param memory the raw context image
     */
    public static void mask(byte @NotNull [] memory) {
        Arrays.fill(memory, MAP_PATH_OFFSET, MAP_PATH_OFFSET + MAP_PATH_BYTES, (byte) 0);
        Arrays.fill(memory, StarCraftLayout.SCREEN_TILE_X_OFFSET, StarCraftLayout.SCREEN_TILE_X_OFFSET + SCREEN_TILE_BYTES, (byte) 0);
    }


    /**
     * Creates a {@link ContextSampleDefinition} instance.
     */
    public ContextSampleDefinition() {
        super(SAMPLE_IDENTIFIER, "context", ContextSample.class, ContextSample.MEMORY_BYTES);
    }


    /**
     * @return the bounded payload reader
     */
    @Override
    public @NotNull SampleReader<ContextSample> getReader() {
        return READER;
    }

    /**
     * @return the canonical payload writer
     */
    @Override
    public @NotNull SampleWriter<ContextSample> getWriter() {
        return WRITER;
    }


    /**
     * Calculates the exact payload size before reserving reusable output storage.
     *
     * @param sample the sample to encode
     * @return the exact encoded byte count
     */
    @Override
    public int getEncodedSize(@NotNull ContextSample sample) {
        return ContextSample.MEMORY_BYTES;
    }

    /**
     * Creates a {@link Sampler} instance with session-owned capture buffers.
     *
     * @param context the capture session
     * @return the created {@link Sampler} instance
     */
    @Override
    public @NotNull Sampler<ContextSample> createSampler(@NotNull CaptureContext context) {
        byte[] memory = new byte[ContextSample.MEMORY_BYTES];

        return () -> {
            context.getMemory().read(context.getContextAddress(), memory, 0, memory.length);
            mask(memory);

            return new ContextSample(memory);
        };
    }
}
