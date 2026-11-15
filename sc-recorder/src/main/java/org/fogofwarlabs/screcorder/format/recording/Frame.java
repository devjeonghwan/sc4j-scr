package org.fogofwarlabs.screcorder.format.recording;

import org.fogofwarlabs.screcorder.exception.Exceptions;
import org.fogofwarlabs.screcorder.format.recording.observation.CaptureConsistency;
import org.fogofwarlabs.screcorder.format.recording.observation.SampleObservation;
import org.fogofwarlabs.screcorder.format.recording.observation.SampleStatus;
import org.fogofwarlabs.screcorder.format.recording.observation.StorageMode;
import org.fogofwarlabs.screcorder.sample.Sample;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Stores immutable typed samples and observation metadata for one observed frame.
 */
public final class Frame {
    private final long frameNumber;
    private final long elapsedNanoseconds;
    private final @NotNull CaptureConsistency consistency;
    private final @NotNull Map<Class<? extends Sample>, Sample> samples;
    private final @NotNull Map<Class<? extends Sample>, SampleObservation> observations;

    Frame(long frameNumber, long elapsedNanoseconds, @NotNull CaptureConsistency consistency,
          @NotNull Map<Class<? extends Sample>, Sample> samples,
          @NotNull Map<Class<? extends Sample>, SampleObservation> observations) {
        this.frameNumber        = frameNumber;
        this.elapsedNanoseconds = elapsedNanoseconds;
        this.consistency        = consistency;
        this.samples            = Map.copyOf(samples);
        this.observations       = Map.copyOf(observations);
    }

    /**
     * @return the observed frame number
     */
    public long getFrameNumber() {
        return this.frameNumber;
    }

    /**
     * @return the elapsed monotonic time at frame staging start
     */
    public long getElapsedNanoseconds() {
        return this.elapsedNanoseconds;
    }

    /**
     * @return the observation consistency evidence
     */
    public @NotNull CaptureConsistency getConsistency() {
        return this.consistency;
    }

    /**
     * Gets the available decoded sample of the exact requested class.
     *
     * @param sampleClass   the exact sample class
     * @param <SampleValue> the requested sample type
     * @return the immutable decoded sample
     * @throws IllegalStateException if the sample was not selected, unavailable, or stored only as a digest
     */
    public <SampleValue extends Sample> @NotNull SampleValue getSample(@NotNull Class<SampleValue> sampleClass) {
        SampleObservation observation = this.getSampleObservation(sampleClass);

        if (observation.getStatus() != SampleStatus.AVAILABLE) {
            throw Exceptions.newIllegalStateException("Sample {0} is unavailable: {1}", sampleClass.getSimpleName(), observation.getStatus());
        }

        if (observation.getStorageMode() != StorageMode.VALUES) {
            throw Exceptions.newIllegalStateException("Sample {0} contains a digest instead of values", sampleClass.getSimpleName());
        }

        return sampleClass.cast(this.samples.get(sampleClass));
    }

    /**
     * Gets representation and collection metadata for the requested class.
     *
     * @param sampleClass the exact selected sample class
     * @return the immutable observation metadata
     * @throws IllegalStateException if the sample was not selected
     */
    public @NotNull SampleObservation getSampleObservation(@NotNull Class<? extends Sample> sampleClass) {
        SampleObservation observation = this.observations.get(sampleClass);

        if (observation == null) {
            throw Exceptions.newIllegalStateException("Sample {0} was not selected", sampleClass.getSimpleName());
        }

        return observation;
    }
}
