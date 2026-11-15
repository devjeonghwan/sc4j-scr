package org.fogofwarlabs.screcorder.sample;

import org.fogofwarlabs.screcorder.capture.sampling.CaptureContext;
import org.fogofwarlabs.screcorder.capture.sampling.Sampler;
import org.fogofwarlabs.screcorder.exception.Exceptions;
import org.fogofwarlabs.screcorder.sample.io.SampleReader;
import org.fogofwarlabs.screcorder.sample.io.SampleWriter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Binds a stable sample identifier and model to its reader, writer and session sampler.
 *
 * @param <SampleValue> the sample model type
 */
public abstract class SampleDefinition<SampleValue extends Sample> {
    private static final int MAXIMUM_PAYLOAD_BYTES = 64 * 1024 * 1024;

    private final int identifier;
    private final @NotNull String name;
    private final @NotNull Class<SampleValue> sampleClass;
    private final int maximumPayloadBytes;

    /**
     * Creates a {@link SampleDefinition} instance with immutable identity and payload bounds.
     *
     * @param identifier          the positive wire identifier
     * @param name                the command-line sample name
     * @param sampleClass         the concrete sample model class
     * @param maximumPayloadBytes the maximum encoded payload size
     * @throws IllegalArgumentException if {@code identifier} is not positive, {@code name} is blank or {@code maximumPayloadBytes} is out of range
     */
    protected SampleDefinition(int identifier, @NotNull String name, @NotNull Class<SampleValue> sampleClass, int maximumPayloadBytes) {
        if (identifier <= 0 || name.isBlank() || maximumPayloadBytes <= 0 || maximumPayloadBytes > MAXIMUM_PAYLOAD_BYTES) {
            throw Exceptions.newIllegalArgumentException("Invalid sample definition: {0}", name);
        }

        this.identifier          = identifier;
        this.name                = name;
        this.sampleClass         = sampleClass;
        this.maximumPayloadBytes = maximumPayloadBytes;
    }


    /**
     * @return the stable wire identifier
     */
    public final int getIdentifier() {
        return this.identifier;
    }

    /**
     * @return the command-line sample name
     */
    public final @NotNull String getName() {
        return this.name;
    }

    /**
     * @return the concrete sample model class
     */
    public final @NotNull Class<SampleValue> getSampleClass() {
        return this.sampleClass;
    }

    /**
     * @return the maximum encoded payload size
     */
    public final int getMaximumPayloadBytes() {
        return this.maximumPayloadBytes;
    }

    /**
     * @return the bounded payload reader
     */
    public abstract @NotNull SampleReader<SampleValue> getReader();

    /**
     * @return the canonical payload writer
     */
    public abstract @NotNull SampleWriter<SampleValue> getWriter();


    /**
     * Calculates the exact payload size before reserving reusable output storage.
     *
     * @param sample the sample to encode
     * @return the exact encoded byte count
     * @throws IOException if the sample is invalid
     */
    public abstract int getEncodedSize(@NotNull SampleValue sample) throws IOException;

    /**
     * Creates a {@link Sampler} instance with session-owned capture buffers.
     *
     * @param context the capture session
     * @return the created {@link Sampler} instance
     */
    public abstract @NotNull Sampler<SampleValue> createSampler(@NotNull CaptureContext context);
}
