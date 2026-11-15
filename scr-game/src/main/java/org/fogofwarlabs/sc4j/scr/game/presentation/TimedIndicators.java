package org.fogofwarlabs.sc4j.scr.game.presentation;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

/**
 * Retains the timed byte indicators and their wrapping tick deadlines.
 */
public final class TimedIndicators {
    /**
     * The number of retained host notification values and deadlines.
     */
    public static final int INDICATOR_COUNT = 13;

    /**
     * The mask of an unsigned indicator byte.
     */
    private static final int BYTE_MASK = 0xFF;
    /**
     * The multiplier that combines each retained value into the state hash.
     */
    private static final int HASH_MULTIPLIER = 31;

    private final byte @NotNull [] values;
    private final int @NotNull [] deadlines;

    /**
     * Creates a {@link TimedIndicators} instance with copied values and deadlines.
     *
     * @param values    the indicator values
     * @param deadlines the indicator deadlines
     * @throws IllegalArgumentException if {@code values} or {@code deadlines} does not hold {@code 13} entries
     */
    public TimedIndicators(byte @NotNull [] values, int @NotNull [] deadlines) {
        if (values.length != INDICATOR_COUNT || deadlines.length != INDICATOR_COUNT) {
            throw Exceptions.newIllegalArgumentException("Native frame indicators require thirteen values and deadlines.");
        }

        this.values    = values.clone();
        this.deadlines = deadlines.clone();
    }

    /**
     * @param index the original indicator index
     * @return the retained unsigned BYTE
     */
    public int getValue(int index) {
        return this.values[index] & BYTE_MASK;
    }


    /**
     * Clears each nonzero indicator whose deadline has passed, testing the sign bit of the wrapping DWORD difference.
     *
     * @param hostTicks the measured host tick counter for this invocation
     */
    public void update(int hostTicks) {
        for (int index = 0; index < this.values.length; index++) {
            if (this.values[index] != 0 && hostTicks - this.deadlines[index] >= 0) {
                this.values[index] = 0;
            }
        }
    }

    /**
     * Computes a hash of the retained indicator values and deadlines.
     *
     * @param hash the preceding deterministic state hash
     * @return the hash including all retained indicator state
     */
    public int computeStateHash(int hash) {
        for (int index = 0; index < this.values.length; index++) {
            hash = HASH_MULTIPLIER * hash + (this.values[index] & BYTE_MASK);
            hash = HASH_MULTIPLIER * hash + this.deadlines[index];
        }

        return hash;
    }
}
