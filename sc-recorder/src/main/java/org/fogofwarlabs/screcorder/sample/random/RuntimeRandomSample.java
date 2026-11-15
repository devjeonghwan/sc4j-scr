package org.fogofwarlabs.screcorder.sample.random;

import org.fogofwarlabs.screcorder.sample.Sample;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Contains the immutable C runtime random states of the target threads that hold runtime thread data.
 */
public final class RuntimeRandomSample implements Sample {
    private final @NotNull List<RuntimeRandomState> threads;

    /**
     * Creates a {@link RuntimeRandomSample} instance.
     *
     * @param threads the thread states to copy
     */
    public RuntimeRandomSample(@NotNull List<RuntimeRandomState> threads) {
        this.threads = List.copyOf(threads);
    }


    /**
     * @return the immutable thread states
     */
    public @NotNull List<RuntimeRandomState> getThreads() {
        return this.threads;
    }
}
