package org.fogofwarlabs.screcorder.sample.random;

import org.fogofwarlabs.screcorder.sample.Sample;

/**
 * Contains the unsigned random state, invocation counter and generator gate.
 */
public final class RandomSample implements Sample {
    private final long state;
    private final long total;
    private final long gate;

    /**
     * Creates a {@link RandomSample} instance.
     *
     * @param state the unsigned random generator state
     * @param total the unsigned random invocation counter
     * @param gate  the unsigned random generator gate
     */
    public RandomSample(long state, long total, long gate) {
        this.state = state;
        this.total = total;
        this.gate  = gate;
    }


    /**
     * @return the unsigned random generator state
     */
    public long getState() {
        return this.state;
    }

    /**
     * @return the unsigned random invocation counter
     */
    public long getTotal() {
        return this.total;
    }

    /**
     * @return the unsigned random generator gate
     */
    public long getGate() {
        return this.gate;
    }
}
