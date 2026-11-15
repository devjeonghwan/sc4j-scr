package org.fogofwarlabs.screcorder.sample.random;

/**
 * Holds the C runtime random state of one target thread.
 */
public final class RuntimeRandomState {
    private final int threadOrder;
    private final long state;

    /**
     * Creates a {@link RuntimeRandomState} instance.
     *
     * @param threadOrder the zero-based rank of the thread by creation time, the initial thread first
     * @param state       the unsigned random state of the thread
     */
    public RuntimeRandomState(int threadOrder, long state) {
        this.threadOrder = threadOrder;
        this.state       = state;
    }


    /**
     * @return the zero-based rank of the thread by creation time, the initial thread first
     */
    public int getThreadOrder() {
        return this.threadOrder;
    }

    /**
     * @return the unsigned random state of the thread
     */
    public long getState() {
        return this.state;
    }
}
