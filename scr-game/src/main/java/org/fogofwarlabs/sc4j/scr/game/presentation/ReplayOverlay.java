package org.fogofwarlabs.sc4j.scr.game.presentation;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;

/**
 * Retains the three replay overlay visibility controls cleared at startup.
 */
public final class ReplayOverlay {
    /**
     * The mask of a visibility byte.
     */
    private static final int BYTE_MASK = 0xFF;
    /**
     * The multiplier that combines each retained value into the state hash.
     */
    private static final int HASH_MULTIPLIER = 31;

    private final int firstPanelVisible;
    private final int secondPanelVisible;
    private final int thirdPanelVisible;

    /**
     * Creates a {@link ReplayOverlay} instance with established visibility controls.
     *
     * @param firstPanelVisible  the first panel visibility byte
     * @param secondPanelVisible the second panel visibility byte
     * @param thirdPanelVisible  the third panel visibility byte
     */
    public ReplayOverlay(int firstPanelVisible, int secondPanelVisible, int thirdPanelVisible) {
        this.firstPanelVisible  = firstPanelVisible & BYTE_MASK;
        this.secondPanelVisible = secondPanelVisible & BYTE_MASK;
        this.thirdPanelVisible  = thirdPanelVisible & BYTE_MASK;
    }

    /**
     * Checks that all three panels are hidden, so the represented overlay draws nothing.
     *
     * @throws UnsupportedOperationException if a panel is visible
     */
    public void update() {
        if (this.firstPanelVisible != 0 || this.secondPanelVisible != 0 || this.thirdPanelVisible != 0) {
            throw Exceptions.newUnsupportedOperationException("An active replay overlay requires its asset and draw context.");
        }
    }

    /**
     * Computes a hash of the retained visibility controls.
     *
     * @param hash the preceding deterministic state hash
     * @return the hash including the established visibility bytes
     */
    public int computeStateHash(int hash) {
        hash = HASH_MULTIPLIER * hash + this.firstPanelVisible;
        hash = HASH_MULTIPLIER * hash + this.secondPanelVisible;

        return HASH_MULTIPLIER * hash + this.thirdPanelVisible;
    }
}
