package org.fogofwarlabs.screcorder.sample.context;

import org.fogofwarlabs.screcorder.exception.Exceptions;
import org.fogofwarlabs.screcorder.sample.Sample;
import org.jetbrains.annotations.NotNull;

/**
 * Stores an immutable game context memory image; embedded addresses remain process-specific.
 */
public final class ContextSample implements Sample {
    /**
     * Size of the recorded game context memory image.
     */
    public static final int MEMORY_BYTES = 66396;

    private final byte @NotNull [] memory;

    /**
     * Creates a {@link ContextSample} instance.
     *
     * @param memory the exact game context bytes, copied on construction
     * @throws IllegalArgumentException if {@code memory} is not {@link #MEMORY_BYTES} bytes long
     */
    public ContextSample(byte @NotNull [] memory) {
        if (memory.length != MEMORY_BYTES) {
            throw Exceptions.newIllegalArgumentException("Invalid context memory size: {0}", memory.length);
        }

        this.memory = memory.clone();
    }


    /**
     * @return a detached copy of the game context memory
     */
    public byte @NotNull [] getMemory() {
        return this.memory.clone();
    }
}
