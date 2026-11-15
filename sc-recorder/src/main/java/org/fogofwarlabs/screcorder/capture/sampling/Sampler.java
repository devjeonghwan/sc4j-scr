package org.fogofwarlabs.screcorder.capture.sampling;

import org.fogofwarlabs.screcorder.sample.Sample;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Collects a typed immutable observation from a capture session.
 *
 * @param <SampleValue> the collected sample type
 */
@FunctionalInterface
public interface Sampler<SampleValue extends Sample> {
    /**
     * Collects the current sample.
     *
     * @return the immutable observation
     * @throws IOException if required memory cannot be collected
     */
    @NotNull SampleValue sample() throws IOException;
}
