package org.fogofwarlabs.screcorder.sample.random;

import org.fogofwarlabs.screcorder.exception.Exceptions;
import org.fogofwarlabs.screcorder.sample.Sample;
import org.jetbrains.annotations.NotNull;

/**
 * Contains the {@code 256} unsigned per-source random invocation counters.
 */
public final class RandomCountersSample implements Sample {
    /**
     * The number of random sources with an invocation counter.
     */
    public static final int COUNTER_COUNT = 256;

    private static final long MAXIMUM_UNSIGNED_INTEGER = 0xFFFFFFFFL;

    private final long @NotNull [] counters;

    /**
     * Creates a {@link RandomCountersSample} instance.
     *
     * @param counters the unsigned DWORD counters indexed by random source
     * @throws IllegalArgumentException if {@code counters} does not hold {@link #COUNTER_COUNT} values or a counter exceeds the unsigned DWORD range
     */
    public RandomCountersSample(long @NotNull [] counters) {
        if (counters.length != COUNTER_COUNT) {
            throw Exceptions.newIllegalArgumentException("Expected {0} random counters", COUNTER_COUNT);
        }

        for (long counter : counters) {
            if (counter < 0 || counter > MAXIMUM_UNSIGNED_INTEGER) {
                throw Exceptions.newIllegalArgumentException("Random counter exceeds unsigned DWORD range");
            }
        }

        this.counters = counters.clone();
    }


    /**
     * @return a copy of the counters indexed by random source
     */
    public long @NotNull [] getCounters() {
        return this.counters.clone();
    }

    /**
     * @param source the random source index
     * @return the unsigned invocation counter
     */
    public long getCounter(int source) {
        return this.counters[source];
    }
}
