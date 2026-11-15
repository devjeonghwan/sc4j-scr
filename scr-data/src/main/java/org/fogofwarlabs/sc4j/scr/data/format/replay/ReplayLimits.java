package org.fogofwarlabs.sc4j.scr.data.format.replay;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

/**
 * Preserves requested and effective seven-DWORD replay resource limits. Missing LMTS values use the lower preset; any invalid signed
 * value selects the entire upper preset.
 */
public final class ReplayLimits {
    /**
     * The bullet capacity of the classic replay resource preset.
     */
    public static final int CLASSIC_BULLET_CAPACITY = 100;

    /**
     * The number of limit values: seven DWORDs in resource field order.
     */
    static final int VALUE_COUNT = 7;

    /**
     * The lower preset: the classic limits, and the values of a replay without an LMTS section.
     */
    static final int @NotNull [] MINIMUM_VALUES = {5000, 2500, 500, 1700, CLASSIC_BULLET_CAPACITY, 2000, 500};
    /**
     * The upper preset: the current limits, and the values of a replay with an invalid LMTS value.
     */
    static final int @NotNull [] MAXIMUM_VALUES = {10000, 5000, 1000, 3400, 400, 4000, 1000};

    private static final int THINGY_CAPACITY_VALUE = 2;
    private static final int UNIT_CAPACITY_VALUE = 3;

    private final boolean explicitSection;
    private final boolean fallback;

    private final int @NotNull [] requestedValues;
    private final int @NotNull [] effectiveValues;

    /**
     * Creates a {@link ReplayLimits} instance with the replay load defaults without an LMTS section.
     */
    ReplayLimits() {
        this.explicitSection = false;
        this.fallback        = false;

        this.requestedValues = MINIMUM_VALUES.clone();
        this.effectiveValues = MINIMUM_VALUES.clone();
    }

    /**
     * Creates a {@link ReplayLimits} instance from a fully decoded LMTS tuple.
     *
     * @param requestedValues the seven signed DWORD values
     * @throws IllegalArgumentException if the tuple does not hold seven values
     */
    ReplayLimits(int @NotNull [] requestedValues) {
        if (requestedValues.length != MINIMUM_VALUES.length) {
            throw Exceptions.newIllegalArgumentException("The replay limit tuple must contain seven values.");
        }

        boolean fallback = false;

        for (int valueIndex = 0; valueIndex < requestedValues.length; valueIndex++) {
            if (requestedValues[valueIndex] < MINIMUM_VALUES[valueIndex] || requestedValues[valueIndex] > MAXIMUM_VALUES[valueIndex]) {
                fallback = true;

                break;
            }
        }

        this.explicitSection = true;
        this.fallback        = fallback;

        this.requestedValues = requestedValues.clone();
        this.effectiveValues = fallback ? MAXIMUM_VALUES.clone() : requestedValues.clone();
    }

    /**
     * @return {@code true} if a decoded LMTS section supplied the requested tuple, {@code false} otherwise
     */
    public boolean hasExplicitSection() {
        return this.explicitSection;
    }

    /**
     * @return {@code true} if an invalid requested value selected the entire upper preset, {@code false} otherwise
     */
    public boolean isFallback() {
        return this.fallback;
    }


    /**
     * @return the effective original unit-pool capacity, without allocating unit identities
     */
    public int getUnitCapacity() {
        return this.effectiveValues[UNIT_CAPACITY_VALUE];
    }

    /**
     * @return the effective standalone thingy capacity
     */
    public int getThingyCapacity() {
        return this.effectiveValues[THINGY_CAPACITY_VALUE];
    }

    /**
     * @return a copy of the requested seven values in resource field order
     */
    public int @NotNull [] getRequestedValues() {
        return this.requestedValues.clone();
    }

    /**
     * @return a copy of the effective seven values in resource field order
     */
    public int @NotNull [] getEffectiveValues() {
        return this.effectiveValues.clone();
    }
}
