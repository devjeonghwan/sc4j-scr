package org.fogofwarlabs.screcorder.sample.unitlist;

import org.fogofwarlabs.screcorder.sample.Sample;
import org.jetbrains.annotations.NotNull;

/**
 * Contains unit pool identifiers in their visible and hidden traversal order.
 */
public final class UnitListsSample implements Sample {
    private final int @NotNull [] visibleUnitIdentifiers;
    private final int @NotNull [] hiddenUnitIdentifiers;

    /**
     * Creates a {@link UnitListsSample} instance.
     *
     * @param visibleUnitIdentifiers the visible traversal to copy
     * @param hiddenUnitIdentifiers  the hidden traversal to copy
     */
    public UnitListsSample(int @NotNull [] visibleUnitIdentifiers, int @NotNull [] hiddenUnitIdentifiers) {
        this.visibleUnitIdentifiers = visibleUnitIdentifiers.clone();
        this.hiddenUnitIdentifiers  = hiddenUnitIdentifiers.clone();
    }


    /**
     * @return a copy of the visible traversal identifiers
     */
    public int @NotNull [] getVisibleUnitIdentifiers() {
        return this.visibleUnitIdentifiers.clone();
    }

    /**
     * @return a copy of the hidden traversal identifiers
     */
    public int @NotNull [] getHiddenUnitIdentifiers() {
        return this.hiddenUnitIdentifiers.clone();
    }

    int @NotNull [] getVisibleStorage() {
        return this.visibleUnitIdentifiers;
    }

    int @NotNull [] getHiddenStorage() {
        return this.hiddenUnitIdentifiers;
    }
}
