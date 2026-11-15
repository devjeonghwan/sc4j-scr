package org.fogofwarlabs.screcorder.format.recording.observation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Stores collection status and representation metadata independently of sample values.
 */
public final class SampleObservation {
    private final @NotNull SampleStatus status;
    private final @NotNull StorageMode storageMode;
    private final long sampledFrame;
    private final byte @Nullable [] digest;

    /**
     * Creates a {@link SampleObservation} instance from decoded observation metadata.
     *
     * @param status       the collection status
     * @param storageMode  the stored representation
     * @param sampledFrame the sampled frame, or {@code -1} when unavailable
     * @param digest       the digest bytes, or {@code null} when no digest is stored
     */
    public SampleObservation(@NotNull SampleStatus status, @NotNull StorageMode storageMode, long sampledFrame, byte @Nullable [] digest) {
        this.status       = status;
        this.storageMode  = storageMode;
        this.sampledFrame = sampledFrame;
        this.digest       = digest == null ? null : digest.clone();
    }

    /**
     * @return the collection status
     */
    public @NotNull SampleStatus getStatus() {
        return this.status;
    }

    /**
     * @return the stored representation
     */
    public @NotNull StorageMode getStorageMode() {
        return this.storageMode;
    }

    /**
     * @return the actual sampled frame, or {@code -1} when unavailable
     */
    public long getSampledFrame() {
        return this.sampledFrame;
    }

    /**
     * @return a digest copy, or {@code null} when no digest was stored
     */
    public byte @Nullable [] getDigest() {
        return this.digest == null ? null : this.digest.clone();
    }
}
