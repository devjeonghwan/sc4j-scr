package org.fogofwarlabs.screcorder.sample.ai;

import org.fogofwarlabs.screcorder.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

/**
 * Stores one immutable AI region memory image; embedded addresses remain process-specific.
 */
public final class AiRegionState {
    /**
     * Size of one AI region memory image.
     */
    public static final int MEMORY_BYTES = 80;

    private final int identifier;
    private final byte @NotNull [] memory;

    /**
     * Creates a {@link AiRegionState} instance.
     *
     * @param identifier the zero-based region index
     * @param memory     the exact region memory bytes, copied on construction
     * @throws IllegalArgumentException if {@code memory} is not {@link #MEMORY_BYTES} bytes long
     */
    public AiRegionState(int identifier, byte @NotNull [] memory) {
        if (memory.length != MEMORY_BYTES) {
            throw Exceptions.newIllegalArgumentException("Invalid AI region memory size: {0}", memory.length);
        }

        this.identifier = identifier;
        this.memory     = memory.clone();
    }


    /**
     * @return the zero-based region index
     */
    public int getIdentifier() {
        return this.identifier;
    }

    /**
     * @return a detached copy of the region memory
     */
    public byte @NotNull [] getMemory() {
        return this.memory.clone();
    }
}
