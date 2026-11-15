package org.fogofwarlabs.screcorder.sample.io;

import org.fogofwarlabs.screcorder.sample.Sample;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Writes one typed sample as a canonical little-endian payload.
 *
 * @param <SampleValue> the sample model type
 */
@FunctionalInterface
public interface SampleWriter<SampleValue extends Sample> {
    /**
     * Writes the complete payload at the current buffer position.
     *
     * @param output the bounded destination buffer
     * @param sample the immutable sample value
     * @throws IOException if the value or destination is invalid
     */
    void write(@NotNull ByteBuffer output, @NotNull SampleValue sample) throws IOException;
}
