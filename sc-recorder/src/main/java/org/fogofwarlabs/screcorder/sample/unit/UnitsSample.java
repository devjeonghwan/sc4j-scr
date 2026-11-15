package org.fogofwarlabs.screcorder.sample.unit;

import org.fogofwarlabs.screcorder.sample.Sample;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Contains an immutable collection of units observed in one frame.
 */
public final class UnitsSample implements Sample {
    private final @NotNull List<UnitState> units;

    /**
     * Creates a {@link UnitsSample} instance.
     *
     * @param units the observed units
     */
    public UnitsSample(@NotNull List<UnitState> units) {
        this.units = List.copyOf(units);
    }


    /**
     * @return the immutable observed units
     */
    public @NotNull List<UnitState> getUnits() {
        return this.units;
    }
}
