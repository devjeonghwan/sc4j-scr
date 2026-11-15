package org.fogofwarlabs.screcorder.sample.frame;

import org.fogofwarlabs.screcorder.sample.Sample;

/**
 * Holds observed simulation clock, random and unit counters.
 */
public final class FrameStateSample implements Sample {
    private final long randomState;
    private final long randomTotal;

    private final long seconds;
    private final long unitCount;

    private final long orderTimerCounter;
    private final long secondaryTimerCounter;

    private final long unitCapacity;

    /**
     * Creates a {@link FrameStateSample} instance.
     *
     * @param randomState           the unsigned random generator state
     * @param randomTotal           the unsigned random call count
     * @param seconds               the unsigned game clock in seconds
     * @param unitCount             the unsigned live unit count
     * @param orderTimerCounter     the unsigned primary order timer counter
     * @param secondaryTimerCounter the unsigned secondary order timer counter
     * @param unitCapacity          the unsigned allocated unit capacity
     */
    public FrameStateSample(long randomState, long randomTotal,
                            long seconds, long unitCount,
                            long orderTimerCounter, long secondaryTimerCounter,
                            long unitCapacity) {
        this.randomState = randomState;
        this.randomTotal = randomTotal;

        this.seconds   = seconds;
        this.unitCount = unitCount;

        this.orderTimerCounter     = orderTimerCounter;
        this.secondaryTimerCounter = secondaryTimerCounter;

        this.unitCapacity = unitCapacity;
    }


    /**
     * @return the unsigned random generator state
     */
    public long getRandomState() {
        return this.randomState;
    }

    /**
     * @return the unsigned random call count
     */
    public long getRandomTotal() {
        return this.randomTotal;
    }

    /**
     * @return the unsigned game clock in seconds
     */
    public long getSeconds() {
        return this.seconds;
    }

    /**
     * @return the unsigned live unit count
     */
    public long getUnitCount() {
        return this.unitCount;
    }

    /**
     * @return the unsigned primary order timer counter
     */
    public long getOrderTimerCounter() {
        return this.orderTimerCounter;
    }

    /**
     * @return the unsigned secondary order timer counter
     */
    public long getSecondaryTimerCounter() {
        return this.secondaryTimerCounter;
    }

    /**
     * @return the unsigned allocated unit capacity
     */
    public long getUnitCapacity() {
        return this.unitCapacity;
    }
}
