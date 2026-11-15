package org.fogofwarlabs.screcorder.sample.io;

import org.fogofwarlabs.screcorder.sample.Sample;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Reads one typed sample from its bounded little-endian payload.
 *
 * @param <SampleValue> the sample model type
 */
@FunctionalInterface
public interface SampleReader<SampleValue extends Sample> {
    /**
     * Reads and consumes the complete payload.
     *
     * @param input the bounded payload buffer
     * @return the decoded immutable sample
     * @throws IOException if the payload is invalid
     */
    @NotNull SampleValue read(@NotNull ByteBuffer input) throws IOException;
}
